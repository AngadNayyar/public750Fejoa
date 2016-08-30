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


public class BranchList extends StorageDirList<BranchInfo> {
    public BranchList(StorageDir storageDir) {
        super(storageDir, new AbstractEntryIO<BranchInfo>() {
            @Override
            public String getId(BranchInfo entry) {
                return entry.getBranch();
            }

            @Override
            public BranchInfo read(StorageDir dir) throws IOException {
                return BranchInfo.read(idFromStoragePath(dir), dir);
            }
        });
    }
}
