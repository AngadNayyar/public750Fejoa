/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.filestorage;

import org.fejoa.library.IContactPublic;
import org.fejoa.library.UserData;
import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.database.IOStorageDir;
import org.fejoa.library.database.MovableStorageContainer;
import org.fejoa.library.database.MovableStorageList;
import org.fejoa.library.support.StorageLib;

import java.io.IOException;


public class ContactFileStorage extends MovableStorageContainer {
    private IContactPublic contact;

    final private MovableStorageList<FileStorageEntry> storageList;

    static ContactFileStorage open(IOStorageDir dir, UserData userData) {
        String contactId = StorageLib.fileName(dir.getBaseDir());
        IContactPublic contact = null;
        if (userData.getMyself().getId().equals(contactId))
            contact = userData.getMyself();
        else {
            for (IContactPublic contactPublic : userData.getContactStore().getContactList().getEntries()) {
                if (contactPublic.getId().equals(contactId)) {
                    contact = contactPublic;
                    break;
                }
            }
        }
        ContactFileStorage contactFileStorage = new ContactFileStorage(dir);
        contactFileStorage.contact = contact;
        return contactFileStorage;
    }

    static ContactFileStorage create(IContactPublic contact) {
        ContactFileStorage contactFileStorage = new ContactFileStorage(null);
        contactFileStorage.contact = contact;
        return contactFileStorage;
    }

    private ContactFileStorage(IOStorageDir dir) {
        super(dir);
        storageList = new MovableStorageList<FileStorageEntry>(dir) {
            @Override
            protected FileStorageEntry readObject(IOStorageDir storageDir) throws IOException, CryptoException {
                FileStorageEntry entry = new FileStorageEntry(storageDir);
                entry.load(getContact());
                return entry;
            }
        };
    }

    public MovableStorageList<FileStorageEntry> getStorageList() {
        return storageList;
    }

    public IContactPublic getContact() {
        return contact;
    }
}
