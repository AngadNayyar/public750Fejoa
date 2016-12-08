/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.database;

import java8.util.concurrent.CompletableFuture;

import java.util.ArrayList;
import java.util.List;


public class DBObjectContainer implements IDBContainerEntry {
    protected IOStorageDir storageDir;
    final List<Item> children = new ArrayList<>();

    static class Item {
        final public String relativePath;
        final public IDBContainerEntry entry;

        public Item(String relativePath, IDBContainerEntry entry) {
            this.relativePath = relativePath;
            this.entry = entry;
        }
    }

    public DBObjectContainer() {
    }

    public DBObjectContainer(IOStorageDir dir) {
        setTo(dir);
    }

    public void add(IDBContainerEntry value) {
        add(value, "");
    }

    public void add(IDBContainerEntry value, String relativePath) {
        children.add(new Item(relativePath, value));
        if (storageDir != null)
            setChildStorageDir(value, storageDir, relativePath);
    }

    private void setChildStorageDir(IDBContainerEntry child, IOStorageDir dir, String relativePath) {
        if (relativePath.equals(""))
            child.setTo(dir);
        else
            child.setTo(new IOStorageDir(dir, relativePath));
    }

    private StorageDir.IListener listener;

    public void setTo(StorageDir dir, boolean monitorDir) {
        setTo(dir);

        if (monitorDir) {
            listener = new StorageDir.IListener() {
                @Override
                public void onTipChanged(DatabaseDiff diff) {
                    invalidate();
                }
            };
            dir.addListener(listener);
        }
    }

    @Override
    public void setTo(IOStorageDir dir) {
        this.storageDir = dir;

        for (Item item : children)
            setChildStorageDir(item.entry, dir, item.relativePath);
    }

    @Override
    public CompletableFuture<Void> flush() {
        CompletableFuture<Void>[] jobs = new CompletableFuture[children.size()];
        int i = 0;
        for (Item item : children) {
            jobs[i] = item.entry.flush();
            i++;
        }

        return CompletableFuture.allOf(jobs);
    }

    @Override
    public void invalidate() {
        for (Item item : children)
            item.entry.invalidate();
    }
}
