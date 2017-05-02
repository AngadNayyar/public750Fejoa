/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library;

import org.fejoa.chunkstore.HashValue;
import org.fejoa.library.command.OutgoingCommandQueue;
import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.crypto.CryptoHelper;
import org.fejoa.library.crypto.CryptoSettings;
import org.fejoa.library.database.DefaultCommitSignature;
import org.fejoa.library.database.ICommitSignature;
import org.fejoa.library.database.StorageDir;
import org.fejoa.library.command.IncomingCommandQueue;
import org.json.JSONException;

import java.io.File;
import java.io.IOException;


public class UserData extends StorageDirObject {
    static private String BRANCHES_PATH = "branches";
    static private String MYSELF_PATH = "myself";
    static private String CONTACT_PATH = "contacts";
    static private String CONFIG_PATH = "config";
    static private String REMOTES_PATH = "remotes";

    final static private String ACCESS_STORE_PATH = "accessStore";
    final static private String IN_QUEUE_PATH = "inQueue";
    final static private String OUT_QUEUE_PATH = "outQueue";

    final static private String GATEWAY_PATH = "gateway";

    final static public String USER_DATA_CONTEXT = "userdata";

    final private KeyStore keyStore;
    final private BranchList branchList;
    final private ContactPrivate myself;
    final private ContactStore contactStore;
    final private ConfigStore configStore;
    final private RemoteList remoteStore;

    protected UserData(FejoaContext context, StorageDir storageDir, KeyStore keyStore)
            throws IOException, CryptoException {
        super(context, storageDir);

        this.keyStore = keyStore;

        remoteStore = new RemoteList(new StorageDir(storageDir, REMOTES_PATH));

        branchList = new BranchList(new StorageDir(storageDir, BRANCHES_PATH), remoteStore);
        if (findBranchInfo(keyStore.getStorageDir().getBranch(), USER_DATA_CONTEXT) == null)
            branchList.add(BranchInfo.create(keyStore.getStorageDir().getBranch(), "KeyStore", USER_DATA_CONTEXT));

        myself = new ContactPrivate(context, new StorageDir(storageDir, MYSELF_PATH), remoteStore, branchList);
        contactStore = new ContactStore(context, new StorageDir(storageDir, CONTACT_PATH));
        configStore = new ConfigStore(context, new StorageDir(storageDir, CONFIG_PATH), this);
    }

    public void commit(boolean all) throws IOException {
        if (all) {
            keyStore.commit();
        }
        storageDir.commit();
    }

    public FejoaContext getContext() {
        return context;
    }

    public ContactPrivate getMyself() {
        return myself;
    }

    public KeyStore getKeyStore() {
        return keyStore;
    }

    public void addBranch(BranchInfo branchEntry) throws IOException, CryptoException {
        branchList.add(branchEntry);
    }

    public BranchList getBranchList() {
        return branchList;
    }

    public BranchInfo findBranchInfo(String branch, String context) throws IOException, CryptoException {
        for (BranchInfo branchInfo : branchList.getEntries(context)) {
            if (branchInfo.getBranch().equals(branch))
                return branchInfo;
        }
        return null;
    }

    public ContactStore getContactStore() {
        return contactStore;
    }

    public ConfigStore getConfigStore() {
        return configStore;
    }

    public RemoteList getRemoteStore() {
        return remoteStore;
    }

