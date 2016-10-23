/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library;

import org.apache.commons.codec.binary.Base64;
import org.fejoa.library.crypto.*;
import org.fejoa.library.database.DefaultCommitSignature;
import org.fejoa.library.database.IOStorageDir;
import org.fejoa.library.database.StorageDir;
import org.fejoa.library.support.StorageLib;
import org.json.JSONException;
import org.json.JSONObject;

import javax.crypto.SecretKey;
import java.io.IOException;


public class KeyStore extends StorageDirObject {
    final static String MASTER_KEY_KEY = "masterKey";
    final static String MASTER_KEY_IV_KEY = "masterKeyIV";
    final static String MASTER_KEY_PASSWORD_SALT_KEY = "masterPasswordSalt";
    final static String MASTER_KEY_PASSWORD_SETTINGS_KEY = "kdfSettings";

    final static String USER_DATA_BRANCH_KEY = "userDataBranch";

    static public String KEYSTORE_BRANCH_KEY = "keystore";
    static public String KEYSTORE_KDF_PARAMS = "keystoreKDFParams";
    static public String SYM_SETTINGS_KEY = "symSettings";

    static public String SYM_KEY_PATH = "symKeys";
    static public String SIGNATURE_KEY_PATH = "signKeyPairs";
    static public String ENCRYPTION_PATH = "pubKeyPairs";

    static class Settings {
        final public String branch;
        final public KDFCrypto kdfCrypto;
        final public byte iv[];
        final public CryptoSettings.Symmetric settings;

        public Settings(String branch, KDFCrypto kdfCrypto, byte[] iv, CryptoSettings.Symmetric settings) {
            this.branch = branch;
            this.kdfCrypto = kdfCrypto;
            this.iv = iv;
            this.settings = settings;
        }

        public Settings(JSONObject config) throws JSONException {
            branch = config.getString(KEYSTORE_BRANCH_KEY);
            kdfCrypto = new KDFCrypto(config.getJSONObject(KEYSTORE_KDF_PARAMS));
            iv = Base64.decodeBase64(config.getString(Constants.IV_KEY));
            settings = JsonCryptoSettings.symFromJson(config.getJSONObject(SYM_SETTINGS_KEY));
        }

        public JSONObject toJson() {
            JSONObject config = new JSONObject();
            try {
                config.put(KEYSTORE_BRANCH_KEY, branch);
                config.put(KEYSTORE_KDF_PARAMS, kdfCrypto.toJson());
                config.put(Constants.IV_KEY, Base64.encodeBase64String(iv));
                config.put(SYM_SETTINGS_KEY, JsonCryptoSettings.toJson(settings));
            } catch (JSONException e) {
                e.printStackTrace();
                throw new RuntimeException("Unexpected");
            }
            return config;
        }
    }

    static public class KDFCrypto {
        final public byte[] kdfSalt;
        final public CryptoSettings.Password kdfSettings;

        final public byte[] encryptedMasterKey;
        final public byte[] masterKeyIV;
        final public CryptoSettings.Symmetric symmetricSettings;

        static public KDFCrypto create(FejoaContext context, SecretKey secretKey,
                                        CryptoSettings.Password kdfSettings, String password) throws CryptoException {
            // kdf key
            ICryptoInterface crypto = context.getCrypto();
            byte[] salt = crypto.generateSalt();
            SecretKey passwordKey = crypto.deriveKey(password, salt, kdfSettings.kdfAlgorithm,
                    kdfSettings.kdfIterations, kdfSettings.passwordSize);
            // encrypt master key
            CryptoSettings.Symmetric symmetric = context.getCryptoSettings().symmetric;
            byte[] masterKeyIV = crypto.generateInitializationVector(symmetric.ivSize);
            byte[] encryptedMasterKey = crypto.encryptSymmetric(secretKey.getEncoded(), passwordKey, masterKeyIV,
                    symmetric);

            return new KDFCrypto(salt, kdfSettings, encryptedMasterKey, masterKeyIV, symmetric);
        }

        static public SecretKey open(FejoaContext context, KDFCrypto config, String password) throws CryptoException {
            // kdf key
            ICryptoInterface crypto = context.getCrypto();
            SecretKey secretKey = crypto.deriveKey(password, config.kdfSalt, config.kdfSettings.kdfAlgorithm,
                    config.kdfSettings.kdfIterations, config.kdfSettings.passwordSize);
            // decrypt master key
            CryptoSettings.Symmetric settings = CryptoSettings.symmetricSettings(config.symmetricSettings.keyType,
                    config.symmetricSettings.algorithm);
            byte masterKeyBytes[] = crypto.decryptSymmetric(config.encryptedMasterKey, secretKey, config.masterKeyIV,
                    settings);
            return CryptoHelper.symmetricKeyFromRaw(masterKeyBytes, settings);
        }

