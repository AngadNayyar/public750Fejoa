/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library;

import org.apache.commons.codec.binary.Base64;
import org.fejoa.chunkstore.HashValue;
import org.fejoa.library.crypto.*;
import org.fejoa.library.database.DefaultCommitSignature;
import org.fejoa.library.database.ICommitSignature;
import org.fejoa.library.database.StorageDir;
import org.json.JSONException;
import org.json.JSONObject;

import javax.crypto.SecretKey;
import java.io.IOException;


class StorageDirObject {
    final protected StorageDir storageDir;

    protected StorageDirObject(StorageDir storageDir) {
        this.storageDir = storageDir;
    }

    public StorageDir getStorageDir() {
        return storageDir;
    }

    public void commit() throws IOException {
        storageDir.commit();
    }
}

class KeyStore2 extends StorageDirObject {
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

    static public class KDFCrypto {
        static public JSONObject create(FejoaContext context, SecretKey secretKey,
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

            JSONObject object = new JSONObject();
            // kdf params
            try {
                object.put(MASTER_KEY_PASSWORD_ALGORITHM_KEY, kdfSettings.kdfAlgorithm);
                object.put(MASTER_KEY_PASSWORD_SALT_KEY, Base64.encodeBase64String(salt));
                object.put(MASTER_KEY_PASSWORD_SIZE_KEY, kdfSettings.keySize);
                object.put(MASTER_KEY_PASSWORD_ITERATIONS_KEY, kdfSettings.kdfIterations);
                // master key encryption
                object.put(MASTER_KEY_KEY, Base64.encodeBase64String(encryptedMasterKey));
                object.put(MASTER_KEY_IV_KEY, Base64.encodeBase64String(masterKeyIV));
                object.put(Constants.ALGORITHM_KEY, symmetric.algorithm);
                object.put(Constants.KEY_TYPE_KEY, symmetric.keyType);
            } catch (JSONException e) {
                e.printStackTrace();
                throw new RuntimeException("Unexpected json error.");
            }

            return object;
        }

        static public SecretKey open(FejoaContext context, JSONObject jsonObject, String password) throws JSONException, CryptoException {
            // kdf params
            String kdfAlgorithm = jsonObject.getString(MASTER_KEY_PASSWORD_ALGORITHM_KEY);
            byte[] salt = Base64.decodeBase64(jsonObject.getString(MASTER_KEY_PASSWORD_SALT_KEY));
            int masterPasswordSize = jsonObject.getInt(MASTER_KEY_PASSWORD_SIZE_KEY);
            int kdfIterations = jsonObject.getInt(MASTER_KEY_PASSWORD_ITERATIONS_KEY);
            // master key encryption
            byte[] encryptedMasterKey = Base64.decodeBase64(jsonObject.getString(MASTER_KEY_KEY));
            byte[] masterKeyIV = Base64.decodeBase64(jsonObject.getString(MASTER_KEY_IV_KEY));
            String symmetricAlgorithm = jsonObject.getString(Constants.ALGORITHM_KEY);
            String symmetricKeyType = jsonObject.getString(Constants.KEY_TYPE_KEY);

            // kdf key
            ICryptoInterface crypto = context.getCrypto();
            SecretKey secretKey = crypto.deriveKey(password, salt, kdfAlgorithm, kdfIterations,
                    masterPasswordSize);
            // decrypt master key
            CryptoSettings.Symmetric settings = CryptoSettings.symmetricSettings(symmetricKeyType, symmetricAlgorithm);
            byte masterKeyBytes[] = crypto.decryptSymmetric(encryptedMasterKey, secretKey, masterKeyIV, settings);
            return CryptoHelper.symmetricKeyFromRaw(masterKeyBytes, settings);
        }
    }

