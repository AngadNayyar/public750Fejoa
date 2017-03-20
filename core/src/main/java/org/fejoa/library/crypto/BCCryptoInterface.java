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
import java.lang.reflect.Modifier;
import java.security.*;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.KeySpec;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;


public class BCCryptoInterface implements ICryptoInterface {
    static class JavaSecurityRestrictionRemover {
        // Based on http://stackoverflow.com/questions/1179672/how-to-avoid-installing-unlimited-strength-jce-policy-files-when-deploying-an
        private JavaSecurityRestrictionRemover() {
            Logger logger = Logger.getGlobal();
            if (!isRestrictedCryptography()) {
                logger.fine("Cryptography restrictions removal not needed");
                return;
            }
            try {
                /*
                 * Do the following, but with reflection to bypass access checks:
                 *
                 * JceSecurity.isRestricted = false;
                 * JceSecurity.defaultPolicy.perms.clear();
                 * JceSecurity.defaultPolicy.add(CryptoAllPermission.INSTANCE);
                 */
                final Class<?> jceSecurity = Class.forName("javax.crypto.JceSecurity");
                final Class<?> cryptoPermissions = Class.forName("javax.crypto.CryptoPermissions");
                final Class<?> cryptoAllPermission = Class.forName("javax.crypto.CryptoAllPermission");

                final Field isRestrictedField = jceSecurity.getDeclaredField("isRestricted");
                isRestrictedField.setAccessible(true);
                final Field modifiersField = Field.class.getDeclaredField("modifiers");
                modifiersField.setAccessible(true);
                modifiersField.setInt(isRestrictedField, isRestrictedField.getModifiers() & ~Modifier.FINAL);
                isRestrictedField.set(null, false);

                final Field defaultPolicyField = jceSecurity.getDeclaredField("defaultPolicy");
                defaultPolicyField.setAccessible(true);
                final PermissionCollection defaultPolicy = (PermissionCollection) defaultPolicyField.get(null);

                final Field perms = cryptoPermissions.getDeclaredField("perms");
                perms.setAccessible(true);
                ((Map<?, ?>) perms.get(defaultPolicy)).clear();

                final Field instance = cryptoAllPermission.getDeclaredField("INSTANCE");
                instance.setAccessible(true);
                defaultPolicy.add((Permission) instance.get(null));

                logger.fine("Successfully removed cryptography restrictions");
            } catch (final Exception e) {
                logger.log(Level.WARNING, "Failed to remove cryptography restrictions", e);
            }
        }

        private static boolean isRestrictedCryptography() {
            // This simply matches the Oracle JRE, but not OpenJDK.
            return "Java(TM) SE Runtime Environment".equals(System.getProperty("java.runtime.name"));
        }
    }

    // enable “Unlimited Strength” JCE policy
    // This is necessary to use AES256!
    static private JavaSecurityRestrictionRemover remover = new JavaSecurityRestrictionRemover();

    public BCCryptoInterface() {
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
