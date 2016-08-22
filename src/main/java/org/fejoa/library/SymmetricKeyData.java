/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library;

import org.fejoa.chunkstore.HashValue;
import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.crypto.CryptoHelper;
import org.fejoa.library.crypto.CryptoSettings;
import org.fejoa.library.crypto.CryptoSettingsIO;
import org.fejoa.library.database.StorageDir;

import javax.crypto.SecretKey;
import java.io.IOException;


public class SymmetricKeyData implements IStorageDirBundle {
    final private String PATH_SYMMETRIC_KEY = "key";
    final private String PATH_SYMMETRIC_IV = "iV";

    public SecretKey key;
    public byte iv[];
    public CryptoSettings.Symmetric settings;

    public SymmetricKeyData() {

    }

    private SymmetricKeyData(FejoaContext context, CryptoSettings.Symmetric settings) throws CryptoException {
        this.settings = settings;
        key = context.getCrypto().generateSymmetricKey(settings);
        iv = context.getCrypto().generateInitializationVector(settings.ivSize);
    }

    private SymmetricKeyData(StorageDir dir) throws IOException {
        this.settings = new CryptoSettings.Symmetric();
        read(dir);
    }

    static public SymmetricKeyData create(FejoaContext context, CryptoSettings.Symmetric settings) throws CryptoException {
        return new SymmetricKeyData(context, settings);
    }

    static public SymmetricKeyData open(StorageDir dir) throws CryptoException, IOException {
        return new SymmetricKeyData(dir);
    }

    public HashValue keyId() {
        return new HashValue(CryptoHelper.sha1Hash(key.getEncoded()));
    }

    @Override
    public void write(StorageDir dir) throws IOException {
        dir.writeBytes(PATH_SYMMETRIC_KEY, key.getEncoded());
        dir.writeBytes(PATH_SYMMETRIC_IV, iv);

        CryptoSettingsIO.write(settings, dir);
    }

    @Override
    public void read(StorageDir dir) throws IOException {
        settings = new CryptoSettings.Symmetric();
        CryptoSettingsIO.read(settings, dir);

        key = CryptoHelper.symmetricKeyFromRaw(dir.readBytes(PATH_SYMMETRIC_KEY), settings);
        iv = dir.readBytes(PATH_SYMMETRIC_IV);
    }
}
