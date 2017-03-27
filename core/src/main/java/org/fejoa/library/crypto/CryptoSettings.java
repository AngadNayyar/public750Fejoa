/*
 * Copyright 2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.crypto;

import org.fejoa.chunkstore.HashValue;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;

public class CryptoSettings {
    final static public String SHA2 = "SHA2";
    final static public String SHA3_256 = "SHA3_256";

    static public class KeyTypeSettings {
        public int keySize = -1;
        public String keyType;
    }

    static public class Password {
        // kdf
        public String kdfAlgorithm;
        public int kdfIterations = -1;
        public int passwordSize = -1;

        public HashValue hash(MessageDigest messageDigest) {
            DataOutputStream outputStream = new DataOutputStream(new DigestOutputStream(new OutputStream() {
                @Override
                public void write(int i) throws IOException {

                }
            }, messageDigest));

            try {
                outputStream.write(kdfAlgorithm.getBytes());
                outputStream.writeInt(kdfIterations);
                outputStream.writeInt(passwordSize);
                outputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return new HashValue(messageDigest.digest());
        }
    }

    static public class Symmetric extends KeyTypeSettings{
        public String algorithm;
        public int ivSize = -1;
    }

    static public class Asymmetric extends KeyTypeSettings {
        public String algorithm;
    }

    static public class Signature extends KeyTypeSettings {
        public String algorithm;
    }

    public Password masterPassword = new Password();
    public Asymmetric publicKey = new Asymmetric();
    public Signature signature = new Signature();
    public Symmetric symmetric = new Symmetric();

    private CryptoSettings() {

    }

    static public void setDefaultEC(CryptoSettings cryptoSettings) {
        cryptoSettings.publicKey.algorithm = "ECIES";
        cryptoSettings.publicKey.keyType = "ECIES/secp256r1";
        cryptoSettings.publicKey.keySize = 0;

        cryptoSettings.signature.algorithm = "SHA256withECDSA";
        cryptoSettings.signature.keyType = "ECIES/secp256r1";
        cryptoSettings.signature.keySize = 0;
    }

    static public void setDefaultRSA(CryptoSettings cryptoSettings) {
        cryptoSettings.publicKey.algorithm = "RSA/NONE/PKCS1PADDING";
        cryptoSettings.publicKey.keyType = "RSA";
        cryptoSettings.publicKey.keySize = 2048;

        cryptoSettings.signature.algorithm = "SHA1withRSA";
        cryptoSettings.signature.keyType = "RSA";
        cryptoSettings.signature.keySize = 2048;
    }

    static public CryptoSettings getDefault() {
        CryptoSettings cryptoSettings = new CryptoSettings();

        setDefaultRSA(cryptoSettings);

        cryptoSettings.symmetric.algorithm = "AES/CTR/NoPadding";
        cryptoSettings.symmetric.keyType = "AES";
        cryptoSettings.symmetric.keySize = 256;
        cryptoSettings.symmetric.ivSize = 16 * 8;

        cryptoSettings.masterPassword.kdfAlgorithm = "PBKDF2WithHmacSHA512";
        cryptoSettings.masterPassword.kdfIterations = 20000;
        cryptoSettings.masterPassword.passwordSize = 256;

        return cryptoSettings;
    }

    static public CryptoSettings getFast() {
        CryptoSettings cryptoSettings = getDefault();
        cryptoSettings.publicKey.keySize = 512;

        cryptoSettings.symmetric.keySize = 128;
        cryptoSettings.symmetric.ivSize = 16 * 8;

        cryptoSettings.masterPassword.kdfIterations = 1;

        return cryptoSettings;
    }

    static public CryptoSettings empty() {
        return new CryptoSettings();
    }

    static public Signature signatureSettings(String algorithm) {
        Signature cryptoSettings = signatureSettings();
        cryptoSettings.algorithm = algorithm;
        return cryptoSettings;
    }

    static public Signature signatureSettings() {
        CryptoSettings settings = empty();
        CryptoSettings defaultSettings = getDefault();

        settings.signature.algorithm = defaultSettings.signature.algorithm;
        settings.signature.keyType = defaultSettings.signature.keyType;
        settings.signature.keySize = defaultSettings.signature.keySize;
        return settings.signature;
    }

    static public Symmetric symmetricSettings(String keyType, String algorithm) {
        CryptoSettings cryptoSettings = empty();
        cryptoSettings.symmetric.keyType = keyType;
        cryptoSettings.symmetric.algorithm = algorithm;
        return cryptoSettings.symmetric;
    }

    static public CryptoSettings symmetricKeyTypeSettings(String keyType) {
        CryptoSettings cryptoSettings = empty();
        cryptoSettings.symmetric.keyType = keyType;
        return cryptoSettings;
    }
}
