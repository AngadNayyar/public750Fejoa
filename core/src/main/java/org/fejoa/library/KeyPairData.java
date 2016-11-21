/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library;

import org.fejoa.library.database.IOStorageDir;
import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.crypto.CryptoHelper;
import org.fejoa.library.crypto.CryptoSettings;
import org.fejoa.library.crypto.CryptoSettingsIO;

import java.io.IOException;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;


public class KeyPairData implements IStorageDirBundle {
    final private String PATH_PRIVATE_KEY = "privateKey";
    final private String PATH_PUBLIC_KEY = "publicKey";

    private String id;
    private KeyPair keyPair;
    final protected CryptoSettings.KeyTypeSettings settings;

    protected KeyPairData(CryptoSettings.KeyTypeSettings settings) {
        this.settings = settings;
    }

    public KeyPairData(KeyPair keyPair, CryptoSettings.KeyTypeSettings settings) {
        this.id = CryptoHelper.sha1HashHex(keyPair.getPublic().getEncoded());
        this.keyPair = keyPair;
        this.settings = settings;
    }

    @Override
    public void write(IOStorageDir dir) throws IOException, CryptoException {
        dir.writeString(Constants.ID_KEY, id);
        dir.putBytes(PATH_PRIVATE_KEY, keyPair.getPrivate().getEncoded());
        dir.putBytes(PATH_PUBLIC_KEY, keyPair.getPublic().getEncoded());
        writeSettings(dir);
    }

    protected void writeSettings(IOStorageDir dir) throws IOException {
        CryptoSettingsIO.write(settings, dir, "");
    }

    protected void readSettings(IOStorageDir dir) throws IOException {
        CryptoSettingsIO.read(settings, dir, "");
    }

    @Override
    public void read(IOStorageDir dir) throws IOException {
        id = dir.readString(Constants.ID_KEY);
        readSettings(dir);
        PrivateKey privateKey;
        PublicKey publicKey;
        try {
            privateKey = CryptoHelper.privateKeyFromRaw(dir.readBytes(PATH_PRIVATE_KEY), settings.keyType);
            publicKey = CryptoHelper.publicKeyFromRaw(dir.readBytes(PATH_PUBLIC_KEY), settings.keyType);
        } catch (Exception e) {
            throw new IOException(e.getMessage());
        }
        keyPair = new KeyPair(publicKey, privateKey);
    }

    public String getId() {
        return id;
    }

    public KeyId getKeyId() {
        return new KeyId(id);
    }

    public KeyPair getKeyPair() {
        return keyPair;
    }

    public CryptoSettings.KeyTypeSettings getKeyTypeSettings() {
        return settings;
    }
}
