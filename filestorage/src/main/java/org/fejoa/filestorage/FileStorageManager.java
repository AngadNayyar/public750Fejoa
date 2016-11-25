/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.filestorage;

import org.fejoa.library.*;
import org.fejoa.library.command.AccessCommandHandler;
import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.database.IOStorageDir;
import org.fejoa.library.database.StorageDir;
import org.fejoa.library.support.Task;
import org.json.JSONException;

import java.io.File;
import java.io.IOException;


public class FileStorageManager {
    final static public String STORAGE_CONTEXT = "org.fejoa.filestorage";
    final private Client client;
    final private StorageDirList<FileStorageEntry> storageList;
    final private AppContext appContext;

    public FileStorageManager(Client client) {
        this.client = client;
        appContext = client.getUserData().getConfigStore().getAppContext(STORAGE_CONTEXT);
        storageList = new StorageDirList<>(appContext.getStorageDir(),
                new StorageDirList.AbstractEntryIO<FileStorageEntry>() {
                    @Override
                    public String getId(FileStorageEntry entry) {
                        return entry.getId();
                    }

                    @Override
                    public FileStorageEntry read(IOStorageDir dir) throws IOException, CryptoException {
                        FileStorageEntry fileStorageEntry = new FileStorageEntry();
                        fileStorageEntry.read(dir);
                        return fileStorageEntry;
                    }
                });
    }

    public void addAccessGrantedHandler(AccessCommandHandler.IContextHandler handler) {
        appContext.addAccessGrantedHandler(client.getIncomingCommandManager(), handler);
    }

    public StorageDirList<FileStorageEntry> getFileStorageList() {
        return storageList;
    }

    public void createNewStorage(String path) throws IOException, CryptoException {
        File file = new File(path);
        /*if (file.exists()) {
            statusManager.info("File storage dir: " + file.getPath() + "exists");
            return;
        }*/

        file.mkdirs();

        FejoaContext context = client.getContext();
        UserData userData = client.getUserData();
        BranchInfo branchInfo = userData.createNewEncryptedStorage(STORAGE_CONTEXT, "File Storage");
        Remote remote = userData.getGateway();
        branchInfo.addLocation(remote.getId(), context.getRootAuthInfo(remote));
        userData.addBranch(branchInfo);

        storageList.add(new FileStorageEntry(file, branchInfo));
        userData.commit(true);
    }

    public void grantAccess(FileStorageEntry entry, int accessRights, ContactPublic contactPublic)
            throws IOException, JSONException, CryptoException {
        client.grantAccess(entry.getBranch(), STORAGE_CONTEXT, accessRights, contactPublic);
    }

    public void sync(FileStorageEntry entry, Task.IObserver<CheckoutDir.Update, CheckoutDir.Result> observer)
            throws IOException, CryptoException {
        File destination = entry.getPath();
        StorageDir indexStorage = client.getContext().getPlainStorage(new File(destination, ".index"),
                entry.getBranch());
        Index index = new Index(indexStorage);
        UserData userData = client.getUserData();
        BranchInfo branchInfo = userData.findBranchInfo(entry.getBranch(), STORAGE_CONTEXT);
        final StorageDir branchStorage = userData.getStorageDir(branchInfo);
        CheckoutDir checkoutDir = new CheckoutDir(branchStorage, index, destination);
        Task<CheckoutDir.Update, CheckoutDir.Result> checkIn = checkoutDir.checkIn();
        checkIn.setStartScheduler(new Task.CurrentThreadScheduler());
        checkIn.start(observer);
    }
}
