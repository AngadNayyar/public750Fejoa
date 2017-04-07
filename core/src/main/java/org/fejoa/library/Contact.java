/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library;

import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.crypto.CryptoSettings;
import org.fejoa.library.crypto.ICryptoInterface;
import org.fejoa.library.database.IOStorageDir;
import org.fejoa.library.database.MovableStorageContainer;

import java.io.IOException;
import java.security.PublicKey;


abstract public class Contact<SignKey, EncKey> extends MovableStorageContainer implements IContactPublic {
    final static private String SIGNATURE_KEYS_DIR = "signatureKeys";
    final static private String ENCRYPTION_KEYS_DIR = "encryptionKeys";

    final protected FejoaContext context;
    final protected StorageDirList.IEntryIO<SignKey> signEntryIO;
    final protected StorageDirList.IEntryIO<EncKey> encEntryIO;
    private PersonalDetailsManager personalDetailsManager;

    protected String id;

    protected StorageDirList<SignKey> signatureKeys;
    protected StorageDirList<EncKey> encryptionKeys;

    protected Contact(FejoaContext context, StorageDirList.IEntryIO<SignKey> signEntryIO,
                      StorageDirList.IEntryIO<EncKey> encEntryIO, IOStorageDir dir) {
        super(dir);
        this.context = context;
        this.signEntryIO = signEntryIO;
        this.encEntryIO = encEntryIO;
        this.signatureKeys = new StorageDirList<>(getSignatureKeysDir(), signEntryIO);
        this.encryptionKeys = new StorageDirList<>(getEncKeysDir(), encEntryIO);

        try {
            read(storageDir);
        } catch (IOException e) {
            //e.printStackTrace();
        }
    }

    @Override
    public void setStorageDir(IOStorageDir dir) throws IOException, CryptoException {
        super.setStorageDir(dir);

        signatureKeys.setStorageDir(getSignatureKeysDir());
        encryptionKeys.setStorageDir(getEncKeysDir());
    }

    private IOStorageDir getSignatureKeysDir() {
        return new IOStorageDir(storageDir, SIGNATURE_KEYS_DIR);
    }

    private IOStorageDir getEncKeysDir() {
        return new IOStorageDir(storageDir, ENCRYPTION_KEYS_DIR);
    }

    protected void read(IOStorageDir storageDir) throws IOException {
        id = storageDir.readString(Constants.ID_KEY);
    }

    abstract public PublicKey getVerificationKey(KeyId keyId);

    @Override
    public boolean verify(KeyId keyId, byte[] data, byte[] signature, CryptoSettings.Signature signatureSettings)
            throws CryptoException {
        ICryptoInterface crypto = context.getCrypto();
        PublicKey publicKey = getVerificationKey(keyId);
        if (publicKey == null)
            return false;
        return crypto.verifySignature(data, signature, publicKey, signatureSettings);
    }

    public void setId(String id) throws IOException {
        this.id = id;
        storageDir.writeString(Constants.ID_KEY, id);
    }

    public String getId() {
        return id;
    }

    public StorageDirList<SignKey> getSignatureKeys() {
        return signatureKeys;
    }

    public StorageDirList<EncKey> getEncryptionKeys() {
        return encryptionKeys;
    }

    public void addSignatureKey(SignKey key) throws IOException, CryptoException {
        signatureKeys.add(key);
    }

    public SignKey getSignatureKey(KeyId id) {
        return signatureKeys.get(id.toString());
    }

    public void addEncryptionKey(EncKey key) throws IOException, CryptoException {
        encryptionKeys.add(key);
    }

    public EncKey getEncryptionKey(KeyId id) {
        return getEncryptionKey(id.toString());
    }

    public EncKey getEncryptionKey(String id) {
        return encryptionKeys.get(id);
    }

    public PersonalDetailsManager getPersonalDetails(Client client) {
        if (personalDetailsManager != null)
            return personalDetailsManager;
        personalDetailsManager = new PersonalDetailsManager(client, this);
        personalDetailsManager.setTo(new IOStorageDir(storageDir, "details"));
        return personalDetailsManager;
    }
}


