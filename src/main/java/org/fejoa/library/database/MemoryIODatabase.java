/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.database;

import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.support.StorageLib;

import java.io.IOException;
import java.util.*;


public class MemoryIODatabase implements IIODatabaseInterface {

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

    @Override
    public byte[] readBytes(String path) throws IOException, CryptoException {
        path = validate(path);
        byte[] data = root.get(path);
        if (data == null)
            throw new IOException("No data at path: " + path);
        return data;
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

    @Override
    public void writeBytes(String path, byte[] bytes) throws IOException, CryptoException {
        path = validate(path);
        root.put(path, bytes);
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
