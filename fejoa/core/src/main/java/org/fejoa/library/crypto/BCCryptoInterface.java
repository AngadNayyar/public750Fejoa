/*
 * Copyright 2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.crypto;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.security.*;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.KeySpec;
import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;


public class BCCryptoInterface implements ICryptoInterface {
    public BCCryptoInterface() {
        // enable “Unlimited Strength” JCE policy
        // This is necessary to use AES256!
        try {
            Field field = Class.forName("javax.crypto.JceSecurity").getDeclaredField("isRestricted");
            field.setAccessible(true);
            field.set(null, java.lang.Boolean.FALSE);
        } catch (Exception ex) {
        }
        Security.addProvider(new BouncyCastleProvider());
    }

    @Override
    public SecretKey deriveKey(String secret, byte[] salt, String algorithm, int iterations, int keyLength)
            throws CryptoException {
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance(algorithm);
            KeySpec spec = new PBEKeySpec(secret.toCharArray(), salt, iterations, keyLength);
            return factory.generateSecret(spec);
        } catch (Exception e) {
            throw new CryptoException(e.getMessage());
        }
    }

    @Override
    public KeyPair generateKeyPair(CryptoSettings.KeyTypeSettings settings) throws CryptoException {
        KeyPairGenerator keyGen;
        try {
            if (settings.keyType.startsWith("ECIES")) {
                keyGen = KeyPairGenerator.getInstance("ECIES");
                String curve = settings.keyType.substring("ECIES/".length());
                keyGen.initialize(new ECGenParameterSpec(curve));
            } else {
                keyGen = KeyPairGenerator.getInstance(settings.keyType);
                keyGen.initialize(settings.keySize, new SecureRandom());
            }
        } catch (Exception e) {
            throw new CryptoException(e.getMessage());
        }
        return keyGen.generateKeyPair();
    }

    @Override
    public byte[] encryptAsymmetric(byte[] input, PublicKey key, CryptoSettings.Asymmetric settings)
            throws CryptoException {
        Cipher cipher;
        try {
            cipher = Cipher.getInstance(settings.algorithm);
            cipher.init(Cipher.ENCRYPT_MODE, key);
            return cipher.doFinal(input);
        } catch (Exception e) {
            throw new CryptoException(e.getMessage());
        }
    }

    @Override
    public byte[] decryptAsymmetric(byte[] input, PrivateKey key, CryptoSettings.Asymmetric settings)
            throws CryptoException {
        Cipher cipher;
        try {
            cipher = Cipher.getInstance(settings.algorithm);
            cipher.init(Cipher.DECRYPT_MODE, key);
            return cipher.doFinal(input);
        } catch (Exception e) {
            throw new CryptoException(e.getMessage());
        }
    }

    @Override
    public SecretKey generateSymmetricKey(CryptoSettings.KeyTypeSettings settings) throws CryptoException {
        KeyGenerator keyGenerator;
        try {
            keyGenerator = KeyGenerator.getInstance(settings.keyType);
        } catch (Exception e) {
            throw new CryptoException(e.getMessage());
        }
        keyGenerator.init(settings.keySize, new SecureRandom());
        return keyGenerator.generateKey();
    }

    @Override
    public byte[] generateInitializationVector(int sizeBits) {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[sizeBits / 8];
        random.nextBytes(bytes);
        return bytes;
    }

    @Override
    public byte[] generateSalt() {
        return generateInitializationVector(32 * 8);
    }

    @Override
    public byte[] encryptSymmetric(byte[] input, SecretKey secretKey, byte[] iv,
                                   CryptoSettings.Symmetric settings) throws CryptoException {
        try {
            Cipher cipher = Cipher.getInstance(settings.algorithm);
            IvParameterSpec ips = new IvParameterSpec(iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, ips);
            return cipher.doFinal(input);
        } catch (Exception e) {
            throw new CryptoException(e.getMessage());
        }
    }

    @Override
    public byte[] decryptSymmetric(byte[] input, SecretKey secretKey, byte[] iv,
                                   CryptoSettings.Symmetric settings) throws CryptoException {
        try {
            Cipher cipher = Cipher.getInstance(settings.algorithm);
            IvParameterSpec ips = new IvParameterSpec(iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, ips);
            return cipher.doFinal(input);
        } catch (Exception e) {
            throw new CryptoException(e.getMessage());
        }
    }

    @Override
    public InputStream encryptSymmetric(InputStream in, SecretKey secretKey, byte[] iv,
                                         CryptoSettings.Symmetric settings) throws CryptoException {
        try {
            Cipher cipher = Cipher.getInstance(settings.algorithm);
            IvParameterSpec ips = new IvParameterSpec(iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, ips);
            return new CipherInputStream(in, cipher);
        } catch (Exception e) {
            throw new CryptoException(e.getMessage());
        }
    }

    @Override
    public OutputStream encryptSymmetric(OutputStream out, SecretKey secretKey, byte[] iv,
                                         CryptoSettings.Symmetric settings) throws CryptoException {
        try {
            Cipher cipher = Cipher.getInstance(settings.algorithm);
            IvParameterSpec ips = new IvParameterSpec(iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, ips);
            return new CipherOutputStream(out, cipher);
        } catch (Exception e) {
            throw new CryptoException(e.getMessage());
        }
    }

    @Override
    public InputStream decryptSymmetric(InputStream input, SecretKey secretKey, byte[] iv,
                                        CryptoSettings.Symmetric settings) throws CryptoException {
        try {
            Cipher cipher = Cipher.getInstance(settings.algorithm);
            IvParameterSpec ips = new IvParameterSpec(iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, ips);
            return new CipherInputStream(input, cipher);
        } catch (Exception e) {
            throw new CryptoException(e.getMessage());
        }
    }

    @Override
    public byte[] sign(byte[] input, PrivateKey key, CryptoSettings.Signature settings) throws CryptoException {
        Signature signature;
        try {
            signature = java.security.Signature.getInstance(settings.algorithm);
            signature.initSign(key);
            signature.update(input);
            return signature.sign();
        } catch (Exception e) {
            throw new CryptoException(e.getMessage());
        }
    }

    @Override
    public boolean verifySignature(byte[] message, byte[] signature, PublicKey key,
                                   CryptoSettings.Signature settings)
            throws CryptoException {
        Signature sig;
        try {
            sig = java.security.Signature.getInstance(settings.algorithm);
            sig.initVerify(key);
            sig.update(message);
            return sig.verify(signature);
        } catch (Exception e) {
            throw new CryptoException(e.getMessage());
        }
    }
}
