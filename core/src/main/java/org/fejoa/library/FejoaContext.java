/*
 * Copyright 2015-2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library;

import org.fejoa.chunkstore.ChunkStoreBranchLog;
import org.fejoa.chunkstore.Config;
import org.fejoa.chunkstore.HashValue;
import org.fejoa.chunkstore.Repository;
import org.fejoa.library.crypto.Crypto;
import org.fejoa.library.crypto.CryptoSettings;
import org.fejoa.library.crypto.ICryptoInterface;
import org.fejoa.library.database.CSRepositoryBuilder;
import org.fejoa.library.database.ICommitSignature;
import org.fejoa.library.database.StorageDir;
import org.fejoa.library.remote.AuthInfo;
import org.fejoa.library.crypto.CryptoException;

import java.io.*;
import java.util.*;
import java.util.concurrent.Executor;


public class FejoaContext {
    final static private String INFO_FILE = "info";

    final private File homeDir;
    private CryptoSettings cryptoSettings = CryptoSettings.getDefault();

    private Map<String, StorageDir> secureStorageDirs = new HashMap<>();
    private Map<String, String> rootPasswords = new HashMap<>();

    // executes a task in the context thread
    private Executor contextExecutor;

    public FejoaContext(String homeDir, Executor contextExecutor) {
        this(new File(homeDir), contextExecutor);
    }

    public FejoaContext(File homeDir, Executor contextExecutor) {
        this.homeDir = homeDir;
        this.homeDir.mkdirs();

        setContextExecutor(contextExecutor);
    }

    public void setContextExecutor(Executor contextExecutor) {
        this.contextExecutor = contextExecutor;
    }

    public Executor getContextExecutor() {
        return contextExecutor;
    }

    public File getHomeDir() {
        return homeDir;
    }

    public ICryptoInterface getCrypto() {
        return Crypto.get();
    }

    public CryptoSettings getCryptoSettings() {
        return cryptoSettings;
    }


    public StorageDir getPlainStorage(String branch) throws IOException, CryptoException {
        return getStorage(branch, null, null);
    }

    public StorageDir getPlainStorage(File path, String branch) throws IOException, CryptoException {
        return getStorage(path, branch, null,null, null);
    }

    public StorageDir getStorage(String branch, HashValue rev, SymmetricKeyData cryptoKeyData,
                                 ICommitSignature commitSignature) throws IOException, CryptoException {
        return getStorage(getChunkStoreDir(), branch, rev, cryptoKeyData, commitSignature);
    }

    public StorageDir getStorage(String branch, SymmetricKeyData cryptoKeyData, ICommitSignature commitSignature)
            throws IOException, CryptoException {
        return getStorage(getChunkStoreDir(), branch, null, cryptoKeyData, commitSignature);
    }

    public StorageDir getStorage(File path, String branch, HashValue rev, SymmetricKeyData cryptoKeyData,
                                  ICommitSignature commitSignature) throws IOException, CryptoException {
        StorageDir dir = secureStorageDirs.get(path.getPath() + ":" + branch);
        if (dir != null && dir.getBranch().equals(branch))
            return new StorageDir(dir);

        // not found create one
        path.mkdirs();

        Repository repository = CSRepositoryBuilder.openOrCreate(this, path, branch, rev, cryptoKeyData);
        StorageDir storageDir = new StorageDir(repository, "", contextExecutor);
        secureStorageDirs.put(path.getPath() + ":" + branch, storageDir);
        storageDir = new StorageDir(storageDir);
        storageDir.setCommitSignature(commitSignature);
        return storageDir;
    }

    public HashValue getStorageLogTip(String branch) throws IOException {
        return getStorageLogTip(getChunkStoreDir(), branch);
    }

    public HashValue getStorageLogTip(StorageDir storageDir) throws IOException {
        ChunkStoreBranchLog log = ((Repository)storageDir.getDatabase()).getBranchLog();
        if (log.getLatest() == null)
            return Config.newBoxHash();
        return log.getLatest().getEntryId();
    }

    public HashValue getStorageLogTip(File repoDir, String branch) throws IOException {
        File logDir = new File(repoDir, "branches");
        ChunkStoreBranchLog log = new ChunkStoreBranchLog(new File(logDir, branch));
        if (log.getLatest() == null)
            return Config.newBoxHash();
        return log.getLatest().getEntryId();
    }

    private File getChunkStoreDir() {
        return new File(homeDir, ".chunkstore");
    }

    public void setUserDataId(String id) throws IOException {
        File file = new File(homeDir, INFO_FILE);
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file)));
        writer.write(id);
    }

    public String getUserDataId() throws IOException {
        File file = new File(homeDir, INFO_FILE);
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(new FileInputStream(file)));
        return bufferedReader.readLine();
    }

    private String makeName(String serverUser, String server) {
        return serverUser + "@" + server;
    }

    public String getRootPassword(String serverUser, String server) {
        return rootPasswords.get(makeName(serverUser, server));
    }

    public AuthInfo getRootAuthInfo(Remote remote) {
        return getRootAuthInfo(remote.getUser(), remote.getServer());
    }

    public AuthInfo.Password getRootAuthInfo(String serverUser, String server) {
        String password = getRootPassword(serverUser, server);
        if (password == null)
            password = "";
        return new AuthInfo.Password(password);
    }

    public void registerRootPassword(String serverUser, String server, String password) {
        rootPasswords.put(makeName(serverUser, server), password);
    }
}
