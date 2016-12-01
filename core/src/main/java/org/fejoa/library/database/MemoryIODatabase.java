/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.database;

import org.fejoa.library.support.StorageLib;
import org.fejoa.library.crypto.CryptoException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;


class MemoryRandomDataAccess implements ISyncRandomDataAccess {
    interface IIOCallback {
        void onClose(MemoryRandomDataAccess that);
    }

    private byte[] buffer;
    final private IIOSyncDatabase.Mode mode;
    final private IIOCallback callback;
    private int position = 0;
    private ByteArrayInputStream inputStream = null;
    private ByteArrayOutputStream outputStream = null;

    public MemoryRandomDataAccess(byte[] buffer, IIOSyncDatabase.Mode mode, IIOCallback callback) {
        this.buffer = buffer;
        this.mode = mode;
        this.callback = callback;
    }

    public IIOSyncDatabase.Mode getMode() {
        return mode;
    }

    public byte[] getData() {
        return buffer;
    }

    @Override
    public long length() {
        if (outputStream != null)
            return Math.max(position, outputStream.toByteArray().length);
        return buffer.length;
    }

    @Override
    public long position() {
        return position;
    }

    @Override
    public void seek(long position) throws IOException, CryptoException {
        if (inputStream != null) // position is set on next read
            inputStream = null;
        else
            flush();
        this.position = (int)position;
    }

    @Override
    public void write(byte[] data) throws IOException {
        write(data, 0, data.length);
    }

    @Override
    public int read(byte[] buffer) throws IOException, CryptoException {
        return read(buffer, 0, buffer.length);
    }

    @Override
    public void write(byte[] data, int offset, int length) throws IOException {
        if (!mode.has(IIOSyncDatabase.Mode.WRITE))
            throw new IOException("Read only");
        if (inputStream != null)
            inputStream = null;
        if (outputStream == null) {
            outputStream = new ByteArrayOutputStream();
            outputStream.write(buffer, 0, position);
        }
        outputStream.write(data, offset, length);
        position += length;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException, CryptoException {
        if (!mode.has(IIOSyncDatabase.Mode.READ))
            throw new IOException("Not in read mode");

        if (outputStream != null) {
            flush();
        }
        if (inputStream == null) {
            inputStream = new ByteArrayInputStream(this.buffer);
            inputStream.skip(position);
        }
        int read = inputStream.read(buffer, offset, length);
        position += read;
        return read;
    }

    @Override
    public void flush() throws IOException {
        if (outputStream == null)
            return;
        // if we didn't overwrite the whole buffer copy the remaining bytes
        if (position < this.buffer.length)
            outputStream.write(buffer, position, buffer.length - position);
        this.buffer = outputStream.toByteArray();
        outputStream = null;
    }

    @Override
    public void close() throws IOException, CryptoException {
        flush();
        callback.onClose(this);
    }
}

public class MemoryIODatabase implements IIOSyncDatabase {
    static class Dir {
        final private Dir parent;
        final private String name;
        final private Map<String, Dir> dirs = new HashMap<>();
        final private Map<String, byte[]> files = new HashMap<>();

        public Dir(Dir parent, String name) {
            this.parent = parent;
            this.name = name;
        }

        public Dir getSubDir(String dir, boolean createMissing) {
            Dir subDir = this;
            if (dir.equals(""))
                return subDir;
            String[] parts = dir.split("/");
            for (String part : parts) {
                Dir subSubDir = subDir.dirs.get(part);
                if (subSubDir == null) {
                    if (!createMissing)
                        return null;
                    Dir newSubDir = new Dir(subDir, part);
                    subDir.dirs.put(part, newSubDir);
                    subDir = newSubDir;
                } else
                    subDir = subSubDir;
            }
            return subDir;
        }

        public void put(String path, byte[] data) {
            String dirPath = StorageLib.dirName(path);
            String fileName = StorageLib.fileName(path);
            Dir subDir = getSubDir(dirPath, true);
            subDir.files.put(fileName, data);
        }

