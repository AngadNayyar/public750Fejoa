/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.database;

import java8.util.concurrent.CompletableFuture;


public class DBString extends DBObject<String> {
    public DBString() {
        super("");
    }

    public DBString(String path) {
        super(path);
    }

    public DBString(IOStorageDir dir, String path) {
        super(dir, path);
    }

    @Override
    protected CompletableFuture<String> readFromDB(IOStorageDir dir, String path) {
        return dir.readStringAsync(path);
    }

    @Override
    protected CompletableFuture<Void> writeToDB(IOStorageDir dir, String path) {
        return dir.putStringAsync(path, cache);
    }
}
