/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library;

import org.fejoa.chunkstore.HashValue;
import org.fejoa.library.database.StorageDir;

import java.io.IOException;


public class BranchInfo implements IStorageDirBundle {
    final static private String DESCRIPTION_KEY = "description";
    final static private String ENCRYPTION_KEY = "encKey";

    final private String branch;

    private String description = "";

    private HashValue encKey;
    private String keyStoreId = "";

    private boolean signBranch;

    static public BranchInfo read(String id, StorageDir dir) throws IOException {
        BranchInfo entry = new BranchInfo(id);
        entry.read(dir);
        return entry;
    }

    public BranchInfo(String branch) {
        this.branch = branch;
    }

    public BranchInfo(String branch, String description) {
        this.branch = branch;
        this.description = description;
    }

    public BranchInfo(String branch, String description, HashValue encKey, KeyStore keyStore, boolean sign) {
        this(branch, description);

        this.encKey = encKey;
        if (keyStore != null)
            this.keyStoreId = keyStore.getId();
        this.signBranch = sign;
    }

    public String getBranch() {
        return branch;
    }

    public String getDescription() {
        return description;
    }

    public HashValue getKeyId() {
        return encKey;
    }

    public String getKeyStoreId() {
        return keyStoreId;
    }

    public boolean signBranch() {
        return signBranch;
    }

    @Override
    public void write(StorageDir dir) throws IOException {
        dir.writeString(DESCRIPTION_KEY, description);
        if (encKey != null && !encKey.isZero())
            dir.writeString(ENCRYPTION_KEY, encKey.toHex());
    }

    @Override
    public void read(StorageDir dir) throws IOException {
        description = dir.readString(DESCRIPTION_KEY);
        try {
            encKey = HashValue.fromHex(dir.readString(ENCRYPTION_KEY));
        } catch (IOException e) {
            // no enc key
        }
    }
}