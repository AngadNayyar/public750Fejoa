/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.database;

import java8.util.concurrent.CompletableFuture;
import java8.util.function.Function;


public abstract class DBReadableObject<T> implements IDBContainerEntry {
    protected IOStorageDir dir;
    final protected String path;
    protected T cache;

    public DBReadableObject(String path) {
        this.path = path;
    }

    public DBReadableObject(IOStorageDir dir, String path) {
        this(path);

        setTo(dir);
    }

    abstract protected CompletableFuture<T> readFromDB(IOStorageDir dir, String path);

    @Override
    public void setTo(IOStorageDir dir) {
        this.dir = dir;
    }

    protected void setCache(T value) {
        synchronized (this) {
            this.cache = value;
        }
    }

    public CompletableFuture<T> get() {
        synchronized (this) {
            if (dir == null || cache != null)
                return CompletableFuture.completedFuture(cache);

            return readFromDB(dir, path).thenApply(new Function<T, T>() {
                @Override
                public T apply(T value) {
                    setCache(value);
                    return value;
                }
            });
        }
    }

    @Override
    public CompletableFuture<Void> flush() {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void invalidate() {
        setCache(null);
    }
}
