/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library;

import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.database.IOStorageDir;
import org.fejoa.library.database.MovableStorageList;

import java.io.IOException;


public class BranchList extends MovableStorageList<BranchInfo> {
    public BranchList(IOStorageDir storageDir) throws IOException, CryptoException {
        super(storageDir);
    }

    @Override
    protected BranchInfo createObject(IOStorageDir storageDir, String id) throws IOException, CryptoException {
        return new BranchInfo(storageDir, id);
    }


    public void add(BranchInfo branchInfo) throws IOException, CryptoException {
        super.add(branchInfo.getBranch(), branchInfo);
    }
}
