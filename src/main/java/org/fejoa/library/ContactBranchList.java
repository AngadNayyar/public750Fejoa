/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library;

import org.fejoa.library.database.StorageDir;

import java.io.IOException;


public class ContactBranchList extends StorageDirList<ContactBranch> {
    static final private String ACCESS_TOKEN_KEY = "accessToken";
    static final private String KEY_DATA_DIR = "keyData";

    public ContactBranchList(final FejoaContext context, StorageDir storageDir) {
        super(storageDir, new IEntryIO<ContactBranch>() {
            @Override
            public String getId(ContactBranch entry) {
                return entry.getBranch();
            }

            @Override
            public ContactBranch read(StorageDir dir) throws IOException {
                try {
                    String branch = dir.readString(Constants.BRANCH_KEY);
                    AccessTokenContact token = new AccessTokenContact(context, dir.readString(ACCESS_TOKEN_KEY));
                    SymmetricKeyData keyData = null;
                    if (dir.listDirectories("").contains(KEY_DATA_DIR)) {
                        keyData = new SymmetricKeyData();
                        keyData.read(new StorageDir(dir, KEY_DATA_DIR));
                    }
                    return new ContactBranch(branch, keyData, token);
                } catch (Exception e) {
                    throw new IOException(e.getMessage());
                }
            }

            @Override
            public void write(ContactBranch entry, StorageDir dir) throws IOException {
                dir.writeString(Constants.BRANCH_KEY, entry.getBranch());
                dir.writeString(ACCESS_TOKEN_KEY, entry.getAccessToken().getRawAccessToken());
                if (entry.getBranchKey() != null)
                    entry.getBranchKey().write(new StorageDir(dir, KEY_DATA_DIR));
            }
        });
    }
}
