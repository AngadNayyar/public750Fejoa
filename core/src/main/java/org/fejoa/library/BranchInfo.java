/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library;

import org.fejoa.chunkstore.HashValue;
import org.fejoa.library.database.IOStorageDir;
import org.fejoa.library.remote.AuthInfo;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


public class BranchInfo implements IStorageDirBundle {
    public class CryptoInfo {
        private HashValue encKey;
        private String keyStoreId = "";
        private boolean signBranch;

        public void write(IOStorageDir dir) throws IOException {
            if (encKey != null && !encKey.isZero()) {
                dir.writeString(ENCRYPTION_KEY, encKey.toHex());
                dir.writeString(Constants.KEY_STORE_ID, keyStoreId);
            }
        }

        public void read(IOStorageDir dir) throws IOException {
            try {
                encKey = HashValue.fromHex(dir.readString(ENCRYPTION_KEY));
                keyStoreId = dir.readString(Constants.KEY_STORE_ID);
            } catch (IOException e) {
                // no enc key
            }
        }
    }

    final static private String DESCRIPTION_KEY = "description";
    final static private String ENCRYPTION_KEY = "encKey";
    final static private String REMOTE_IDS_KEY = "remotes";

    final private String branch;
    final private List<String> remotes = new ArrayList<>();
    private AuthInfo authInfo;

    private String description = "";
    private CryptoInfo cryptoInfo = new CryptoInfo();

    static public BranchInfo read(String id, IOStorageDir dir) throws IOException {
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

        cryptoInfo.encKey = encKey;
        if (keyStore != null)
            cryptoInfo.keyStoreId = keyStore.getId();
        cryptoInfo.signBranch = sign;
    }

    public String getBranch() {
        return branch;
    }

    public String getDescription() {
        return description;
    }

    public HashValue getKeyId() {
        return cryptoInfo.encKey;
    }

    public String getKeyStoreId() {
        return cryptoInfo.keyStoreId;
    }

    public boolean signBranch() {
        return cryptoInfo.signBranch;
    }

    @Override
    public void write(IOStorageDir dir) throws IOException {
        dir.writeString(DESCRIPTION_KEY, description);
        cryptoInfo.write(dir);

        writeRemotes(dir);
    }

    @Override
    public void read(IOStorageDir dir) throws IOException {
        description = dir.readString(DESCRIPTION_KEY);
        cryptoInfo.read(dir);

        readRemotes(dir);
    }

    private void writeRemotes(IOStorageDir dir) throws IOException {
        if (remotes.size() > 0) {
            JSONObject remotes = new JSONObject();
            try {
                remotes.put("ids", remotes);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            dir.writeString(REMOTE_IDS_KEY, remotes.toString());
        }
    }

    private void readRemotes(IOStorageDir dir) {
        try {
            JSONObject jsonObject = new JSONObject(dir.readString(REMOTE_IDS_KEY));
            JSONArray array = jsonObject.getJSONArray(REMOTE_IDS_KEY);
            remotes.clear();
            for (int i = 0; i < array.length(); i++)
                remotes.add(array.getString(i));
        } catch (Exception e) {

        }
    }

}