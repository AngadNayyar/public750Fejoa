/*
 * Copyright 2015.
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
import java.security.PublicKey;


public class ContactPublic extends Contact<PublicKeyItem, PublicKeyItem> {
    final static private String REMOTES_DIR = "remotes";
    final static private String ACCESS_DIR = "access";

    private RemoteList remotes;
    private ContactBranchList contactBranchList;

    protected ContactPublic(FejoaContext context, IOStorageDir storageDir) {
        super(context, getEntryIO(), getEntryIO(),
                storageDir);
    }

    static private StorageDirList.AbstractEntryIO<PublicKeyItem> getEntryIO() {
        return new StorageDirList.AbstractEntryIO<PublicKeyItem>() {
            @Override
            public String getId(PublicKeyItem entry) {
                return entry.getId();
            }

            @Override
            public PublicKeyItem read(IOStorageDir dir) throws IOException, CryptoException {
                PublicKeyItem item = new PublicKeyItem();
                item.read(dir);
                return item;
            }
        };
    }

    @Override
    protected void setStorageDir(IOStorageDir dir) {
        super.setStorageDir(dir);

        remotes = new RemoteList(new IOStorageDir(storageDir, REMOTES_DIR));
        contactBranchList = new ContactBranchList(context, new IOStorageDir(storageDir, ACCESS_DIR));
    }

    @Override
    public PublicKey getVerificationKey(KeyId keyId) {
        PublicKeyItem keyItem = signatureKeys.get(keyId.toString());
        if (keyItem ==  null)
            return null;
        return keyItem.getKey();
    }

    public RemoteList getRemotes() {
        return remotes;
    }

    public ContactBranchList getContactBranchList() {
        return contactBranchList;
    }
}
