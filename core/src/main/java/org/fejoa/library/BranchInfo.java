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
    public ContactAccessList(MovableStorageContainer parent, String subDir) {
        super(parent, subDir);
    }

    @Override
    protected ContactAccess readObject(IOStorageDir storageDir, String id) throws IOException, CryptoException {
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
            assert remoteList != null;
            return remoteList.get(getRemoteId());
        }

        public AuthInfo getAuthInfo(FejoaContext context) throws IOException, CryptoException {
            if (authInfo != null)
                return authInfo;
            authInfo = AuthInfo.read(storageDir, getRemote(), context);
            return authInfo;
        }
    }

    static public class CryptoKeyRef {
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
            encKey = HashValue.fromHex(dir.readString(ENCRYPTION_KEY));
            keyStoreId = dir.readString(Constants.KEY_STORE_ID);
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
    }

    final static private String DESCRIPTION_KEY = "description";
    final static private String ENCRYPTION_KEY = "encKey";
    final static private String LOCATIONS_KEY = "locations";
    final static private String CONTACT_ACCESS_KEY = "contactAccess";

    final private String branch;
    private String description = "";
    final private MovableStorageList<Location> locations;
    private RemoteList remoteList;
    final private ContactAccessList contactAccessList;

    private void load() throws IOException {
        description = storageDir.readString(DESCRIPTION_KEY);
    }

    static public BranchInfo open(IOStorageDir dir, String branch) throws IOException, CryptoException {
        BranchInfo branchInfo = new BranchInfo(dir, branch);
        branchInfo.load();
        return branchInfo;
    }

    static public BranchInfo create(String branch, String description) throws IOException {
        return new BranchInfo(branch, description);
    }

    private BranchInfo(IOStorageDir dir, String branch)  {
        super(dir);

        this.branch = branch;
        locations = new MovableStorageList<Location>(this, LOCATIONS_KEY) {
            @Override
            protected Location readObject(IOStorageDir storageDir, String id) throws IOException, CryptoException {
                return new Location(storageDir, id);
            }
        };
        contactAccessList = new ContactAccessList(this, CONTACT_ACCESS_KEY);
    }

    private BranchInfo(String branch, String description)
            throws IOException {
        this((IOStorageDir)null, branch);

        setDescription(description);
    }

    public void setRemoteList(RemoteList remoteList) {
        this.remoteList = remoteList;
    }

    public Collection<Location> getLocationEntries() throws IOException, CryptoException {
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
        CryptoKeyRef cryptoKeyRef = new CryptoKeyRef();
        cryptoKeyRef.encKey = encKey;
        if (keyStore != null)
            cryptoKeyRef.keyStoreId = keyStore.getId();
        cryptoKeyRef.signBranch = sign;
        setCryptoKeyRef(cryptoKeyRef);
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

    public void setCryptoKeyRef(CryptoKeyRef cryptoKeyRef) throws IOException {
        cryptoKeyRef.write(storageDir);
    }

    public CryptoKeyRef getCryptoKeyRef() throws IOException {
        CryptoKeyRef keyRef = new CryptoKeyRef();
        keyRef.read(storageDir);
        return keyRef;
    }

    public void setCryptoKey(SymmetricKeyData keyData) throws IOException, CryptoException {
        keyData.write(storageDir);
    }

    public SymmetricKeyData getCryptoKey() {
        try {
            return SymmetricKeyData.open(storageDir);
        } catch (Exception e) {
            return null;
        }
    }
}