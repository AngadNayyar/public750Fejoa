/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library;

import org.fejoa.library.database.IOStorageDir;
import org.fejoa.library.database.StorageDir;
import org.fejoa.library.crypto.CryptoException;

import java.io.IOException;


public class AccessStore extends StorageDirObject {
    private StorageDirList<AccessTokenServer> accessTokens;

    protected AccessStore(final FejoaContext context, StorageDir dir) {
        super(context, dir);

        accessTokens = new StorageDirList<>(storageDir,
                new StorageDirList.AbstractEntryIO<AccessTokenServer>() {
                    @Override
                    public String getId(AccessTokenServer entry) {
                        return entry.getId();
                    }

                    @Override
                    public AccessTokenServer read(IOStorageDir dir) throws IOException, CryptoException {
                        return AccessTokenServer.open(context, dir);
                    }
                });
    }

    public void addAccessToken(AccessTokenServer token) throws IOException, CryptoException {
        accessTokens.add(token);
    }

    public String getId() {
        return getBranch();
    }
}
