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
import org.fejoa.library.database.MovableStorageContainer;
import org.fejoa.library.database.MovableStorageList;

import java.io.IOException;


public class ContactAccessList extends MovableStorageList<ContactAccess> {
    public ContactAccessList(MovableStorageContainer parent, String subDir) {
        super(parent, subDir);
    }

    @Override
    protected ContactAccess readObject(IOStorageDir storageDir) throws IOException, CryptoException {
        return new ContactAccess(storageDir);
    }

    public ContactAccess add(ContactPublic contactPublic, AccessToken accessToken) throws IOException, CryptoException {
        ContactAccess contactAccess = new ContactAccess(contactPublic);
        add(contactPublic.getId(), contactAccess);
        contactAccess.setAccessToken(accessToken);
        return contactAccess;
    }
}
