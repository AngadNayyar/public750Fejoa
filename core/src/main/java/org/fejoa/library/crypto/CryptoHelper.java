/*
 * Copyright 2014-2017.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.crypto;

import org.bouncycastle.jcajce.provider.digest.SHA3;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.bind.DatatypeConverter;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.List;


public class CryptoHelper {
    public static String toHex(byte[] bytes) {
        StringBuffer stringBuffer = new StringBuffer();
        for (int i = 0; i < bytes.length; i++)
            stringBuffer.append(String.format("%02X", bytes[i]));

        return stringBuffer.toString().toLowerCase();
    }

    // see:
    // http://stackoverflow.com/questions/140131/convert-a-string-representation-of-a-hex-dump-to-a-byte-array-using-java
    public static byte[] fromHex(String hex) {
        int length = hex.length();
        byte[] data = new byte[length / 2];
        for (int i = 0; i < length; i += 2)
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4) + Character.digit(hex.charAt(i + 1), 16));
        return data;
    }

    static public MessageDigest sha1Hash() throws NoSuchAlgorithmException {
        return MessageDigest.getInstance("SHA-1");
    }

    static public MessageDigest sha256Hash() throws NoSuchAlgorithmException {
        return MessageDigest.getInstance("SHA-256");
    }

    static public MessageDigest sha3_256Hash() throws NoSuchAlgorithmException {
        return new SHA3.DigestSHA3(256);
    }

    static public String sha1HashHex(byte data[]) {
        return toHex(sha1Hash(data));
    }

    static public String sha1HashHex(String data) {
        return sha1HashHex(data.getBytes());
    }

    static public String sha256HashHex(byte data[]) {
        return toHex(sha256Hash(data));
    }

    static public String sha256HashHex(String data) {
        return sha256HashHex(data.getBytes());
    }

    static public String sha3_256HashHex(byte data[]) {
        return toHex(sha3_256Hash(data));
    }

    static public byte[] hash(byte[] data, MessageDigest messageDigest) {
        messageDigest.reset();
        messageDigest.update(data);
        return messageDigest.digest();
    }

    static public byte[] sha1Hash(byte data[]) {
        try {
            return hash(data, sha1Hash());
        } catch (Exception e) {
            System.out.println(e.getMessage());
            return null;
        }
    }

    static public byte[] sha3_256Hash(byte data[]) {
        try {
            return hash(data, sha3_256Hash());
        } catch (Exception e) {
            System.out.println(e.getMessage());
            return null;
        }
    }

    static public byte[] sha256Hash(byte data[]) {
        try {
            return hash(data, sha256Hash());
        } catch (Exception e) {
            System.out.println(e.getMessage());
            return null;
        }
    }

    static public String generateSha1Id(ICryptoInterface crypto) {
        return sha1HashHex(crypto.generateSalt());
    }

    static public SecretKey symmetricKeyFromRaw(byte[] key, CryptoSettings.Symmetric settings) {
        return new SecretKeySpec(key, 0, key.length, settings.keyType);
    }

    private static List<String> splitIntoEqualParts(String string, int partitionSize) {
        List<String> parts = new ArrayList<>();
        int length = string.length();
        for (int i = 0; i < length; i += partitionSize)
            parts.add(string.substring(i, Math.min(length, i + partitionSize)));
        return parts;
    }

    static private String convertToPEM(String type, Key key) {
        String pemKey = "-----BEGIN " + type + "-----\n";
        List<String> parts = splitIntoEqualParts(DatatypeConverter.printBase64Binary(key.getEncoded()), 64);
        for (String part : parts)
            pemKey += part + "\n";
        pemKey += "-----END " + type + "-----";
        return pemKey;
    }

    static public String convertToPEM(PublicKey key) {
        String algo = key.getAlgorithm();
        return convertToPEM(algo + " PUBLIC KEY", key);
    }

    static public String convertToPEM(PrivateKey key) {
        String algo = key.getAlgorithm();
        return convertToPEM(algo + " PRIVATE KEY", key);
    }

    static private KeyFactory getKeyFactory(String keyType) throws NoSuchAlgorithmException {
        if (keyType.startsWith("EC")) {
            return KeyFactory.getInstance("EC");
        } else {
            return KeyFactory.getInstance(keyType);
        }
    }

    static public PrivateKey privateKeyFromRaw(byte[] key, String keyType) throws CryptoException {
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(key);
        try {
            KeyFactory keyFactory = getKeyFactory(keyType);
            return keyFactory.generatePrivate(spec);
        } catch (Exception e) {
            throw new CryptoException(e);
        }
    }

    static public PublicKey publicKeyFromRaw(byte[] key, String keyType) throws CryptoException {
        X509EncodedKeySpec spec = new X509EncodedKeySpec(key);
        try {
            KeyFactory keyFactory = getKeyFactory(keyType);
            return keyFactory.generatePublic(spec);
        } catch (Exception e) {
            throw new CryptoException(e);
        }
    }

    static private String parsePemKeyType(String pemKey) {
        int startIndex = "-----BEGIN ".length();
        int endIndex = pemKey.indexOf(" ", startIndex);
        if (startIndex >= pemKey.length() || endIndex < 0 || endIndex >= pemKey.length())
            return "";

        return pemKey.substring(startIndex, endIndex);
    }

    static public PublicKey publicKeyFromPem(String pemKey) {
        String type = parsePemKeyType(pemKey);
        pemKey = pemKey.replace("-----BEGIN " + type + " PUBLIC KEY-----\n", "");
        pemKey = pemKey.replace("-----END " + type + " PUBLIC KEY-----", "");

        byte [] decoded = DatatypeConverter.parseBase64Binary(pemKey);
        try {
            return publicKeyFromRaw(decoded, type);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    static public PrivateKey privateKeyFromPem(String pemKey) {
        String type = parsePemKeyType(pemKey);
        pemKey = pemKey.replace("-----BEGIN " + type + " PRIVATE KEY-----\n", "");
        pemKey = pemKey.replace("-----END " + type + " PRIVATE KEY-----", "");

        byte [] decoded = DatatypeConverter.parseBase64Binary(pemKey);
        try {
            return privateKeyFromRaw(decoded, type);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    static public SecretKey secretKey(byte[] key, CryptoSettings.KeyTypeSettings settings) {
        return new SecretKeySpec(key, 0, settings.keySize / 8, settings.keyType);
    }

    static public SecretKey secretKey(byte[] key, String keyType) {
        return new SecretKeySpec(key, 0, key.length, keyType);
    }
}
