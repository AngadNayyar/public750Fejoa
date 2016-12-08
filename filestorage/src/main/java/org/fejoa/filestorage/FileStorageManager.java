/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.filestorage;

import org.fejoa.chunkstore.HashValue;
import org.fejoa.library.*;
import org.fejoa.library.command.AccessCommandHandler;
import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.crypto.CryptoHelper;
import org.fejoa.library.database.*;
import org.fejoa.library.remote.TaskUpdate;
import org.fejoa.library.support.StorageLib;
import org.fejoa.library.support.Task;
import org.json.JSONException;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.ExecutionException;


public class FileStorageManager {
    final static public String STORAGE_CONTEXT = "org.fejoa.filestorage";
    final private Client client;
    final private ContactStorageList storageList;
    final private AppContext appContext;

    public FileStorageManager(final Client client) {
        this.client = client;
        appContext = client.getUserData().getConfigStore().getAppContext(STORAGE_CONTEXT);
        storageList = new ContactStorageList(client.getUserData());
        storageList.setTo(appContext.getStorageDir());
    }

    public void addAccessGrantedHandler(AccessCommandHandler.IContextHandler handler) {
        appContext.addAccessGrantedHandler(client.getIncomingCommandManager(), handler);
    }

    public ContactStorageList getContactFileStorageList() {
        return storageList;
    }

    public ContactStorageList.ContactStorage getOwnFileStorage() throws IOException, CryptoException {
        return getContactFileStorageList().get(client.getUserData().getMyself().getId());
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

        // test


        // config entry
        IContactPublic myself = userData.getMyself();
        addContactStorage(myself, branchInfo, file.getPath());
    }

    public String getProfile() {
        return "my_device";
    }

    public void addContactStorage(IContactPublic contact, BranchInfo branchInfo, String checkoutPath)
            throws IOException, CryptoException {
        String branch = branchInfo.getBranch();
        ContactStorageList.ContactStorage contactStorage = storageList.get(contact.getId());
        ContactStorageList.Store store = contactStorage.getStores().get(branch);
        ContactStorageList.CheckOutProfiles checkOutProfiles;
        try {
            checkOutProfiles = store.getCheckOutProfiles().get();
        } catch (Exception e) {
            throw new IOException(e);
        }
        ContactStorageList.CheckOutList checkOutList = checkOutProfiles.ensureCheckOut(getProfile());
        ContactStorageList.CheckOutEntry checkOutEntry = new ContactStorageList.CheckOutEntry();
        checkOutList.getCheckOutEntries().add(checkOutEntry);
        checkOutEntry.setCheckOutPath(checkoutPath);
        Collection<BranchInfo.Location> locations = branchInfo.getLocationEntries();
        for (BranchInfo.Location location : locations)
            checkOutEntry.getRemoteIds().add(location.getRemoteId());

        try {
            store.setCheckOutProfile(checkOutProfiles);
        } catch (JSONException e) {
            throw new IOException(e);
        }

        storageList.flush();

        client.getUserData().commit(true);
    }

    public void grantAccess(String branch, int accessRights, ContactPublic contactPublic)
            throws IOException, JSONException, CryptoException {
        client.grantAccess(branch, STORAGE_CONTEXT, accessRights, contactPublic);
    }

    public void sync(ContactStorageList.CheckOutEntry entry, BranchInfo.Location location, boolean overWriteLocalChanges,
                     Task.IObserver<CheckoutDir.Update, CheckoutDir.Result> observer)
            throws Exception {
        String path = entry.getCheckOutPath();
        if (path.equals("")) {
            String branchName = location.getBranchInfo().getBranch();
            path = StorageLib.appendDir(client.getContext().getHomeDir().getPath(), branchName);
        }
        File destination = new File(path);
        File indexDir = new File(destination, ".index");
        Index index = new Index(client.getContext(), indexDir, location.getBranchInfo().getBranch());
        HashValue rev = index.getRev();
        UserData userData = client.getUserData();
        BranchInfo branchInfo = location.getBranchInfo();
        final StorageDir branchStorage = userData.getStorageDir(branchInfo, rev);
        // TODO better check if we need to check in, i.e. if tip is zero try to pull first!
        if (!rev.isZero() || branchStorage.getTip().isZero()) {
            // try to check in first
            checkIn(destination, branchStorage, index, observer);
        }
        sync(branchStorage, location, destination, index, overWriteLocalChanges, observer);
    }

    private void checkIn(File destination, StorageDir branchStorage, Index index,
                         Task.IObserver<CheckoutDir.Update, CheckoutDir.Result> observer)
            throws IOException, CryptoException {
        CheckoutDir checkoutDir = new CheckoutDir(branchStorage, index, destination);
        Task<CheckoutDir.Update, CheckoutDir.Result> checkIn = checkoutDir.checkIn();
        checkIn.setStartScheduler(new Task.CurrentThreadScheduler());
        checkIn.start(observer);
    }

    private void sync(StorageDir branchStorage, final BranchInfo.Location location, final File destination, final Index index,
                      final boolean overWriteLocalChanges,
                      final Task.IObserver<CheckoutDir.Update, CheckoutDir.Result> observer)
            throws IOException, CryptoException {
        client.sync(branchStorage, location.getRemote(), location.getAuthInfo(client.getContext()),
                new Task.IObserver<TaskUpdate, String>() {
            @Override
            public void onProgress(TaskUpdate taskUpdate) {

            }

            @Override
            public void onResult(String s) {
                try {
                    CheckoutDir checkoutDir = new CheckoutDir(
                            client.getUserData().getStorageDir(location.getBranchInfo()), index, destination);
                    checkoutDir.checkOut(overWriteLocalChanges).start(observer);
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (CryptoException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onException(Exception exception) {
                observer.onException(exception);
            }
        });
    }
}
