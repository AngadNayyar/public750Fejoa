/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library;

import org.fejoa.library.database.StorageDir;

import java.io.IOException;


public class StorageDirObject {
    final protected FejoaContext context;
    final protected StorageDir storageDir;

    protected StorageDirObject(FejoaContext context, StorageDir storageDir) {
        this.context = context;
        this.storageDir = storageDir;
    }

    public StorageDir getStorageDir() {
        return storageDir;
    }

    public FejoaContext getContext() {
        return context;
    }

    public void commit() throws IOException {
        storageDir.commit();
    }

    public String getBranch() {
        return storageDir.getBranch();
    }
}