        public byte[] get(String path) {
            String dirPath = StorageLib.dirName(path);
            String fileName = StorageLib.fileName(path);
            Dir subDir = getSubDir(dirPath, false);
            if (subDir == null)
                return null;
            return subDir.files.get(fileName);
        }

        public boolean hasFile(String path) {
            String dirPath = StorageLib.dirName(path);
            String fileName = StorageLib.fileName(path);
            Dir subDir = getSubDir(dirPath, false);
            if (subDir == null)
                return false;
            return subDir.files.containsKey(fileName);
        }

        public void remove(String path) {
            String dirPath = StorageLib.dirName(path);
            String fileName = StorageLib.fileName(path);
            Dir subDir = getSubDir(dirPath, false);
            if (subDir == null)
                return;
            subDir.files.remove(fileName);
            while (subDir.files.size() == 0 && subDir.dirs.size() == 0 && subDir.parent != null) {
                Dir parent = subDir.parent;
                parent.dirs.remove(subDir.name);
                subDir = parent;
            }
        }
    }

    final Dir root = new Dir(null, "");

    @Override
    public boolean hasFile(String path) throws IOException, CryptoException {
        path = validate(path);
        return root.hasFile(path);
    }

    private byte[] readBytesInternal(String path) {
        path = validate(path);
        return root.get(path);
    }

    private List<String> getList(Map<String, List<String>> map, String path) {
        List<String> list = map.get(path);
        if (list == null) {
            list = new ArrayList<>();
            map.put(path, list);
        }
        return list;
    }

    private String validate(String path) {
        while (path.length() > 0 && path.charAt(0) == '/')
            path = path.substring(1);
        return path;
    }

    public byte[] readBytes(String path) throws IOException, CryptoException {
        byte[] bytes = readBytesInternal(path);
        if (bytes == null)
            throw new IOException("Not found!");
        return bytes;

       /* ISyncRandomDataAccess dataAccess = open(path, Mode.READ);
        byte[] data = StreamHelper.readAll(dataAccess);
        dataAccess.close();
        return data;*/
    }

    public void putBytes(String path, byte[] bytes) throws IOException, CryptoException {
        root.put(validate(path), bytes);
        /*ISyncRandomDataAccess dataAccess = open(path, Mode.TRUNCATE);
        dataAccess.write(bytes);
        dataAccess.close();*/
    }

    @Override
    public ISyncRandomDataAccess open(final String path, Mode mode) throws IOException, CryptoException {
        byte[] existingBytes = readBytesInternal(path);
        if (existingBytes == null) {
            if (!mode.has(Mode.WRITE))
                throw new FileNotFoundException("File not found: " + path);
            existingBytes = new byte[0];
        }
        return new MemoryRandomDataAccess(existingBytes, mode, new MemoryRandomDataAccess.IIOCallback() {
            @Override
            public void onClose(MemoryRandomDataAccess that) {
                if (!that.getMode().has(Mode.WRITE))
                    return;
                root.put(validate(path), that.getData());
            }
        });
    }

    @Override
    public void remove(String path) throws IOException, CryptoException {
        path = validate(path);
        root.remove(path);
    }

    @Override
    public Collection<String> listFiles(String path) throws IOException, CryptoException {
        Dir parentDir = root.getSubDir(path, false);
        if (parentDir == null)
            return Collections.emptyList();
        return parentDir.files.keySet();
    }

    @Override
    public Collection<String> listDirectories(String path) throws IOException, CryptoException {
        Dir parentDir = root.getSubDir(path, false);
        if (parentDir == null)
            return Collections.emptyList();
        return parentDir.dirs.keySet();
    }

    public Map<String, byte[]> getEntries() {
        Map<String, byte[]> out = new HashMap<>();
        getEntries(out, root, "");
        return out;
    }

    private void getEntries(Map<String, byte[]> out, Dir dir, String path) {
        for (Map.Entry<String, byte[]> entry : dir.files.entrySet())
            out.put(StorageLib.appendDir(path, entry.getKey()), entry.getValue());
        for (Map.Entry<String, Dir> entry : dir.dirs.entrySet())
            getEntries(out, entry.getValue(), StorageLib.appendDir(path, entry.getKey()));
    }
}
