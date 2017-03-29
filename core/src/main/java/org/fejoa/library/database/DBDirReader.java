/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.database;

import java8.util.concurrent.CompletableFuture;

import java.util.Collection;


public class DBDirReader extends DBReadableObject<Collection<String>> {
    public DBDirReader() {
        super("");
    }

    public DBDirReader(IOStorageDir dir) {
        super(dir, "");
    }

    @Override
    protected CompletableFuture<Collection<String>> readFromDB(IOStorageDir dir, String path) {
        return dir.listDirectoriesAsync(path);
    }
}
