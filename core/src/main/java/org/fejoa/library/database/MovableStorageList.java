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
    final private Map<String, T> entries = new HashMap<>();

    public MovableStorageList(MovableStorageContainer parent, String subDir) throws IOException, CryptoException {
        super(parent, subDir);

        load();
    }

    public MovableStorageList(IOStorageDir storageDir) throws IOException, CryptoException {
        super(storageDir);

        load();
    }

    abstract protected T createObject(IOStorageDir storageDir, String id) throws IOException, CryptoException;

    public void add(String name, T entry) throws IOException, CryptoException {
        IOStorageDir subDir = getStorageDir(name);
        entry.setStorageDir(subDir);
        attach(entry, name);
        entries.put(name, entry);
    }

    public T get(String name) {
        return entries.get(name);
    }

    private IOStorageDir getStorageDir(String name) {
        return new IOStorageDir(storageDir, name);
    }

    private void load() throws IOException, CryptoException {
        Collection<String> subDirs = storageDir.listDirectories("");
        entries.clear();
        for (String dir : subDirs) {
            T entry = createObject(new IOStorageDir(storageDir, dir), dir);
            entries.put(dir, entry);
        }
    }

    public Collection<T> getEntries() {
        return entries.values();
    }
}
