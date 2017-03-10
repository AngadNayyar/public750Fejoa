/*
 * Copyright 2017.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.support;

import org.fejoa.library.Constants;
import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.crypto.CryptoHelper;
import org.json.JSONException;
import org.json.JSONObject;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;


/**
 * Helper to write and read which hash algorithm is used in a protocol.
 */
public class ProtocolHashHelper {
    static public final String HASH_SHA3_256 = "sha3-256";

    static public byte[] hash(byte[] data, JSONObject commandObject) throws JSONException, CryptoException {
        String algo = commandObject.getString(Constants.HASH_ALGO_KEY);
        return hash(data, algo);
    }

    static public MessageDigest getMessageDigest(String algo) throws CryptoException {
        try {
            switch (algo) {
                case HASH_SHA3_256:
                    return CryptoHelper.sha3_256Hash();
            }
        } catch (NoSuchAlgorithmException e) {
            throw new CryptoException(e);
        }
        throw new CryptoException("No such hash algorithm: " + algo);
    }

    static public byte[] hash(byte[] data, String algo) throws CryptoException {
        return CryptoHelper.hash(data, getMessageDigest(algo));
    }

    static public String hashHex(byte[] data, String algo) throws CryptoException {
        return CryptoHelper.toHex(hash(data, algo));
    }

    static public String hashHex(String data, JSONObject commandObject) throws JSONException, CryptoException {
        return CryptoHelper.toHex(hash(data.getBytes(), commandObject));
    }

    static public String hashHex(String data, String algo) throws CryptoException {
        return hashHex(data.getBytes(), algo);
    }
}
