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
import org.fejoa.library.database.StorageDir;
import org.json.JSONException;
import org.json.JSONObject;

import javax.crypto.SecretKey;
import java.io.IOException;


public class KeyStore extends StorageDirObject {
    final static String MASTER_KEY_KEY = "masterKey";
    final static String MASTER_KEY_IV_KEY = "masterKeyIV";
    final static String MASTER_KEY_PASSWORD_ALGORITHM_KEY = "masterPasswordAlgo";
    final static String MASTER_KEY_PASSWORD_SALT_KEY = "masterPasswordSalt";
    final static String MASTER_KEY_PASSWORD_SIZE_KEY = "masterPasswordSize";
    final static String MASTER_KEY_PASSWORD_ITERATIONS_KEY = "masterPasswordIterations";

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
        final public String kdfAlgorithm;
        final public byte[] salt;
        final public int masterPasswordSize;
        final public int kdfIterations;
        final public byte[] encryptedMasterKey;
        final public byte[] masterKeyIV;
        final public String symmetricAlgorithm;
        final public String symmetricKeyType;

        static public KDFCrypto create(FejoaContext context, SecretKey secretKey,
                                        CryptoSettings.Password kdfSettings, String password) throws CryptoException {
            // kdf key
            ICryptoInterface crypto = context.getCrypto();
            byte[] salt = crypto.generateSalt();
            SecretKey passwordKey = crypto.deriveKey(password, salt, kdfSettings.kdfAlgorithm,
                    kdfSettings.kdfIterations, kdfSettings.keySize);
            // encrypt master key
            CryptoSettings.Symmetric symmetric = context.getCryptoSettings().symmetric;
            byte[] masterKeyIV = crypto.generateInitializationVector(kdfSettings.ivSize);
            byte[] encryptedMasterKey = crypto.encryptSymmetric(secretKey.getEncoded(), passwordKey, masterKeyIV,
                    symmetric);

            return new KDFCrypto(kdfSettings.kdfAlgorithm, salt, kdfSettings.keySize, kdfSettings.kdfIterations,
                    encryptedMasterKey, masterKeyIV, symmetric.algorithm, symmetric.keyType);
        }

        static public SecretKey open(FejoaContext context, KDFCrypto config, String password) throws CryptoException {
            // kdf key
            ICryptoInterface crypto = context.getCrypto();
            SecretKey secretKey = crypto.deriveKey(password, config.salt, config.kdfAlgorithm, config.kdfIterations,
                    config.masterPasswordSize);
            // decrypt master key
            CryptoSettings.Symmetric settings = CryptoSettings.symmetricSettings(config.symmetricKeyType,
                    config.symmetricAlgorithm);
            byte masterKeyBytes[] = crypto.decryptSymmetric(config.encryptedMasterKey, secretKey, config.masterKeyIV,
                    settings);
            return CryptoHelper.symmetricKeyFromRaw(masterKeyBytes, settings);
        }

        public KDFCrypto(String kdfAlgorithm, byte[] salt, int masterPasswordSize, int kdfIterations,
                      byte[] encryptedMasterKey, byte[] masterKeyIV, String symmetricAlgorithm,
                      String symmetricKeyType) {
            this.kdfAlgorithm = kdfAlgorithm;
            this.salt = salt;
            this.masterPasswordSize = masterPasswordSize;
            this.kdfIterations = kdfIterations;
            this.encryptedMasterKey = encryptedMasterKey;
            this.masterKeyIV = masterKeyIV;
            this.symmetricAlgorithm = symmetricAlgorithm;
            this.symmetricKeyType = symmetricKeyType;
        }

        public KDFCrypto(JSONObject jsonObject) throws JSONException {
            // kdf params
            kdfAlgorithm = jsonObject.getString(MASTER_KEY_PASSWORD_ALGORITHM_KEY);
            salt = Base64.decodeBase64(jsonObject.getString(MASTER_KEY_PASSWORD_SALT_KEY));
            masterPasswordSize = jsonObject.getInt(MASTER_KEY_PASSWORD_SIZE_KEY);
            kdfIterations = jsonObject.getInt(MASTER_KEY_PASSWORD_ITERATIONS_KEY);
            // master key encryption
            encryptedMasterKey = Base64.decodeBase64(jsonObject.getString(MASTER_KEY_KEY));
            masterKeyIV = Base64.decodeBase64(jsonObject.getString(MASTER_KEY_IV_KEY));
            symmetricAlgorithm = jsonObject.getString(Constants.ALGORITHM_KEY);
            symmetricKeyType = jsonObject.getString(Constants.KEY_TYPE_KEY);
        }

        public JSONObject toJson() {
            JSONObject object = new JSONObject();
            // kdf params
            try {
                object.put(MASTER_KEY_PASSWORD_ALGORITHM_KEY, kdfAlgorithm);
                object.put(MASTER_KEY_PASSWORD_SALT_KEY, Base64.encodeBase64String(salt));
                object.put(MASTER_KEY_PASSWORD_SIZE_KEY, masterPasswordSize);
                object.put(MASTER_KEY_PASSWORD_ITERATIONS_KEY, kdfIterations);
                // master key encryption
                object.put(MASTER_KEY_KEY, Base64.encodeBase64String(encryptedMasterKey));
                object.put(MASTER_KEY_IV_KEY, Base64.encodeBase64String(masterKeyIV));
                object.put(Constants.ALGORITHM_KEY, symmetricAlgorithm);
                object.put(Constants.KEY_TYPE_KEY, symmetricKeyType);
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

    public void addSymmetricKey(String id, SymmetricKeyData keyData) throws IOException {
        keyData.write(new StorageDir(storageDir, StorageDir.appendDir(SYM_KEY_PATH, id)));
    }

    public SymmetricKeyData getSymmetricKey(String id) throws IOException, CryptoException {
        StorageDir dir = new StorageDir(storageDir, StorageDir.appendDir(SYM_KEY_PATH, id));
        return SymmetricKeyData.open(dir);
    }
}
