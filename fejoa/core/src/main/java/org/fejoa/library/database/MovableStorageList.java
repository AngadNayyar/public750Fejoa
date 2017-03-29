/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.database;

import org.fejoa.library.crypto.CryptoException;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;


public abstract class MovableStorageList<T extends MovableStorage> extends MovableStorageContainer {
    public MovableStorageList(MovableStorageContainer parent, String subDir) {
        super(parent, subDir);
    }

    public MovableStorageList(IOStorageDir storageDir) {
        super(storageDir);
    }

    abstract protected T readObject(IOStorageDir storageDir) throws IOException, CryptoException;

    public void add(String name, T entry) throws IOException, CryptoException {
        IOStorageDir subDir = getStorageDir(name);
        entry.setStorageDir(subDir);
        attach(entry, name);
    }

    public T get(String name) throws IOException, CryptoException {
        return readObject(new IOStorageDir(storageDir, name));
    }

    private IOStorageDir getStorageDir(String name) {
        return new IOStorageDir(storageDir, name);
    }

    protected Map<String, T> load(IOStorageDir storageDir) throws IOException, CryptoException {
        Collection<String> subDirs = storageDir.listDirectories("");
        Map<String, T> entries = new HashMap<>();
        for (String dir : subDirs) {
            try {
                T entry = readObject(new IOStorageDir(storageDir, dir));
                entries.put(dir, entry);
            } catch (Exception e) {
                continue;
            }
        }
        return entries;
    }

    public Collection<T> getEntries() throws IOException, CryptoException {
        return load(storageDir).values();
    }
}
