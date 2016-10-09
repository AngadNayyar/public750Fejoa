/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library;

import org.fejoa.library.crypto.CryptoSettings;
import org.fejoa.library.database.IOStorageDir;
import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.crypto.CryptoSettingsIO;
import org.fejoa.library.crypto.ICryptoInterface;

import java.io.IOException;
import java.security.KeyPair;


public class SigningKeyPair extends KeyPairData {
    private SigningKeyPair() {
        super(new CryptoSettings.Signature());
    }

    public SigningKeyPair(KeyPair keyPair, CryptoSettings.Signature signatureSettings) {
        super(keyPair, signatureSettings);
    }

    static public SigningKeyPair create(ICryptoInterface cryptoInterface, CryptoSettings.Signature signatureSettings)
            throws CryptoException {
        return new SigningKeyPair(cryptoInterface.generateKeyPair(signatureSettings), signatureSettings);
    }

    static public SigningKeyPair open(IOStorageDir dir) throws IOException {
        SigningKeyPair signingKeyPair = new SigningKeyPair();
        signingKeyPair.read(dir);
        return signingKeyPair;
    }

    @Override
    protected void writeSettings(IOStorageDir dir) throws IOException {
        CryptoSettingsIO.write((CryptoSettings.Signature)settings, dir, "");
    }

    @Override
    protected void readSettings(IOStorageDir dir) throws IOException {
        CryptoSettingsIO.read((CryptoSettings.Signature)settings, dir, "");
    }

    public CryptoSettings.Signature getSignatureSettings() {
        return (CryptoSettings.Signature) getKeyTypeSettings();
    }
}
