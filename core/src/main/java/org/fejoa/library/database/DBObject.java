/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.database;

import java8.util.concurrent.CompletableFuture;
import java8.util.function.BiConsumer;


public abstract class DBObject<T> extends DBReadableObject<T> {
    private boolean dirty = false;

    public DBObject(String path) {
        super(path);
    }

    public DBObject(IOStorageDir dir, String path) {
        super(dir, path);
    }

    abstract protected CompletableFuture<Void> writeToDB(IOStorageDir dir, String path);


    public void set(T value) {
        setCache(value, true);
    }

    @Override
    protected void setCache(T value) {
        setCache(value, false);
    }

    private void setCache(T value, boolean dirty) {
        synchronized (this) {
            this.cache = value;
            this.dirty = dirty;
        }
    }

    private void setDirty(boolean dirty) {
        synchronized (this) {
            this.dirty = dirty;
        }
    }

    @Override
    public CompletableFuture<Void> flush() {
        if (!dirty)
            return CompletableFuture.completedFuture(null);
        return writeToDB(dir, path).whenComplete(new BiConsumer<Void, Throwable>() {
            @Override
            public void accept(Void aVoid, Throwable throwable) {
                if (throwable != null)
                    setDirty(false);
            }
        });
    }

    @Override
    public void invalidate() {
        setCache(null, false);
    }
}
