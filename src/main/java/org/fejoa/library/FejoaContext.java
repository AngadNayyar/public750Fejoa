/*
 * Copyright 2015-2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library;

import org.fejoa.chunkstore.*;
import org.fejoa.library.crypto.Crypto;
import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.crypto.CryptoSettings;
import org.fejoa.library.crypto.ICryptoInterface;
import org.fejoa.library.database.CSRepositoryBuilder;
import org.fejoa.library.database.ICommitSignature;
import org.fejoa.library.database.JGitInterface;
import org.fejoa.library.database.StorageDir;
import org.fejoa.library.remote.ConnectionManager;

import java.io.*;
import java.util.*;


public class FejoaContext {
    final static private String INFO_FILE = "info";

    final private String homeDir;
    private CryptoSettings cryptoSettings = CryptoSettings.getDefault();

    private Map<String, StorageDir> secureStorageDirs = new HashMap<>();
    private Map<String, String> rootPasswords = new HashMap<>();

    public FejoaContext(String homeDir) {
        this.homeDir = homeDir;
        new File(homeDir).mkdirs();
    }

    public String getHomeDir() {
        return homeDir;
    }

    public StorageDir getStorage(String branch) throws IOException {
        return get(".git", branch);
    }

    public ICryptoInterface getCrypto() {
        return Crypto.get();
    }

    public CryptoSettings getCryptoSettings() {
        return cryptoSettings;
    }

    private StorageDir get(String path, String branch) throws IOException {
        path = StorageDir.appendDir(homeDir, path);
        StorageDir dir = secureStorageDirs.get(path + ":" + branch);
        if (dir != null && dir.getBranch().equals(branch))
            return new StorageDir(dir);

        // not found create one
        JGitInterface database = new JGitInterface();
        database.init(path, branch, true);

        StorageDir storageDir = new StorageDir(database, "");
        secureStorageDirs.put(path + ":" + branch, storageDir);
        return new StorageDir(storageDir);
    }

    public StorageDir getPlainStorage(String branch) throws IOException, CryptoException {
        return getStorage(branch, null, null);
    }

    public StorageDir getStorage(String branch, SymmetricKeyData cryptoKeyData, ICommitSignature commitSignature)
            throws IOException, CryptoException {
        return getNew(getChunkStoreDir(), branch, cryptoKeyData, commitSignature);
    }

    public HashValue getStorageLogTip(String branch) throws IOException {
        String logDir = StorageDir.appendDir(getChunkStoreDir(), "branches");
        ChunkStoreBranchLog log = new ChunkStoreBranchLog(new File(logDir, branch));
        if (log.getLatest() == null)
            return new HashValue(new byte[0]);
        return log.getLatest().getEntryId();
    }

    private String getChunkStoreDir() {
        return StorageDir.appendDir(homeDir, ".chunkstore");
    }

    private StorageDir getNew(String path, String branch, SymmetricKeyData cryptoKeyData,
                             ICommitSignature commitSignature) throws IOException, CryptoException {
        StorageDir dir = secureStorageDirs.get(path + ":" + branch);
        if (dir != null && dir.getBranch().equals(branch))
            return new StorageDir(dir);

        // not found create one
        File pathFile = new File(path);
        pathFile.mkdirs();

        Repository repository = CSRepositoryBuilder.openOrCreate(this, pathFile, branch, cryptoKeyData);
        StorageDir storageDir = new StorageDir(repository, "");
        secureStorageDirs.put(path + ":" + branch, storageDir);
        storageDir = new StorageDir(storageDir);
        storageDir.setCommitSignature(commitSignature);
        return storageDir;
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

    public ConnectionManager.AuthInfo getTokenAuthInfo(Remote remote, String branch, int rights) {
        // TODO implement
        return getRootAuthInfo(remote.getUser(), remote.getServer());
    }

    public ConnectionManager.AuthInfo getRootAuthInfo(Remote remote) {
        return getRootAuthInfo(remote.getUser(), remote.getServer());
    }

    public ConnectionManager.AuthInfo getRootAuthInfo(String serverUser, String server) {
        String password = getRootPassword(serverUser, server);
        if (password == null)
            password = "";
        return new ConnectionManager.AuthInfo(serverUser, server, password);
    }

    private String makeName(String serverUser, String server) {
        return serverUser + "@" + server;
    }

    private String getRootPassword(String serverUser, String server) {
        return rootPasswords.get(makeName(serverUser, server));
    }

    public void registerRootPassword(String serverUser, String server, String password) {
        rootPasswords.put(makeName(serverUser, server), password);
    }
}
