/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library;

import org.fejoa.library.database.StorageDir;


public class ConfigStore extends StorageDirObject {
    protected ConfigStore(FejoaContext context, StorageDir storageDir) {
        super(context, storageDir);
    }

    public StorageDir getConfigDir(String configId) {
        return new StorageDir(storageDir, configId);
    }
}
