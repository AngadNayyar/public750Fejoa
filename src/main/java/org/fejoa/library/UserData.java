/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library;

import org.fejoa.chunkstore.HashValue;
import org.fejoa.library.command.IncomingCommandQueue;
import org.fejoa.library.command.OutgoingCommandQueue;
import org.fejoa.library.crypto.*;
import org.fejoa.library.database.DefaultCommitSignature;
import org.fejoa.library.database.ICommitSignature;
import org.fejoa.library.database.StorageDir;
import org.json.JSONException;

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

    final private KeyStore keyStore;
    final private BranchList branchList;
    final private ContactPrivate myself;
    final private ContactStore contactStore;
    final private ConfigStore configStore;
    final private RemoteList remoteStore;

    protected UserData(FejoaContext context, StorageDir storageDir, KeyStore keyStore)
            throws IOException {
        super(context, storageDir);

        this.keyStore = keyStore;

        branchList = new BranchList(new StorageDir(storageDir, BRANCHES_PATH));
        branchList.add(new BranchInfo(keyStore.getStorageDir().getBranch()));

        myself = new ContactPrivate(context, new StorageDir(storageDir, MYSELF_PATH));
        contactStore = new ContactStore(context, new StorageDir(storageDir, CONTACT_PATH));
        configStore = new ConfigStore(context, new StorageDir(storageDir, CONFIG_PATH));
        remoteStore = new RemoteList(new StorageDir(storageDir, REMOTES_PATH));
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

    public StorageDir getBranch(String branch, SigningKeyPair signingKeyPair) throws IOException, CryptoException {
        BranchInfo branchEntry = branchList.get(branch);
        String branchId = branchEntry.getKeyId().toHex();
        SymmetricKeyData keyData = keyStore.getSymmetricKey(branchId);
        ICommitSignature commitSignature = new DefaultCommitSignature(context, signingKeyPair);
        return context.getStorage(branchId, keyData, commitSignature);
    }

    public void addBranch(BranchInfo branchEntry) throws IOException {
        branchList.add(branchEntry);
    }

    public BranchList getBranchList() {
        return branchList;
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

        String branch = CryptoHelper.sha1HashHex(context.getCrypto().generateInitializationVector(32));
        SymmetricKeyData userDataKeyData = SymmetricKeyData.create(context, context.getCryptoSettings().symmetric);
        StorageDir userDataDir = context.getStorage(branch, userDataKeyData,
                new DefaultCommitSignature(context, signingKeyPair));

        UserData userData = new UserData(context, userDataDir, keyStore);
        userData.addBranch(new BranchInfo(userData.getBranch(), "User Data (this)", null, null, false));
        keyStore.setUserData(userData);
        keyStore.addSymmetricKey(userDataDir.getBranch(), userDataKeyData);

        userData.myself.addSignatureKey(signingKeyPair);
        userData.myself.setId(signingKeyPair.getKeyId().getKeyId());
        userData.myself.getSignatureKeys().setDefault(signingKeyPair.getId());

        EncryptionKeyPair encryptionKeyPair = EncryptionKeyPair.create(context.getCrypto(),
                context.getCryptoSettings().publicKey);
        userData.myself.addEncryptionKey(encryptionKeyPair);
        userData.myself.getEncryptionKeys().setDefault(encryptionKeyPair.getId());

        // access control
        StorageDir accessControlBranch = context.getStorage(
                CryptoHelper.sha1HashHex(context.getCrypto().generateInitializationVector(32)), null, null);
        AccessStore accessStore = new AccessStore(context, accessControlBranch);
        userData.addBranch(new BranchInfo(accessStore.getStorageDir().getBranch(), "Access Store", null, null, false));
        userData.getStorageDir().writeString(ACCESS_STORE_PATH, accessStore.getStorageDir().getBranch());

        // in queue
        StorageDir inQueueBranch = context.getStorage(
                CryptoHelper.sha1HashHex(context.getCrypto().generateInitializationVector(32)), null, null);
        IncomingCommandQueue incomingCommandQueue = new IncomingCommandQueue(inQueueBranch);
        userData.addBranch(new BranchInfo(incomingCommandQueue.getStorageDir().getBranch(), "In Queue", null, null, false));
        userData.getStorageDir().writeString(IN_QUEUE_PATH, incomingCommandQueue.getStorageDir().getBranch());

        // out queue
        StorageDir outQueueBranch = context.getStorage(
                CryptoHelper.sha1HashHex(context.getCrypto().generateInitializationVector(32)), null, null);
        OutgoingCommandQueue outgoingCommandQueue = new OutgoingCommandQueue(outQueueBranch);
        userData.addBranch(new BranchInfo(outgoingCommandQueue.getStorageDir().getBranch(), "Out Queue", null, null, false));
        userData.getStorageDir().writeString(OUT_QUEUE_PATH, outgoingCommandQueue.getStorageDir().getBranch());

        return userData;
    }

    static public UserData open(FejoaContext context, UserDataSettings settings, String password)
            throws JSONException, CryptoException, IOException {
        KeyStore keyStore = KeyStore.open(context, settings.keyStoreSettings, password);
        String userDataBranch = keyStore.getUserDataBranch();
        SymmetricKeyData userDataKeyData = keyStore.getSymmetricKey(userDataBranch);

        StorageDir userDataDir = context.getStorage(userDataBranch, userDataKeyData, null);
        UserData userData = new UserData(context, userDataDir, keyStore);

        // set the commit signature
        SigningKeyPair signingKeyPair = userData.myself.getSignatureKeys().getDefault();
        ICommitSignature commitSignature = new DefaultCommitSignature(context, signingKeyPair);
        userData.getStorageDir().setCommitSignature(commitSignature);
        keyStore.getStorageDir().setCommitSignature(commitSignature);

        return userData;
    }

    public UserDataSettings getSettings() throws IOException {
        return new UserDataSettings(keyStore.getConfig(), getAccessStore().getBranch(),
                getIncomingCommandQueue().getId(), getOutgoingCommandQueue().getId());
    }

    public String getId() {
        return getBranch();
    }

    public SymmetricKeyData getKeyData(BranchInfo branchInfo) throws CryptoException, IOException {
        SymmetricKeyData symmetricKeyData = null;
        HashValue keyId = branchInfo.getKeyId();
        if (keyId != null && !keyId.isZero()) {
            if (!keyStore.getId().equals(branchInfo.getKeyStoreId()))
                throw new CryptoException("Unknown keystore.");
            symmetricKeyData = keyStore.getSymmetricKey(keyId.toHex());
        }
        return symmetricKeyData;
    }

    public StorageDir getStorageDir(BranchInfo branchInfo) throws IOException, CryptoException {
        SymmetricKeyData symmetricKeyData = getKeyData(branchInfo);
        ICommitSignature commitSignature = null;
        if (branchInfo.signBranch()) {
            SigningKeyPair keyPair = getMyself().getSignatureKeys().getDefault();
            commitSignature = new DefaultCommitSignature(context, keyPair);
        }
        return context.getStorage(branchInfo.getBranch(), symmetricKeyData, commitSignature);
    }

    public IncomingCommandQueue getIncomingCommandQueue() throws IOException {
        String id = storageDir.readString(IN_QUEUE_PATH);
        BranchInfo branchInfo = getBranchList().get(id);
        try {
            return new IncomingCommandQueue(getStorageDir(branchInfo));
        } catch (CryptoException e) {
            e.printStackTrace();
            // incoming queue is not encrypted
            throw new RuntimeException(e);
        }
    }

    public OutgoingCommandQueue getOutgoingCommandQueue() throws IOException {
        String id = storageDir.readString(OUT_QUEUE_PATH);
        BranchInfo branchInfo = getBranchList().get(id);
        try {
            return new OutgoingCommandQueue(getStorageDir(branchInfo));
        } catch (CryptoException e) {
            e.printStackTrace();
            // outgoing queue is not encrypted
            throw new RuntimeException(e);
        }
    }

    public AccessStore getAccessStore() throws IOException {
        String id = storageDir.readString(ACCESS_STORE_PATH);
        BranchInfo branchInfo = getBranchList().get(id);
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

