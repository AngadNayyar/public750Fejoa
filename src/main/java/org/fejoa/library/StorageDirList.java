/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library;

import org.fejoa.library.database.StorageDir;

import java.io.IOException;
import java.util.*;


public class StorageDirList<T> {
    public interface IEntryIO<T> {
        String getId(T entry);
        T read(StorageDir dir) throws IOException;
        void write(T entry, StorageDir dir) throws IOException;
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
        public void write(T entry, StorageDir dir) throws IOException {
            entry.write(dir);
        }

        protected String idFromStoragePath(StorageDir dir) {
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
    protected StorageDir storageDir;

    private T defaultEntry = null;

    protected void load() {
        List<String> dirs;
        try {
            dirs = storageDir.listDirectories("");
        } catch (IOException e) {
            return;
        }
        Collections.sort(dirs);
        for (String dir : dirs) {
            StorageDir subDir = new StorageDir(storageDir, dir);
            T entry;
            try {
                entry = entryIO.read(subDir);
            } catch (IOException e) {
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

    public StorageDirList(IEntryIO<T> entryIO) {
        this.entryIO = entryIO;
    }

    public StorageDirList(StorageDir storageDir, IEntryIO<T> entryIO) {
        this.entryIO = entryIO;

        setTo(storageDir);
    }

    public void setTo(StorageDir storageDir) {
        this.storageDir = storageDir;

        map.clear();
        load();
    }

    public Collection<T> getEntries() {
        return map.values();
    }

    public String add(T entry) throws IOException {
        String id = entryIO.getId(entry);
        StorageDir subDir = new StorageDir(storageDir, id);
        entryIO.write(entry, subDir);
        map.put(id, entry);
        return id;
    }

    public void update(T entry) throws IOException {
        remove(entryIO.getId(entry));
        add(entry);
    }

    public void remove(String key) {
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

