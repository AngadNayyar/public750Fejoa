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


public class ContactStore extends StorageDirObject {
    final static private String REQUESTED_CONTACTS_DIR = "requestedContacts";

    private StorageDirList<ContactPublic> contactList;
    private StorageDirList<ContactPublic> requestedContacts;

    final private StorageDirList.IEntryIO<ContactPublic> entryIO = new StorageDirList.IEntryIO<ContactPublic>() {
        @Override
        public String getId(ContactPublic entry) {
            return entry.getId();
        }

        @Override
        public ContactPublic read(IOStorageDir dir) throws IOException {
            return new ContactPublic(context, dir);
        }

        @Override
        public void write(ContactPublic entry, IOStorageDir dir) throws IOException {

        }
    };

    protected ContactStore(final FejoaContext context, StorageDir dir) {
        super(context, dir);

        contactList = new StorageDirList<>(storageDir, entryIO);
        requestedContacts = new StorageDirList<>(new StorageDir(storageDir, REQUESTED_CONTACTS_DIR), entryIO);
    }

    public void addContact(ContactPublic contact) throws IOException, CryptoException {
        contact.setStorageDir(contactList.getStorageDirForId(contact.getId()));
        contactList.add(contact);
    }

    public StorageDirList<ContactPublic> getContactList() {
        return contactList;
    }

    public StorageDirList<ContactPublic> getRequestedContacts() {
        return requestedContacts;
    }

    public IContactFinder<IContactPublic> getContactFinder() {
        return new IContactFinder<IContactPublic>() {
            @Override
            public ContactPublic get(String contactId) {
                return contactList.get(contactId);
            }
        };
    }
}