    static public UserData create(FejoaContext context, String password)
            throws IOException, CryptoException {

        CryptoSettings.Signature signatureSettings = context.getCryptoSettings().signature;
        SigningKeyPair signingKeyPair = SigningKeyPair.create(context.getCrypto(), signatureSettings);

        KeyStore keyStore = KeyStore.create(context, signingKeyPair, password);

        String branch = CryptoHelper.sha1HashHex(context.getCrypto().generateSalt());
        SymmetricKeyData userDataKeyData = SymmetricKeyData.create(context, context.getCryptoSettings().symmetric);
        StorageDir userDataDir = context.getStorage(branch, userDataKeyData,
                new DefaultCommitSignature(context, signingKeyPair));

        UserData userData = new UserData(context, userDataDir, keyStore);
        userData.addBranch(BranchInfo.create(userData.getBranch(), "User Data (this)", USER_DATA_CONTEXT));
        keyStore.setUserData(userData);
        keyStore.addSymmetricKey(userDataDir.getBranch(), userDataKeyData, USER_DATA_CONTEXT);

        userData.myself.addSignatureKey(signingKeyPair);
        userData.myself.setId(signingKeyPair.getKeyId().getKeyId());
        userData.myself.getSignatureKeys().setDefault(signingKeyPair.getId());

        EncryptionKeyPair encryptionKeyPair = EncryptionKeyPair.create(context.getCrypto(),
                context.getCryptoSettings().publicKey);
        userData.myself.addEncryptionKey(encryptionKeyPair);
        userData.myself.getEncryptionKeys().setDefault(encryptionKeyPair.getId());

        // access control
        StorageDir accessControlBranch = context.getStorage(
                CryptoHelper.sha1HashHex(context.getCrypto().generateSalt()), null, null);
        AccessStore accessStore = new AccessStore(context, accessControlBranch);
        userData.addBranch(BranchInfo.create(accessStore.getStorageDir().getBranch(), "Access Store",
                USER_DATA_CONTEXT));
        userData.getStorageDir().writeString(ACCESS_STORE_PATH, accessStore.getStorageDir().getBranch());
        accessStore.commit();

        // in queue
        StorageDir inQueueBranch = context.getStorage(
                CryptoHelper.sha1HashHex(context.getCrypto().generateSalt()), null, null);
        IncomingCommandQueue incomingCommandQueue = new IncomingCommandQueue(inQueueBranch);
        userData.addBranch(BranchInfo.create(incomingCommandQueue.getStorageDir().getBranch(), "In Queue",
                USER_DATA_CONTEXT));
        userData.getStorageDir().writeString(IN_QUEUE_PATH, incomingCommandQueue.getStorageDir().getBranch());
        incomingCommandQueue.commit();

        // out queue
        StorageDir outQueueBranch = context.getStorage(
                CryptoHelper.sha1HashHex(context.getCrypto().generateSalt()), null, null);
        OutgoingCommandQueue outgoingCommandQueue = new OutgoingCommandQueue(outQueueBranch);
        userData.addBranch(BranchInfo.create(outgoingCommandQueue.getStorageDir().getBranch(), "Out Queue",
                USER_DATA_CONTEXT));
        userData.getStorageDir().writeString(OUT_QUEUE_PATH, outgoingCommandQueue.getStorageDir().getBranch());
        outgoingCommandQueue.commit();

        return userData;
    }

    static public UserData open(FejoaContext context, UserDataSettings settings, String password)
            throws JSONException, CryptoException, IOException {
        KeyStore keyStore = KeyStore.open(context, settings.keyStoreSettings, password);
        String userDataBranch = keyStore.getUserDataBranch();
        SymmetricKeyData userDataKeyData = keyStore.getSymmetricKey(userDataBranch, USER_DATA_CONTEXT);

        StorageDir userDataDir = context.getStorage(userDataBranch, userDataKeyData, null);
        UserData userData = new UserData(context, userDataDir, keyStore);

        // set the commit signature
        SigningKeyPair signingKeyPair = userData.myself.getSignatureKeys().getDefault();
        ICommitSignature commitSignature = new DefaultCommitSignature(context, signingKeyPair);
        userData.getStorageDir().setCommitSignature(commitSignature);
        keyStore.getStorageDir().setCommitSignature(commitSignature);

        return userData;
    }

    public UserDataSettings getSettings() throws IOException, CryptoException {
        return new UserDataSettings(keyStore.getConfig(), getAccessStore().getBranch(),
                getIncomingCommandQueue().getId(), getOutgoingCommandQueue().getId());
    }

    public String getId() {
        return getBranch();
    }

    public SymmetricKeyData getKeyData(BranchInfo branchInfo) throws CryptoException, IOException {
        SymmetricKeyData symmetricKeyData = null;
        BranchInfo.CryptoKeyRef keyRef = null;
        try {
            keyRef = branchInfo.getCryptoKeyRef();
        } catch (IOException e) {
            // try to read the key directly
            return branchInfo.getCryptoKey();
        }

        HashValue keyId = keyRef.getKeyId();
        if (keyId != null && !keyId.isZero()) {
            if (!keyStore.getId().equals(keyRef.getKeyStoreId()))
                throw new CryptoException("Unknown keystore.");
            symmetricKeyData = keyStore.getSymmetricKey(keyId.toHex(), branchInfo.getStorageContext());
        }
        return symmetricKeyData;
    }

