/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library2;

import org.fejoa.library2.database.StorageDir;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


abstract class AbstractStorageDirList {
    public interface IEntry {
        void write(StorageDir dir) throws IOException;
        void read(StorageDir dir) throws IOException;
    }

    final protected Map<String, IEntry> map = new HashMap<>();
    final protected StorageDir storageDir;

    abstract protected IEntry instantiate(StorageDir dir);

    protected void load() {
        List<String> dirs;
        try {
            dirs = storageDir.listDirectories("");
        } catch (IOException e) {
            return;
        }
        for (String dir : dirs) {
            StorageDir subDir = new StorageDir(storageDir, dir);
            IEntry entry = instantiate(subDir);
            try {
                entry.read(subDir);
            } catch (IOException e) {
                e.printStackTrace();
                continue;
            }
            map.put(dir, entry);
        }
    }

    protected AbstractStorageDirList(StorageDir storageDir) {
        this.storageDir = storageDir;
    }

    public void add(IEntry entry) throws IOException {
        String key = getFreeKey();
        StorageDir subDir = new StorageDir(storageDir, key);
        entry.write(subDir);
        map.put(key, entry);
    }

    private String getFreeKey() {
        for (int i = 0; i < Integer.MAX_VALUE; i++) {
            String key = Integer.toString(i);
            if (!map.containsKey(key))
                return key;
        }
        return "full";
    }
}
