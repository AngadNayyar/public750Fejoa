/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.filestorage;

import org.fejoa.chunkstore.HashValue;
import org.fejoa.chunkstore.Repository;
import org.fejoa.library.*;
import org.fejoa.library.command.AccessCommandHandler;
import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.database.*;
import org.fejoa.library.remote.TaskUpdate;
import org.fejoa.library.support.StorageLib;
import org.fejoa.library.support.Task;
import org.json.JSONException;

import java.io.File;
import java.io.IOException;
import java.util.Collection;


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
        addContactStorage(store, branchInfo, checkoutPath);
    }

    public void addContactStorage(ContactStorageList.Store store, BranchInfo branchInfo, String checkoutPath)
            throws IOException, CryptoException {
        ContactStorageList.CheckoutProfiles checkoutProfiles;
        try {
            checkoutProfiles = store.getCheckOutProfiles().get();
        } catch (Exception e) {
            throw new IOException(e);
        }
        ContactStorageList.CheckoutProfile checkoutProfile = checkoutProfiles.ensureCheckout(getProfile());
        ContactStorageList.CheckoutEntry checkoutEntry = new ContactStorageList.CheckoutEntry();
        checkoutProfile.getCheckoutEntries().add(checkoutEntry);
        checkoutEntry.setCheckoutPath(checkoutPath);
        Collection<BranchInfo.Location> locations = branchInfo.getLocationEntries();
        for (BranchInfo.Location location : locations)
            checkoutEntry.getRemoteIds().add(location.getRemoteId());

        try {
            store.setCheckOutProfile(checkoutProfiles);
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

    static public File getCheckoutDir(Client client, BranchInfo branchInfo, ContactStorageList.CheckoutEntry entry)
            throws IOException, CryptoException {
        String path = entry.getCheckoutPath();
        if (path.equals("")) {
            String branchName = branchInfo.getBranch();
            path = StorageLib.appendDir(client.getContext().getHomeDir().getPath(), branchName);
        }
        return new File(path);
    }

    public void sync(ContactStorageList.CheckoutEntry entry, final BranchInfo.Location location,
                     final boolean overWriteLocalChanges,
                     final Task.IObserver<CheckoutDir.Update, CheckoutDir.Result> observer)
            throws Exception {
        final File destination = getCheckoutDir(client, location.getBranchInfo(), entry);
        File chunkStoreDir = new File(destination, ".chunkstore");
        File indexDir = new File(chunkStoreDir, ".index");
        final Index index = new Index(client.getContext(), indexDir, location.getBranchInfo().getBranch());
        final HashValue initialRev = index.getRev();
        UserData userData = client.getUserData();
        final BranchInfo branchInfo = location.getBranchInfo();
        final StorageDir branchStorage = userData.getStorageDir(chunkStoreDir, branchInfo, null);

        // 1) try to get remote changes
        // 2) check in local changes
        // 3) sync changes
        client.sync(branchStorage, location.getRemote(), location.getAuthInfo(client.getContext()),
                new Task.IObserver<TaskUpdate, String>() {
                    @Override
                    public void onProgress(TaskUpdate taskUpdate) {

                    }

                    @Override
                    public void onResult(String s) {
                        try {
                            final HashValue currentTip = branchStorage.getTip();
                            // try to check in first
                            if (!initialRev.isZero() || currentTip.isZero()) {
                                checkIn(destination, branchStorage, index, new Task.IObserver<CheckoutDir.Update,
                                        CheckoutDir.Result>() {
                                    @Override
                                    public void onProgress(CheckoutDir.Update update) {
                                        observer.onProgress(update);
                                    }

                                    @Override
                                    public void onResult(CheckoutDir.Result result) {
                                        // did we check in?
                                        try {
                                            if (!currentTip.equals(branchStorage.getTip())) {
                                                syncAndCheckout(branchStorage, location, destination, index,
                                                        overWriteLocalChanges, observer);
                                            } else {
                                                checkOut(branchStorage, index, destination, overWriteLocalChanges,
                                                        observer);
                                            }
                                        } catch (Exception e) {
                                            observer.onException(e);
                                        }

                                    }

                                    @Override
                                    public void onException(Exception exception) {
                                        observer.onException(exception);
                                    }
                                });
                            } else
                                checkOut(branchStorage, index, destination, overWriteLocalChanges, observer);
                        } catch (Exception e) {
                            observer.onException(e);
                            return;
                        }
                    }

                    @Override
                    public void onException(Exception exception) {
                        observer.onException(exception);
                    }
                });
    }

    private void checkIn(File destination, StorageDir branchStorage, Index index,
                         Task.IObserver<CheckoutDir.Update, CheckoutDir.Result> observer)
            throws IOException, CryptoException {
        CheckoutDir checkoutDir = new CheckoutDir(branchStorage, index, destination);
        Task<CheckoutDir.Update, CheckoutDir.Result> checkIn = checkoutDir.checkIn();
        checkIn.setStartScheduler(new Task.CurrentThreadScheduler());
        checkIn.start(observer);
    }

    private void checkOut(StorageDir branchStorage, Index index, File destination, boolean overWriteLocalChanges,
                          Task.IObserver<CheckoutDir.Update, CheckoutDir.Result> observer) {
        CheckoutDir checkoutDir = new CheckoutDir(branchStorage, index, destination);
        checkoutDir.checkOut(overWriteLocalChanges).start(observer);
    }

    private void syncAndCheckout(final StorageDir branchStorage, final BranchInfo.Location location, final File destination,
                      final Index index,
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
                checkOut(branchStorage, index, destination, overWriteLocalChanges, observer);
            }

            @Override
            public void onException(Exception exception) {
                observer.onException(exception);
            }
        });
    }
}