    public BranchInfo createNewEncryptedStorage(String storageContext, String description)
            throws IOException, CryptoException {
        String branch = CryptoHelper.sha1HashHex(context.getCrypto().generateSalt());
        SymmetricKeyData keyData = SymmetricKeyData.create(context, context.getCryptoSettings().symmetric);
        keyStore.addSymmetricKey(keyData.keyId().toHex(), keyData, storageContext);
        SigningKeyPair signingKeyPair = getMyself().getSignatureKeys().getDefault();
        StorageDir storageDir = context.getStorage(branch, keyData, new DefaultCommitSignature(context, signingKeyPair));
        BranchInfo branchInfo = BranchInfo.create(storageDir.getBranch(), description, storageContext);
        branchInfo.setCryptoInfo(keyData.keyId(), keyStore, true);
        // add branch info
        addBranch(branchInfo);
        return branchInfo;
    }

    /**
     * Get myself or the contact with the given id.
     */
    public Contact getContact(String contactId) {
        if (getMyself().getId().equals(contactId))
            return getMyself();

        return getContactStore().getContactList().get(contactId);
    }

    /**
     * Get a BranchInfo for a certain context and owner.
     *
     * @param owner either myself of a contact
     */
    public BranchInfo getBranchInfo(String branch, String branchContext, String owner)
            throws IOException, CryptoException {
        BranchList branchList = getContact(owner).getBranchList();
        return branchList.get(branch, branchContext);
    }

    public StorageDir getStorageDir(BranchInfo branchInfo) throws IOException, CryptoException {
        return getStorageDir(branchInfo, null);
    }

    /**
     * Get a StorageDir for a BranchInfo from the default repository.
     *
     * @param rev can be null to get the tip
     */
    public StorageDir getStorageDir(BranchInfo branchInfo, HashValue rev) throws IOException, CryptoException {
        return getStorageDir(null, branchInfo, rev);
    }

    /**
     * Get a StorageDir for a BranchInfo from the repository located at repoPath.
     */
    public StorageDir getStorageDir(File repoPath, BranchInfo branchInfo, HashValue rev)
            throws IOException, CryptoException {
        SymmetricKeyData symmetricKeyData = getKeyData(branchInfo);

        SigningKeyPair keyPair = getMyself().getSignatureKeys().getDefault();
        ICommitSignature commitSignature = new DefaultCommitSignature(context, keyPair);

        if (repoPath != null)
            return context.getStorage(repoPath, branchInfo.getBranch(), rev, symmetricKeyData, commitSignature);
        return context.getStorage(branchInfo.getBranch(), rev, symmetricKeyData, commitSignature);
    }

    public IncomingCommandQueue getIncomingCommandQueue() throws IOException, CryptoException {
        String id = storageDir.readString(IN_QUEUE_PATH);
        BranchInfo branchInfo = getBranchList().get(id, USER_DATA_CONTEXT);
        try {
            return new IncomingCommandQueue(getStorageDir(branchInfo));
        } catch (CryptoException e) {
            e.printStackTrace();
            // incoming queue is not encrypted
            throw new RuntimeException(e);
        }
    }

    public OutgoingCommandQueue getOutgoingCommandQueue() throws IOException, CryptoException {
        String id = storageDir.readString(OUT_QUEUE_PATH);
        BranchInfo branchInfo = getBranchList().get(id, USER_DATA_CONTEXT);
        try {
            return new OutgoingCommandQueue(getStorageDir(branchInfo));
        } catch (CryptoException e) {
            e.printStackTrace();
            // outgoing queue is not encrypted
            throw new RuntimeException(e);
        }
    }

    public AccessStore getAccessStore() throws IOException, CryptoException {
        String id = storageDir.readString(ACCESS_STORE_PATH);
        BranchInfo branchInfo = getBranchList().get(id, USER_DATA_CONTEXT);
        try {
            return new AccessStore(context, getStorageDir(branchInfo));
        } catch (CryptoException e) {
            e.printStackTrace();
            // access store is not encrypted
            throw new RuntimeException(e);
        }
    }

    public void setGateway(Remote remote) throws IOException {
        storageDir.writeString(GATEWAY_PATH, remote.getId());
    }

    public Remote getGateway() throws IOException {
        String id = storageDir.readString(GATEWAY_PATH);
        return getRemoteStore().get(id);
    }

    public ICommitSignature getCommitSignature() {
        SigningKeyPair keyPair = getMyself().getSignatureKeys().getDefault();
        if (keyPair == null)
            return null;
        return new DefaultCommitSignature(context, keyPair);
    }
}

