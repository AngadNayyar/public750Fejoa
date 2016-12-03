/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library;

import java8.util.concurrent.CompletableFuture;
import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.database.IOStorageDir;
import org.fejoa.library.database.MovableStorage;

import java.io.IOException;


public class ContactAccess extends MovableStorage {
    final static public String CONTACT_ID_KEY = "contactId";
    final static public String ACCESS_TOKEN_DIR = "accessToken";

    public ContactAccess(IOStorageDir storageDir) {
        super(storageDir);
    }

    public ContactAccess(ContactPublic contactPublic) throws IOException {
        super(null);

        setContact(contactPublic);
    }

    public void setContact(ContactPublic contactPublic) throws IOException {
        storageDir.writeString(CONTACT_ID_KEY, contactPublic.getId());
    }

    public CompletableFuture<String> getContact() {
        return storageDir.readStringAsync(CONTACT_ID_KEY);
    }

    public void setAccessToken(AccessToken token) throws IOException, CryptoException {
        IOStorageDir tokenDir = new IOStorageDir(storageDir, ACCESS_TOKEN_DIR);
        token.write(tokenDir);
    }
}
