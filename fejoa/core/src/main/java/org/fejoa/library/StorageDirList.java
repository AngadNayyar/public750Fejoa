/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library;

import org.fejoa.library.database.MovableStorage;
import org.fejoa.library.database.IOStorageDir;
import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.database.MovableStorageContainer;

import java.io.IOException;
import java.util.*;


public class StorageDirList<T> extends MovableStorage {
    public interface IEntryIO<T> {
        String getId(T entry);
        T read(IOStorageDir dir) throws IOException, CryptoException;
        void write(T entry, IOStorageDir dir) throws IOException, CryptoException;
    }

    abstract static public class AbstractIdEntry implements IStorageDirBundle {
        private String id;

        public AbstractIdEntry(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }
    }

    abstract static public class AbstractEntryIO<T extends IStorageDirBundle> implements IEntryIO<T> {
        @Override
        public void write(T entry, IOStorageDir dir) throws IOException, CryptoException {
            entry.write(dir);
        }

        protected String idFromStoragePath(IOStorageDir dir) {
            String baseDir = dir.getBaseDir();
            int lastSlash = baseDir.lastIndexOf("/");
            if (lastSlash < 0)
                return baseDir;
            return baseDir.substring(lastSlash + 1);
        }
    }

    static final private String DEFAULT_KEY = "default";

    final private IEntryIO<T> entryIO;
    final private Map<String, T> map = new HashMap<>();

    private T defaultEntry = null;

    protected void load() {
        List<String> dirs;
        try {
            dirs = new ArrayList<>(storageDir.listDirectories(""));
        } catch (Exception e) {
            return;
        }
        Collections.sort(dirs);
        for (String dir : dirs) {
            IOStorageDir subDir = new IOStorageDir(storageDir, dir);
            T entry;
            try {
                entry = entryIO.read(subDir);
            } catch (Exception e) {
                e.printStackTrace();
                continue;
            }
            map.put(dir, entry);
        }

        String defaultId;
        try {
            defaultId = storageDir.readString(DEFAULT_KEY);
        } catch (IOException e) {
            return;
        }

        defaultEntry = get(defaultId);
    }

    public StorageDirList(MovableStorageContainer parent, String subDir, IEntryIO<T> entryIO) {
        super(parent, subDir);
        this.entryIO = entryIO;

        load();
    }

    public StorageDirList(IOStorageDir storageDir, IEntryIO<T> entryIO) {
        super(storageDir);
        this.entryIO = entryIO;

        load();
    }

    @Override
    public void setStorageDir(IOStorageDir target) throws IOException, CryptoException {
        super.setStorageDir(target);

        map.clear();
        load();
    }

    public Collection<T> getEntries() {
        return map.values();
    }

    public String add(T entry, boolean setDefault) throws IOException, CryptoException {
        String id = add(entry);
        if (setDefault)
            setDefault(id);
        return id;
    }

    public String add(T entry) throws IOException, CryptoException {
        String id = entryIO.getId(entry);
        IOStorageDir subDir = getStorageDirForId(id);
        entryIO.write(entry, subDir);
        map.put(id, entry);
        return id;
    }

    protected IOStorageDir getStorageDirForId(String id) {
        return new IOStorageDir(storageDir, id);
    }

    public void update(T entry) throws IOException, CryptoException {
        remove(entryIO.getId(entry));
        add(entry);
    }

    public void remove(String key) throws IOException, CryptoException {
        storageDir.remove(key);
        map.remove(key);
    }

    public T get(String id) {
        return map.get(id);
    }

    public void setDefault(String id) throws IOException {
        T entry = get(id);
        if (entry == null)
            throw new IOException("no entry with give id");
        setDefault(entry);
    }

    public void setDefault(T entry) throws IOException {
        String id = entryIO.getId(entry);
        if (get(id) == null)
            throw new IOException("entry not in list");

        defaultEntry = entry;
        storageDir.writeString(DEFAULT_KEY, id);
    }

    public T getDefault() {
        return defaultEntry;
    }
}

