/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.chunkstore;

import org.fejoa.library.crypto.CryptoException;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.util.ArrayList;
import java.util.List;


public class TreeAccessor {
    private boolean modified = false;
    private FlatDirectoryBox root;
    private IRepoChunkAccessors.ITransaction transaction;

    public TreeAccessor(FlatDirectoryBox root, IRepoChunkAccessors.ITransaction transaction)
            throws IOException {
        this.transaction = transaction;

        this.root = root;
    }

    public boolean isModified() {
        return modified;
    }

    public void setTransaction(IRepoChunkAccessors.ITransaction transaction) {
        this.transaction = transaction;
    }

    private String checkPath(String path) {
        while (path.startsWith("/"))
            path = path.substring(1);
        return path;
    }

    private ChunkContainerRef write(FileBox fileBox) throws IOException, CryptoException {
        fileBox.flush();
        return fileBox.getDataContainer().getRef();
    }

    public FlatDirectoryBox.Entry get(String path) throws IOException, CryptoException {
        path = checkPath(path);
        String[] parts = path.split("/");
        String entryName = parts[parts.length - 1];
        FlatDirectoryBox.Entry currentDir = get(parts, parts.length - 1, false);
        if (currentDir == null)
            return null;
        if (entryName.equals(""))
            return currentDir;
        return ((FlatDirectoryBox)currentDir.getObject()).getEntry(entryName);
    }

    /**
     * @param parts List of directories
     * @param nDirs Number of dirs in parts that should be follow
     * @return null or an entry pointing to the request directory, the object is loaded
     * @throws IOException
     * @throws CryptoException
     */
    public FlatDirectoryBox.Entry get(String[] parts, int nDirs, boolean invalidTouchedDirs)
            throws IOException, CryptoException {
        if (root == null)
            return null;
        FlatDirectoryBox.Entry entry = null;
        FlatDirectoryBox currentDir = root;
        for (int i = 0; i < nDirs; i++) {
            String subDir = parts[i];
            entry = currentDir.getEntry(subDir);
            if (entry == null || entry.isFile())
                return null;

            if (entry.getObject() != null) {
                currentDir = (FlatDirectoryBox)entry.getObject();
            } else {
                IChunkAccessor accessor = transaction.getTreeAccessor(entry.getDataPointer());
                currentDir = FlatDirectoryBox.read(accessor, entry.getDataPointer());
                entry.setObject(currentDir);
            }
            if (invalidTouchedDirs)
                entry.markModified();
        }
        if (currentDir == root) {
            entry = new FlatDirectoryBox.Entry("", null, false);
            entry.setObject(root);
            if (invalidTouchedDirs)
                entry.markModified();
        }
        return entry;
    }

    public boolean hasFile(String path) throws IOException, CryptoException {
        FlatDirectoryBox.Entry fileEntry = get(path);
        if (fileEntry == null)
            return false;
        return fileEntry.isFile();
    }

    public FileBox getFileBox(String path) throws IOException, CryptoException {
        FlatDirectoryBox.Entry fileEntry = get(path);
        if (fileEntry == null)
            throw new NoSuchFileException("Entry not found: " + path);
        assert fileEntry.isFile();

        FileBox fileBox = (FileBox)fileEntry.getObject();
        if (fileBox == null) {
            ChunkContainerRef fileRef = fileEntry.getDataPointer();
            fileBox = FileBox.read(transaction.getFileAccessor(fileRef, path), fileRef);
        }
        return fileBox;
    }

    public void put(String path, FileBox file) throws IOException, CryptoException {
        FlatDirectoryBox.Entry entry = new FlatDirectoryBox.Entry(true);
        entry.setObject(file);
        entry.setDataPointer(file.getRef());
        put(path, entry);
    }

    public FlatDirectoryBox.Entry put(String path, ChunkContainerRef dataPointer, boolean isFile) throws IOException,
            CryptoException {
        FlatDirectoryBox.Entry entry = new FlatDirectoryBox.Entry("", dataPointer, isFile);
        put(path, entry);
        return entry;
    }

    public void put(String path, FlatDirectoryBox.Entry entry) throws IOException, CryptoException {
        path = checkPath(path);
        String[] parts = path.split("/");
        String fileName = parts[parts.length - 1];
        FlatDirectoryBox currentDir = root;
        List<FlatDirectoryBox.Entry> touchedEntries = new ArrayList<>();
        for (int i = 0; i < parts.length - 1; i++) {
            String subDir = parts[i];
            FlatDirectoryBox.Entry currentEntry = currentDir.getEntry(subDir);
            if (currentEntry == null) {
                FlatDirectoryBox subDirBox = FlatDirectoryBox.create();
                currentEntry = currentDir.addDir(subDir, null);
                currentEntry.setObject(subDirBox);
                currentDir = subDirBox;
            } else {
                if (currentEntry.isFile())
                    throw new IOException("Invalid insert path: " + path);
                if (currentEntry.getObject() != null) {
                    currentDir = (FlatDirectoryBox)currentEntry.getObject();
                } else {
                    currentDir = FlatDirectoryBox.read(transaction.getTreeAccessor(currentEntry.getDataPointer()),
                            currentEntry.getDataPointer());
                    currentEntry.setObject(currentDir);
                }
            }
            touchedEntries.add(currentEntry);
        }
        entry.setName(fileName);

        FlatDirectoryBox.Entry existingEntry = currentDir.getEntry(fileName);
        if (existingEntry != null) {
            // check if something has changed
            if (entry.getDataPointer() != null
                    && currentDir.getEntry(fileName).getDataPointer().equals(entry.getDataPointer())) {
                return;
            }
        }

        for (FlatDirectoryBox.Entry touched : touchedEntries) {
            touched.markModified();
        }
        this.modified = true;
        currentDir.put(fileName, entry);
    }

    public FlatDirectoryBox.Entry remove(String path) throws IOException, CryptoException {
        this.modified = true;
        path = checkPath(path);
        String[] parts = path.split("/");
        String entryName = parts[parts.length - 1];
        FlatDirectoryBox.Entry currentDir = get(parts, parts.length - 1, true);
        if (currentDir == null)
            return null;
        // invalidate entry
        currentDir.markModified();
        FlatDirectoryBox directoryBox = (FlatDirectoryBox)currentDir.getObject();
        return directoryBox.remove(entryName);
    }

    public ChunkContainerRef build() throws IOException, CryptoException {
        modified = false;
        return build(root, "");
    }

    private ChunkContainerRef build(FlatDirectoryBox dir, String path) throws IOException, CryptoException {
        for (FlatDirectoryBox.Entry child : dir.getDirs()) {
            if (child.getDataPointer() != null)
                continue;
            assert child.getObject() != null;
            child.setDataPointer(build((FlatDirectoryBox)child.getObject(), path + "/" + child.getName()));
        }
        for (FlatDirectoryBox.Entry child : dir.getFiles()) {
            if (!child.getDataPointer().getDataHash().isZero())
                continue;
            assert child.getObject() != null;
            FileBox fileBox = (FileBox)child.getObject();
            ChunkContainerRef dataPointer = write(fileBox);
            child.setDataPointer(dataPointer);
        }
        return SyncRepository.put(dir, transaction.getTreeAccessor(dir.getRef()), dir.getRef());
    }

    public FlatDirectoryBox getRoot() {
        return root;
    }
}
