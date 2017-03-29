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

import java.io.IOException;
import java.security.KeyPair;


public class EncryptionKeyPair extends KeyPairData {
    private EncryptionKeyPair() {
        super(new CryptoSettings.Asymmetric());
    }

    public EncryptionKeyPair(KeyPair keyPair, CryptoSettings.Asymmetric encSettings) {
        super(keyPair, encSettings);
    }

    static public EncryptionKeyPair create(ICryptoInterface cryptoInterface, CryptoSettings.Asymmetric encSettings)
            throws CryptoException {
        return new EncryptionKeyPair(cryptoInterface.generateKeyPair(encSettings), encSettings);
    }

    static public EncryptionKeyPair open(IOStorageDir dir) throws IOException {
        EncryptionKeyPair keyPair = new EncryptionKeyPair();
        keyPair.read(dir);
        return keyPair;
    }

    public CryptoSettings.Asymmetric getEncSettings() {
        return (CryptoSettings.Asymmetric) getKeyTypeSettings();
    }
}
