/*
 * Copyright 2015-2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library;

import org.fejoa.chunkstore.HashValue;
import org.fejoa.library.database.MovableStorage;
import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.database.IOStorageDir;
import org.fejoa.library.database.MovableStorageContainer;
import org.fejoa.library.database.MovableStorageList;
import org.fejoa.library.remote.AuthInfo;

import java.io.IOException;
import java.util.Collection;


class ContactAccessList extends MovableStorageList<ContactAccess> {
    public ContactAccessList(MovableStorageContainer parent, String subDir) throws IOException, CryptoException {
        super(parent, subDir);
    }

    @Override
    protected ContactAccess createObject(IOStorageDir storageDir, String id) throws IOException, CryptoException {
        return new ContactAccess(storageDir);
    }

    public ContactAccess add(ContactPublic contactPublic, AccessToken accessToken) throws IOException, CryptoException {
        ContactAccess contactAccess = new ContactAccess(contactPublic);
        add(contactPublic.getId(), contactAccess);
        contactAccess.setAccessToken(accessToken);
        return contactAccess;
    }
}

public class BranchInfo extends MovableStorageContainer {
    public class Location extends MovableStorage {
        private AuthInfo authInfo;

        final static private String REMOTE_ID_KEY = "remoteId";

        private Location(IOStorageDir dir, String remoteId) throws IOException {
            super(dir);

            setRemoteId(remoteId);
        }

        public Location(IOStorageDir dir, String remoteId, AuthInfo authInfo) throws IOException {
            this(dir, remoteId);

            setAuthInfo(authInfo);
        }

        public BranchInfo getBranchInfo() {
            return BranchInfo.this;
        }

        public void setRemoteId(String remoteId) throws IOException {
            storageDir.writeString(REMOTE_ID_KEY, remoteId);
        }

        public String getRemoteId() throws IOException {
            return storageDir.readString(REMOTE_ID_KEY);
        }

        public void setAuthInfo(AuthInfo authInfo) throws IOException {
            authInfo.write(storageDir);
            this.authInfo = authInfo;
        }

        public Remote getRemote() throws IOException {
            return remoteList.get(getRemoteId());
        }

        public AuthInfo getAuthInfo(FejoaContext context) throws IOException, CryptoException {
            if (authInfo != null)
                return authInfo;
            authInfo = AuthInfo.read(storageDir, getRemote(), context);
            return authInfo;
        }
    }

    static public class CryptoInfo {
        private HashValue encKey;
        private String keyStoreId = "";
        private boolean signBranch = false;

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
    final static private String LOCATIONS_KEY = "locations";
    final static private String CONTACT_ACCESS_KEY = "contactAccess";

    final private String branch;
    private String description = "";
    private CryptoInfo cryptoInfo = new CryptoInfo();
    final private MovableStorageList<Location> locations;
    final private RemoteList remoteList;
    final private ContactAccessList contactAccessList;

    private void load() throws IOException {
        description = storageDir.readString(DESCRIPTION_KEY);
        cryptoInfo.read(storageDir);
    }

    public BranchInfo(IOStorageDir dir, RemoteList remoteList, String branch) throws IOException, CryptoException {
        super(dir);

        this.branch = branch;
        this.remoteList = remoteList;
        locations = new MovableStorageList<Location>(this, LOCATIONS_KEY) {
            @Override
            protected Location createObject(IOStorageDir storageDir, String id) throws IOException, CryptoException {
                return new Location(storageDir, id);
            }
        };
        contactAccessList = new ContactAccessList(this, CONTACT_ACCESS_KEY);

        try {
            load();
        } catch (IOException e) {
        }
    }

    public BranchInfo(RemoteList remoteList, String branch, String description)
            throws IOException, CryptoException {
        this(null, remoteList, branch);

        setDescription(description);
    }

    public Collection<Location> getLocationEntries() {
        return locations.getEntries();
    }

    public Location addLocation(String remoteId, AuthInfo authInfo) throws IOException, CryptoException {
        Location location = new Location(null, remoteId, authInfo);
        locations.add(remoteId, location);
        return location;
    }

    public MovableStorageList<Location> getLocations() {
        return locations;
    }

    public ContactAccessList getContactAccessList() {
        return contactAccessList;
    }

    public void setCryptoInfo(HashValue encKey, KeyStore keyStore, boolean sign) throws IOException, CryptoException {
        cryptoInfo.encKey = encKey;
        if (keyStore != null)
            cryptoInfo.keyStoreId = keyStore.getId();
        cryptoInfo.signBranch = sign;
        setCryptoInfo(cryptoInfo);
    }

    public String getBranch() {
        return branch;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) throws IOException {
        this.description = description;
        storageDir.writeString(DESCRIPTION_KEY, description);
    }

    public void setCryptoInfo(CryptoInfo cryptoInfo) throws IOException {
        this.cryptoInfo = cryptoInfo;
        cryptoInfo.write(storageDir);
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
}