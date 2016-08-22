/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library;

import org.fejoa.library.crypto.*;
import org.fejoa.library.database.StorageDir;

import java.io.IOException;
import java.security.PublicKey;


abstract class Contact<SignKey, EncKey> implements IContactPublic {
    final static private String SIGNATURE_KEYS_DIR = "signatureKeys";
    final static private String ENCRYPTION_KEYS_DIR = "encryptionKeys";

    final protected FejoaContext context;
    final protected StorageDirList.IEntryIO<SignKey> signEntryIO;
    final protected StorageDirList.IEntryIO<EncKey> encEntryIO;
    protected StorageDir storageDir;

    protected String id = "";

    protected StorageDirList<SignKey> signatureKeys;
    protected StorageDirList<EncKey> encryptionKeys;

    protected Contact(FejoaContext context, StorageDirList.IEntryIO<SignKey> signEntryIO,
                      StorageDirList.IEntryIO<EncKey> encEntryIO, StorageDir dir) {
        this.context = context;
        this.signEntryIO = signEntryIO;
        this.encEntryIO = encEntryIO;

        if (dir != null)
            setStorageDir(dir);
    }

    protected void setStorageDir(StorageDir dir) {
        this.storageDir = dir;

        try {
            id = storageDir.readString(Constants.ID_KEY);
        } catch (IOException e) {
            //e.printStackTrace();
        }

        signatureKeys = new StorageDirList<>(new StorageDir(dir, SIGNATURE_KEYS_DIR), signEntryIO);
        encryptionKeys = new StorageDirList<>(new StorageDir(dir, ENCRYPTION_KEYS_DIR), encEntryIO);
    }

    abstract public PublicKey getVerificationKey(KeyId keyId);

    @Override
    public boolean verify(KeyId keyId, byte[] data, byte[] signature, CryptoSettings.Signature signatureSettings)
            throws CryptoException {
        ICryptoInterface crypto = context.getCrypto();
        PublicKey publicKey = getVerificationKey(keyId);
        return crypto.verifySignature(data, signature, publicKey, signatureSettings);
    }

    public void setId(String id) throws IOException {
        this.id = id;
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

    public void addSignatureKey(SignKey key) throws IOException {
        signatureKeys.add(key);
    }

    public SignKey getSignatureKey(KeyId id) {
        return signatureKeys.get(id.toString());
    }

    public void addEncryptionKey(EncKey key) throws IOException {
        encryptionKeys.add(key);
    }

    public EncKey getEncryptionKey(KeyId id) {
        return getEncryptionKey(id.toString());
    }

    public EncKey getEncryptionKey(String id) {
        return encryptionKeys.get(id);
    }
}


