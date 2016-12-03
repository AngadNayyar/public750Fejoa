/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.filestorage;

import java8.util.concurrent.CompletableFuture;
import org.fejoa.library.*;
import org.fejoa.library.crypto.Crypto;
import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.crypto.CryptoHelper;
import org.fejoa.library.database.IOStorageDir;
import org.fejoa.library.database.MovableStorage;
import org.fejoa.library.support.StorageLib;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutionException;

import static org.fejoa.filestorage.FileStorageManager.STORAGE_CONTEXT;


public class FileStorageEntry extends MovableStorage {
    final static private String PATH_KEY = "path";

    private IContactPublic storageOwner;
    private String id;

    static public FileStorageEntry read(IOStorageDir dir, IContactPrivate storageOwner) throws IOException, CryptoException {
        FileStorageEntry fileStorageEntry = new FileStorageEntry(dir);
        fileStorageEntry.load(storageOwner);

        return fileStorageEntry;
    }

    public FileStorageEntry(IOStorageDir dir) {
        super(dir);

        id = StorageLib.fileName(storageDir.getBaseDir());
    }

    public FileStorageEntry(FejoaContext context) {
        super(null);

        id = CryptoHelper.generateSha1Id(context.getCrypto());
    }

    public IContactPublic getStorageOwner() {
        return storageOwner;
    }

    public void setTo(String path, String branch) throws IOException {
        storageDir.writeString(PATH_KEY, path);
        storageDir.writeString(Constants.BRANCH_KEY, branch);
    }

    public CompletableFuture<String> getPath() {
        return storageDir.readStringAsync(PATH_KEY);
    }

    public String getBranch() throws IOException {
        return storageDir.readString(Constants.BRANCH_KEY);
    }

    public BranchInfo getBranchInfo() throws IOException, CryptoException {
        return storageOwner.getBranchList().get(getBranch(), STORAGE_CONTEXT);
    }

    public String getId() {
        return id;
    }

    public void load(IContactPublic storageOwner) throws IOException, CryptoException {
        this.storageOwner = storageOwner;
    }
}

