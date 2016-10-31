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
    final private UserData userData;

    protected ConfigStore(FejoaContext context, StorageDir storageDir, UserData userData) {
        super(context, storageDir);

        this.userData = userData;
    }

    public AppContext getAppContext(String appId) {
        String path = appId.replace('.', '/');
        return new AppContext(context, appId, new StorageDir(storageDir, path), userData);
    }
}