        public KDFCrypto(byte[] kdfSalt, CryptoSettings.Password kdfSettings,
                         byte[] encryptedMasterKey, byte[] masterKeyIV, CryptoSettings.Symmetric symmetricSettings) {
            this.kdfSalt = kdfSalt;
            this.kdfSettings = kdfSettings;
            this.encryptedMasterKey = encryptedMasterKey;
            this.masterKeyIV = masterKeyIV;
            this.symmetricSettings = symmetricSettings;
        }

        public KDFCrypto(JSONObject jsonObject) throws JSONException {
            // kdf params
            kdfSalt = Base64.decodeBase64(jsonObject.getString(MASTER_KEY_PASSWORD_SALT_KEY));
            kdfSettings = JsonCryptoSettings.passwordFromJson(jsonObject.getJSONObject(MASTER_KEY_PASSWORD_SETTINGS_KEY));
            // master key encryption
            encryptedMasterKey = Base64.decodeBase64(jsonObject.getString(MASTER_KEY_KEY));
            masterKeyIV = Base64.decodeBase64(jsonObject.getString(MASTER_KEY_IV_KEY));
            symmetricSettings = JsonCryptoSettings.symFromJson(jsonObject.getJSONObject(SYM_SETTINGS_KEY));
        }

        public JSONObject toJson() {
            JSONObject object = new JSONObject();
            try {
                // kdf params
                object.put(MASTER_KEY_PASSWORD_SALT_KEY, Base64.encodeBase64String(kdfSalt));
                object.put(MASTER_KEY_PASSWORD_SETTINGS_KEY, JsonCryptoSettings.toJson(kdfSettings));
                // master key encryption
                object.put(MASTER_KEY_KEY, Base64.encodeBase64String(encryptedMasterKey));
                object.put(MASTER_KEY_IV_KEY, Base64.encodeBase64String(masterKeyIV));
                object.put(SYM_SETTINGS_KEY, JsonCryptoSettings.toJson(symmetricSettings));
            } catch (JSONException e) {
                e.printStackTrace();
                throw new RuntimeException("Unexpected json error.");
            }

            return object;
        }
    }

    static public KeyStore create(FejoaContext context, SigningKeyPair signingKeyPair, String password)
            throws CryptoException, IOException {
        String branch = CryptoHelper.sha1HashHex(context.getCrypto().generateInitializationVector(32));
        SymmetricKeyData keyData = SymmetricKeyData.create(context, context.getCryptoSettings().symmetric);
        StorageDir storageDir = context.getStorage(branch, keyData,
                new DefaultCommitSignature(context, signingKeyPair));

        KDFCrypto kdfParams = KDFCrypto.create(context, keyData.key,
                context.getCryptoSettings().masterPassword, password);
        Settings settings = new Settings(branch, kdfParams, keyData.iv, keyData.settings);
        return new KeyStore(context, storageDir, settings);
    }

    public static KeyStore open(FejoaContext context, Settings settings, String password)
            throws CryptoException, IOException {
        String keystoreBranch = settings.branch;
        SymmetricKeyData symmetricKeyData = new SymmetricKeyData();
        symmetricKeyData.key = KDFCrypto.open(context, settings.kdfCrypto, password);
        symmetricKeyData.iv = settings.iv;
        symmetricKeyData.settings = settings.settings;
        StorageDir storageDir = context.getStorage(keystoreBranch, symmetricKeyData, null);
        return new KeyStore(context, storageDir, settings);
    }

    final private Settings settings;

    protected KeyStore(FejoaContext context, StorageDir storageDir, Settings settings) {
        super(context, storageDir);

        this.settings = settings;
    }

    public String getId() {
        return storageDir.getBranch();
    }

    public Settings getConfig() {
        return settings;
    }

    public void setUserData(UserData userData) throws IOException {
        storageDir.writeString(USER_DATA_BRANCH_KEY, userData.getStorageDir().getBranch());
    }

    public String getUserDataBranch() throws IOException {
        return storageDir.readString(USER_DATA_BRANCH_KEY);
    }

    private String getKeyIdPath(String id, String context) {
        String contextPath = context.replace('.', '/');
        return StorageLib.appendDir(contextPath, id);
    }

    public void addSymmetricKey(String id, SymmetricKeyData keyData, String context)
            throws IOException, CryptoException {
        String keyIdPath = getKeyIdPath(id, context);
        keyData.write(new IOStorageDir(storageDir, StorageLib.appendDir(SYM_KEY_PATH, keyIdPath)));
    }

    public SymmetricKeyData getSymmetricKey(String id, String context) throws IOException, CryptoException {
        String keyIdPath = getKeyIdPath(id, context);
        StorageDir dir = new StorageDir(storageDir, StorageLib.appendDir(SYM_KEY_PATH, keyIdPath));
        return SymmetricKeyData.open(dir);
    }
}
