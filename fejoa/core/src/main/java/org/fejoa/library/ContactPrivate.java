/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library;

import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.database.IOStorageDir;
import org.fejoa.library.database.StorageDir;

import java.io.IOException;
import java.security.PublicKey;


public class ContactPrivate extends Contact<SigningKeyPair, EncryptionKeyPair> implements IContactPrivate {
    final private RemoteList remoteList;
    final private BranchList branchList;

    protected ContactPrivate(FejoaContext context, StorageDir dir, RemoteList remoteList, BranchList branchList) {
        super(context, new StorageDirList.AbstractEntryIO<SigningKeyPair>() {
            @Override
            public String getId(SigningKeyPair entry) {
                return entry.getId();
            }

            @Override
            public SigningKeyPair read(IOStorageDir dir) throws IOException {
                return SigningKeyPair.open(dir);
            }
        }, new StorageDirList.AbstractEntryIO<EncryptionKeyPair>() {
            @Override
            public String getId(EncryptionKeyPair entry) {
                return entry.getId();
            }

            @Override
            public EncryptionKeyPair read(IOStorageDir dir) throws IOException {
                return EncryptionKeyPair.open(dir);
            }
        }, dir);

        this.remoteList = remoteList;
        this.branchList = branchList;
    }

    @Override
    public PublicKey getVerificationKey(KeyId keyId) {
        return signatureKeys.get(keyId.toString()).getKeyPair().getPublic();
    }

    @Override
    public byte[] sign(SigningKeyPair signingKeyPair, byte[] data) throws CryptoException {
        return sign(context, signingKeyPair, data);
    }

    static public byte[] sign(FejoaContext context, SigningKeyPair key, byte[] data) throws CryptoException {
        return context.getCrypto().sign(data, key.getKeyPair().getPrivate(), key.getSignatureSettings());
    }

    @Override
    public RemoteList getRemotes() {
        return remoteList;
    }

    @Override
    public BranchList getBranchList() {
        return branchList;
    }
}
