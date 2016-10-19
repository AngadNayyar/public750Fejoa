/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.chunkstore;

import org.fejoa.library.support.StreamHelper;
import org.fejoa.library.crypto.CryptoException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


public class TreeAccessor {
    private boolean modified = false;
    private DirectoryBox root;
    private IRepoChunkAccessors.ITransaction transaction;

    public TreeAccessor(DirectoryBox root, IRepoChunkAccessors.ITransaction transaction) throws IOException {
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

    private BoxPointer write(FileBox fileBox) throws IOException, CryptoException {
        fileBox.flush();
        return fileBox.getDataContainer().getBoxPointer();
    }

    public DirectoryBox.Entry get(String path) throws IOException, CryptoException {
        path = checkPath(path);
        String[] parts = path.split("/");
        String entryName = parts[parts.length - 1];
        DirectoryBox.Entry currentDir = get(parts, parts.length - 1, false);
        if (currentDir == null)
            return null;
        if (entryName.equals(""))
            return currentDir;
        return ((DirectoryBox)currentDir.getObject()).getEntry(entryName);
    }

    /**
     * @param parts List of directories
     * @param nDirs Number of dirs in parts that should be follow
     * @return null or an entry pointing to the request directory, the object is loaded
     * @throws IOException
     * @throws CryptoException
     */
    public DirectoryBox.Entry get(String[] parts, int nDirs, boolean invalidTouchedDirs)
            throws IOException, CryptoException {
        if (root == null)
            return null;
        DirectoryBox.Entry entry = null;
        DirectoryBox currentDir = root;
        for (int i = 0; i < nDirs; i++) {
            String subDir = parts[i];
            entry = currentDir.getEntry(subDir);
            if (entry == null || entry.isFile())
                return null;

            if (entry.getObject() != null) {
                currentDir = (DirectoryBox)entry.getObject();
            } else {
                IChunkAccessor accessor = transaction.getTreeAccessor();
                currentDir = DirectoryBox.read(accessor, entry.getDataPointer());
                entry.setObject(currentDir);
            }
            if (invalidTouchedDirs)
                entry.markModified();
        }
        if (currentDir == root) {
            entry = new DirectoryBox.Entry("", null, false);
            entry.setObject(root);
            if (invalidTouchedDirs)
                entry.markModified();
        }
        return entry;
    }

    public boolean hasFile(String path) throws IOException, CryptoException {
        DirectoryBox.Entry fileEntry = get(path);
        if (fileEntry == null)
            return false;
        return fileEntry.isFile();
    }

    public byte[] read(String path) throws IOException, CryptoException {
        DirectoryBox.Entry fileEntry = get(path);
        if (fileEntry == null)
            throw new IOException("Entry not found");
        assert fileEntry.isFile();

        FileBox fileBox = (FileBox)fileEntry.getObject();
        if (fileBox == null)
            fileBox = FileBox.read(transaction.getFileAccessor(path), fileEntry.getDataPointer());
        ChunkContainerInputStream inputStream = new ChunkContainerInputStream(fileBox.getDataContainer());
        return StreamHelper.readAll(inputStream);
    }

    public void put(String path, FileBox file) throws IOException, CryptoException {
        DirectoryBox.Entry entry = new DirectoryBox.Entry(true);
        entry.setObject(file);
        if (!file.getBoxPointer().getBoxHash().isZero())
            entry.setDataPointer(file.getBoxPointer());
        put(path, entry);
    }

    public DirectoryBox.Entry put(String path, BoxPointer dataPointer, boolean isFile) throws IOException,
            CryptoException {
        DirectoryBox.Entry entry = new DirectoryBox.Entry("", dataPointer, isFile);
        put(path, entry);
        return entry;
    }

    public void put(String path, DirectoryBox.Entry entry) throws IOException, CryptoException {
        path = checkPath(path);
        String[] parts = path.split("/");
        String fileName = parts[parts.length - 1];
        DirectoryBox currentDir = root;
        IChunkAccessor treeAccessor = transaction.getTreeAccessor();
        List<DirectoryBox.Entry> touchedEntries = new ArrayList<>();
        for (int i = 0; i < parts.length - 1; i++) {
            String subDir = parts[i];
            DirectoryBox.Entry currentEntry = currentDir.getEntry(subDir);
            if (currentEntry == null) {
                DirectoryBox subDirBox = DirectoryBox.create();
                currentEntry = currentDir.addDir(subDir, null);
                currentEntry.setObject(subDirBox);
                currentDir = subDirBox;
            } else {
                if (currentEntry.isFile())
                    throw new IOException("Invalid insert path: " + path);
                if (currentEntry.getObject() != null) {
                    currentDir = (DirectoryBox)currentEntry.getObject();
                } else {
                    currentDir = DirectoryBox.read(treeAccessor, currentEntry.getDataPointer());
                    currentEntry.setObject(currentDir);
                }
            }
            touchedEntries.add(currentEntry);
        }
        entry.setName(fileName);

        DirectoryBox.Entry existingEntry = currentDir.getEntry(fileName);
        if (existingEntry != null) {
            // check if something has changed
            if (entry.getDataPointer() != null
                    && currentDir.getEntry(fileName).getDataPointer().equals(entry.getDataPointer())) {
                return;
            }
        }

        for (DirectoryBox.Entry touched : touchedEntries) {
            touched.markModified();
        }
        this.modified = true;
        currentDir.put(fileName, entry);
    }

    public DirectoryBox.Entry remove(String path) throws IOException, CryptoException {
        this.modified = true;
        path = checkPath(path);
        String[] parts = path.split("/");
        String entryName = parts[parts.length - 1];
        DirectoryBox.Entry currentDir = get(parts, parts.length - 1, true);
        if (currentDir == null)
            return null;
        // invalidate entry
        currentDir.markModified();
        DirectoryBox directoryBox = (DirectoryBox)currentDir.getObject();
        return directoryBox.remove(entryName);
    }

    public BoxPointer build() throws IOException, CryptoException {
        modified = false;
        return build(root, "");
    }

    private BoxPointer build(DirectoryBox dir, String path) throws IOException, CryptoException {
        for (DirectoryBox.Entry child : dir.getDirs()) {
            if (child.getDataPointer() != null)
                continue;
            assert child.getObject() != null;
            child.setDataPointer(build((DirectoryBox)child.getObject(), path + "/" + child.getName()));
        }
        for (DirectoryBox.Entry child : dir.getFiles()) {
            if (child.getDataPointer() != null)
                continue;
            assert child.getObject() != null;
            FileBox fileBox = (FileBox)child.getObject();
            BoxPointer dataPointer = write(fileBox);
            child.setDataPointer(dataPointer);
        }
        return Repository.put(dir, transaction.getTreeAccessor());
    }

    public DirectoryBox getRoot() {
        return root;
    }
}