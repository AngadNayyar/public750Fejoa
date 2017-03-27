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
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;


/**
 * Parameters to derive a user key (or sub key) from a base key.
 *
 * Idea: take the base key concat some salt. The hash of this data is the new user key.
 */
public class UserKeyParameters {
    final static public String KDF_PARAMETERS_KEY = "kdfParameters";
    final static public String SUBKEY_SALT_KEY = "userKeySalt";
    final static public String HASH_ALGO_KEY = "hashAlgo";

    final public KDFParameters kdfParameters;
    final public byte[] userKeySalt;
    final public String hashAlgo;

    public UserKeyParameters(KDFParameters kdfParameters, byte[] userKeySalt, String hashAlgo) {
        this.kdfParameters = kdfParameters;
        this.userKeySalt = userKeySalt;
        this.hashAlgo = hashAlgo;
    }

    public UserKeyParameters(JSONObject object) {
        this.kdfParameters = new KDFParameters(object.getJSONObject(KDF_PARAMETERS_KEY));
        this.userKeySalt = Base64.decodeBase64(object.getString(SUBKEY_SALT_KEY));
        this.hashAlgo = object.getString(HASH_ALGO_KEY);
    }

    public JSONObject toJson() {
        JSONObject object = new JSONObject();
        object.put(KDF_PARAMETERS_KEY, kdfParameters.toJson());
        object.put(SUBKEY_SALT_KEY, Base64.encodeBase64String(userKeySalt));
        object.put(HASH_ALGO_KEY, hashAlgo);
        return object;
    }

    public HashValue hash(MessageDigest messageDigest) {
        OutputStream outputStream = new DigestOutputStream(new OutputStream() {
            @Override
            public void write(int i) throws IOException {

            }
        }, messageDigest);

        try {
            outputStream.write(kdfParameters.hash(messageDigest).getBytes());
            outputStream.write(userKeySalt);
            outputStream.write(hashAlgo.getBytes());
            outputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return new HashValue(messageDigest.digest());
    }

    static private MessageDigest getMessageDigest(String algo) throws NoSuchAlgorithmException {
        switch (algo) {
            case CryptoSettings.SHA2:
                return CryptoHelper.sha256Hash();
            case CryptoSettings.SHA3_256:
                return CryptoHelper.sha3_256Hash();
            default:
                throw new NoSuchAlgorithmException();
        }
    }

    static public SecretKey deriveUserKey(SecretKey baseKey, UserKeyParameters settings)
            throws CryptoException {
        byte[] baseKeyBytes = baseKey.getEncoded();
        assert baseKeyBytes.length == 32;
        ByteArrayOutputStream combinedKey = new ByteArrayOutputStream();
        try {
            combinedKey.write(baseKeyBytes);
            combinedKey.write(settings.userKeySalt);
        } catch (IOException e) {
            e.printStackTrace();
            // should not happen
            assert false;
        }

        MessageDigest messageDigest;
        try {
            messageDigest = getMessageDigest(settings.hashAlgo);
        } catch (NoSuchAlgorithmException e) {
            throw new CryptoException(e);
        }
        byte[] out = CryptoHelper.hash(combinedKey.toByteArray(), messageDigest);
        return CryptoHelper.secretKey(out, baseKey.getAlgorithm());
    }

    static public SecretKey deriveUserKey(String password, ICryptoInterface crypto, UserKeyParameters settings)
            throws CryptoException {
        SecretKey baseKey = KDFParameters.deriveKey(password, crypto, settings.kdfParameters);
        return UserKeyParameters.deriveUserKey(baseKey, settings);
    }
}
