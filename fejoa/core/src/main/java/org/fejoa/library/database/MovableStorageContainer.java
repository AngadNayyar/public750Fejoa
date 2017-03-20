/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.database;

import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.database.IOStorageDir;
import org.fejoa.library.database.MovableStorage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


public class MovableStorageContainer extends MovableStorage {
    static private class ChildEntry {
        final public MovableStorage storage;
        final public String subDir;

        public ChildEntry(MovableStorage storage, String subDir) {
            this.storage = storage;
            this.subDir = subDir;
        }
    }

    final private List<ChildEntry> children = new ArrayList<>();

    public MovableStorageContainer(MovableStorageContainer parent, String subDir) {
        super(parent, subDir);
    }

    public MovableStorageContainer(IOStorageDir storageDir) {
        super(storageDir);
    }

    protected void attach(MovableStorage storage, String subDir) {
        children.add(new ChildEntry(storage, subDir));
    }

    @Override
    public void setStorageDir(IOStorageDir target) throws IOException, CryptoException {
        super.setStorageDir(target);

        for (ChildEntry child : children)
            child.storage.setStorageDir(new IOStorageDir(target, child.subDir));
    }
}