    static public KeyStore2 create(FejoaContext context, String path, SigningKeyPair signingKeyPair, String password)
            throws CryptoException, IOException {
        String branch = CryptoHelper.sha1HashHex(context.getCrypto().generateInitializationVector(32));
        SymmetricKeyData keyData = SymmetricKeyData.create(context, context.getCryptoSettings().symmetric);
        StorageDir storageDir = context.getNew(path, branch, keyData,
                new DefaultCommitSignature(context, signingKeyPair));

        JSONObject kdfParams = KeyStore2.KDFCrypto.create(context, keyData.key,
                context.getCryptoSettings().masterPassword, password);
        JSONObject config = new JSONObject();
        try {
            config.put(KEYSTORE_BRANCH_KEY, branch);
            config.put(KEYSTORE_KDF_PARAMS, kdfParams);
            config.put(Constants.IV_KEY, keyData.iv);
            config.put(SYM_SETTINGS_KEY, JsonCryptoSettings.toJson(keyData.settings));
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return new KeyStore2(storageDir, config);
    }

    public static KeyStore2 open(FejoaContext context, String path, JSONObject config, String password)
            throws JSONException, CryptoException, IOException {
        String keystoreBranch = config.getString(KEYSTORE_BRANCH_KEY);
        JSONObject keystoreKDFParams = config.getJSONObject(KEYSTORE_KDF_PARAMS);
        SymmetricKeyData symmetricKeyData = new SymmetricKeyData();
        symmetricKeyData.key = KDFCrypto.open(context, keystoreKDFParams, password);
        symmetricKeyData.iv = Base64.decodeBase64(config.getString(Constants.IV_KEY));
        symmetricKeyData.settings = JsonCryptoSettings.symFromJson(config.getJSONObject(SYM_SETTINGS_KEY));
        StorageDir storageDir = context.getNew(path, keystoreBranch, symmetricKeyData, null);
        return new KeyStore2(storageDir, config);
    }

    final private JSONObject config;

    protected KeyStore2(StorageDir storageDir, JSONObject config) {
        super(storageDir);

        this.config = config;
    }

    public JSONObject getConfig() {
        return config;
    }

    public void setUserData(UserData2 userData) throws IOException {
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

class BranchList extends StorageDirList<BranchList.BranchEntry> {
    static public class BranchEntry extends StorageDirList.AbstractIdEntry {
        private String describtion = "";
        private HashValue keyId;

        static public BranchEntry read(String id, StorageDir dir) throws IOException {
            BranchEntry entry = new BranchEntry(id);
            entry.read(dir);
            return entry;
        }

        public BranchEntry(String branch) {
            super(branch);
        }

        public String getBranch() {
            return super.getId();
        }

        @Override
        public void write(StorageDir dir) throws IOException {

        }

        @Override
        public void read(StorageDir dir) throws IOException {

        }
    }

    public BranchList(StorageDir storageDir) {
        super(storageDir, new AbstractEntryIO<BranchEntry>() {
            @Override
            public String getId(BranchEntry entry) {
                return entry.getId();
            }

            @Override
            public BranchEntry read(StorageDir dir) throws IOException {
                return BranchEntry.read(idFromStoragePath(dir), dir);
            }
        });
    }
}

public class UserData2 extends StorageDirObject {
    static private String LIBS_PATH = "libs";
    static private String BRANCHES_PATH = "branches";
    static private String MYSELF_PATH = "myself";

    final private FejoaContext context;
    final private KeyStore2 keyStore;
    final private BranchList branchList;
    final private ContactPrivate myself;

    protected UserData2(FejoaContext context, StorageDir storageDir, KeyStore2 keyStore)
            throws IOException {
        super(storageDir);

        this.context = context;
        this.keyStore = keyStore;

        branchList = new BranchList(new StorageDir(storageDir, BRANCHES_PATH));
        branchList.add(new BranchList.BranchEntry(keyStore.getStorageDir().getBranch()));

        myself = new ContactPrivate(context, new StorageDir(storageDir, MYSELF_PATH));
    }

    public void commit(boolean all) throws IOException {
        if (all) {
            keyStore.commit();
        }
        storageDir.commit();
    }

    static public UserData2 create(FejoaContext context, String path, String password)
            throws IOException, CryptoException {

        CryptoSettings.Signature signatureSettings = context.getCryptoSettings().signature;
        SigningKeyPair signingKeyPair = SigningKeyPair.create(context.getCrypto(), signatureSettings);

        KeyStore2 keyStore = KeyStore2.create(context, path, signingKeyPair, password);

        String branch = CryptoHelper.sha1HashHex(context.getCrypto().generateInitializationVector(32));
        SymmetricKeyData userDataKeyData = SymmetricKeyData.create(context, context.getCryptoSettings().symmetric);
        StorageDir userDataDir = context.getNew(path, branch, userDataKeyData,
                new DefaultCommitSignature(context, signingKeyPair));

        UserData2 userData = new UserData2(context, userDataDir, keyStore);
        keyStore.setUserData(userData);
        keyStore.addSymmetricKey(userDataDir.getBranch(), userDataKeyData);

        userData.myself.addSignatureKey(signingKeyPair);
        userData.myself.getSignatureKeys().setDefault(signingKeyPair.getId());

        return userData;
    }

    static public UserData2 open(FejoaContext context, String path, JSONObject config, String password)
            throws JSONException, CryptoException, IOException {
        KeyStore2 keyStore = KeyStore2.open(context, path, config, password);
        String userDataBranch = keyStore.getUserDataBranch();
        SymmetricKeyData userDataKeyData = keyStore.getSymmetricKey(userDataBranch);

        StorageDir userDataDir = context.getNew(path, userDataBranch, userDataKeyData, null);
        UserData2 userData = new UserData2(context, userDataDir, keyStore);

        // set the commit signature
        SigningKeyPair signingKeyPair = userData.myself.getSignatureKeys().getDefault();
        ICommitSignature commitSignature = new DefaultCommitSignature(context, signingKeyPair);
        userData.getStorageDir().setCommitSignature(commitSignature);
        keyStore.getStorageDir().setCommitSignature(commitSignature);

        return userData;
    }
}
