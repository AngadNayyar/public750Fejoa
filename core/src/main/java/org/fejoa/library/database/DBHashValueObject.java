/*
 * Copyright 2017.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.database;

import java8.util.concurrent.CompletableFuture;
import java8.util.function.Function;
import org.fejoa.chunkstore.HashValue;


public class DBHashValueObject extends DBObject<HashValue> {
    public DBHashValueObject(String path) {
        super(path);
    }

    @Override
    protected CompletableFuture<Void> writeToDB(IOStorageDir dir, String path) {
        return dir.putBytesAsync(path, cache.getBytes());
    }

    @Override
    protected CompletableFuture<HashValue> readFromDB(IOStorageDir dir, String path) {
        return dir.readBytesAsync(path).thenApply(new Function<byte[], HashValue>() {
            @Override
            public HashValue apply(byte[] bytes) {
                return new HashValue(bytes);
            }
        });
    }
}
