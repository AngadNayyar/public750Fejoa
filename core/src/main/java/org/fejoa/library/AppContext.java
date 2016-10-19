/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library;

import org.fejoa.library.database.StorageDir;


public class AppContext extends StorageDirObject {
    final private UserData userData;

    protected AppContext(FejoaContext context, StorageDir storageDir, UserData userData) {
        super(context, storageDir);

        this.userData = userData;
    }

    public UserData getUserData() {
        return userData;
    }

}