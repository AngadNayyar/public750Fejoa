/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.database;

import org.fejoa.library.crypto.CryptoException;

import java.io.IOException;


public class MovableStorage {
    protected IOStorageDir storageDir;

    public MovableStorage(MovableStorageContainer parent, String subDir) {
        this(new IOStorageDir(parent.storageDir, subDir));

        parent.attach(this, subDir);
    }

    public MovableStorage(IOStorageDir storageDir) {
        if (storageDir == null)
            this.storageDir = new IOStorageDir(AsyncInterfaceUtil.fakeAsync(new MemoryIODatabase()), "");
        else
            this.storageDir = storageDir;
    }

    public void setStorageDir(IOStorageDir target) throws IOException, CryptoException {
        if (this.storageDir == target)
            return;
        storageDir.copyTo(target);
        this.storageDir = target;
    }
}
