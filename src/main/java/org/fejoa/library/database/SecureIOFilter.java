/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.database;

import org.fejoa.library.KeyStoreOld;
import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.crypto.CryptoSettings;
import org.fejoa.library.crypto.ICryptoInterface;
import org.fejoa.library.KeyStore;

import java.io.IOException;


public class SecureIOFilter implements StorageDir.IIOFilter {
    final private KeyStoreOld.SymmetricKeyData symmetricKeyData;
    final private ICryptoInterface crypto;
    final private CryptoSettings.Symmetric cryptoSettings;

    public SecureIOFilter(ICryptoInterface crypto, KeyStoreOld.SymmetricKeyData symmetricKeyData,
                          CryptoSettings.Symmetric cryptoSettings) {
        this.crypto = crypto;
        this.symmetricKeyData = symmetricKeyData;
        this.cryptoSettings = cryptoSettings;
    }

    @Override
    public byte[] writeFilter(byte[] bytes) throws IOException {
        try {
            return crypto.decryptSymmetric(bytes, symmetricKeyData.key, symmetricKeyData.iv, cryptoSettings);
        } catch (CryptoException e) {
            throw new IOException(e.getMessage());
        }
    }

    @Override
    public byte[] readFilter(byte[] bytes) throws IOException {
        try {
            return crypto.decryptSymmetric(bytes, symmetricKeyData.key, symmetricKeyData.iv, cryptoSettings);
        } catch (CryptoException e) {
            throw new IOException(e.getMessage());
        }
    }
}
