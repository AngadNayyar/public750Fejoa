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
import org.fejoa.library.database.StorageDir;

import java.io.IOException;


public class AccessStore extends StorageDirObject {
    private StorageDirList<AccessToken> accessTokens;

    protected AccessStore(final FejoaContext context, StorageDir dir) {
        super(context, dir);

        accessTokens = new StorageDirList<>(storageDir,
                new StorageDirList.AbstractEntryIO<AccessToken>() {
                    @Override
                    public String getId(AccessToken entry) {
                        return entry.getId();
                    }

                    @Override
                    public AccessToken read(IOStorageDir dir) throws IOException, CryptoException {
                        return AccessToken.open(context, dir);
                    }
                });
    }

    public void addAccessToken(AccessToken token) throws IOException, CryptoException {
        accessTokens.add(token);
    }

    public String getId() {
        return getBranch();
    }
}
