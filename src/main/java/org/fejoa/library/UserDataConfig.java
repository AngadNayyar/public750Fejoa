/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library;

import org.fejoa.chunkstore.HashValue;
import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.crypto.CryptoHelper;
import org.fejoa.library.database.IOStorageDir;
import org.fejoa.library.database.StorageDir;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


class SyncConfig extends StorageDirList<SyncConfig.SyncConfigEntry> {
    static class SyncConfigEntry implements IStorageDirBundle {
        private HashValue branch;
        private String type;
        private List<HashValue> storages = new ArrayList<>();

        static final private String TYPE_KEY = "type";
        static final private String STORAGE_KEY = "storage";
        static final private String DATA_KEY = "data";

        private JSONObject toJson() throws JSONException {
            JSONObject object = new JSONObject();
            object.put(TYPE_KEY, type);
            object.put(Constants.BRANCH_KEY, branch.toHex());
            object.put(STORAGE_KEY, new JSONArray(storages));
            return object;
        }

        private void fromJson(String string) throws JSONException {
            JSONObject object = new JSONObject(string);
            type = object.getString(TYPE_KEY);
            branch = HashValue.fromHex(object.getString(Constants.BRANCH_KEY));
            JSONArray storageArray = object.getJSONArray(STORAGE_KEY);
            storages.clear();
            for (int i = 0; i < storageArray.length(); i++)
                storages.add(HashValue.fromHex(storageArray.getString(i)));
        }

        public String getId() {
            return branch.toHex();
        }

        @Override
        public void write(IOStorageDir dir) throws IOException {
            try {
                dir.writeString(DATA_KEY, toJson().toString());
            } catch (JSONException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }

        @Override
        public void read(IOStorageDir dir) throws IOException {
            try {
                fromJson(dir.readString(DATA_KEY));
            } catch (JSONException e) {
                e.printStackTrace();
                throw new IOException(e);
            }
        }
    }

    public SyncConfig() {
        super(createEntryIO());
    }

    public SyncConfig(StorageDir storageDir) {
        super(storageDir, createEntryIO());
    }

    static private IEntryIO<SyncConfigEntry> createEntryIO() {
        return new IEntryIO<SyncConfigEntry>() {
            @Override
            public String getId(SyncConfigEntry entry) {
                return entry.getId();
            }

            @Override
            public SyncConfigEntry read(IOStorageDir dir) throws IOException {
                SyncConfigEntry entry = new SyncConfigEntry();
                entry.read(dir);
                return entry;
            }

            @Override
            public void write(SyncConfigEntry entry, IOStorageDir dir) throws IOException {
                entry.write(dir);
            }
        };
    }
}


public class UserDataConfig extends StorageDirObject {
    final private UserData userData;
    final static private String SYNC_PATH = "sync";

    protected UserDataConfig(FejoaContext context, StorageDir storageDir, UserData userData) {
        super(context, storageDir);

        this.userData = userData;
    }

    static public UserDataConfig create(FejoaContext context, UserData userData, String configApp)
            throws IOException, CryptoException {
        String branch = CryptoHelper.sha1HashHex(context.getCrypto().generateInitializationVector(32));
        StorageDir storageDir = new StorageDir(userData.getConfigStore().getConfigDir(configApp), branch);

        UserDataConfig config = new UserDataConfig(context, storageDir, userData);

        return config;
    }

    static public UserDataConfig open(FejoaContext context, StorageDir storageDir, UserData userData) {
        return new UserDataConfig(context, storageDir, userData);
    }

    public UserData getUserData() {
        return userData;
    }

}
