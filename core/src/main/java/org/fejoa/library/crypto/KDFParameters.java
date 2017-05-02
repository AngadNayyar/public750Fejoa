/*
 * Copyright 2017.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.crypto;

import org.apache.commons.codec.binary.Base64;
import org.fejoa.chunkstore.HashValue;
import org.json.JSONObject;

import javax.crypto.SecretKey;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;


/**
 * Parameter needed to derived a key from a password.
 */
public class KDFParameters {
    final static public String KDF_SALT_KEY = "kdfSalt";

    final public CryptoSettings.Password kdfSettings;
    final public byte[] kdfSalt;

    public KDFParameters(CryptoSettings.Password kdfSettings, byte[] kdfSalt) {
        this.kdfSettings = kdfSettings;
        this.kdfSalt = kdfSalt;
    }

    public KDFParameters(JSONObject object) {
        this.kdfSettings = JsonCryptoSettings.passwordFromJson(object);
        this.kdfSalt = Base64.decodeBase64(object.getString(KDF_SALT_KEY));
    }

    public JSONObject toJson() {
        JSONObject object = JsonCryptoSettings.toJson(kdfSettings);
        object.put(KDF_SALT_KEY, Base64.encodeBase64String(kdfSalt));
        return object;
    }

    static public SecretKey deriveKey(String password, ICryptoInterface crypto, KDFParameters parameters)
            throws CryptoException {
        CryptoSettings.Password kdfSettings = parameters.kdfSettings;
        return crypto.deriveKey(password, parameters.kdfSalt, kdfSettings.kdfAlgorithm,
                kdfSettings.kdfIterations, kdfSettings.passwordSize);
    }

    public HashValue hash(MessageDigest messageDigest) {
        OutputStream outputStream = new DigestOutputStream(new OutputStream() {
            @Override
            public void write(int i) throws IOException {

            }
        }, messageDigest);

        try {
            outputStream.write(kdfSettings.hash(messageDigest).getBytes());
            outputStream.write(kdfSalt);
            outputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return new HashValue(messageDigest.digest());
    }
}
