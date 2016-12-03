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
import org.fejoa.library.database.IOStorageDir;
import org.fejoa.library.database.MovableStorageList;
import org.fejoa.library.database.StorageDir;
import org.fejoa.library.remote.TaskUpdate;
import org.fejoa.library.support.StorageLib;
import org.fejoa.library.support.Task;
import org.json.JSONException;

import java.io.File;
import java.io.IOException;


public class FileStorageManager {
    final static public String STORAGE_CONTEXT = "org.fejoa.filestorage";
    final private Client client;
    final private MovableStorageList<ContactFileStorage> storageList;
    final private AppContext appContext;

    public FileStorageManager(final Client client) {
        this.client = client;
        appContext = client.getUserData().getConfigStore().getAppContext(STORAGE_CONTEXT);
        storageList = new MovableStorageList<ContactFileStorage>(appContext.getStorageDir()) {
            @Override
            protected ContactFileStorage readObject(IOStorageDir storageDir) throws IOException, CryptoException {
                return ContactFileStorage.open(storageDir, client.getUserData());
            }
        };
    }

    public void addAccessGrantedHandler(AccessCommandHandler.IContextHandler handler) {
        appContext.addAccessGrantedHandler(client.getIncomingCommandManager(), handler);
    }

    public MovableStorageList<ContactFileStorage> getContactFileStorageList() {
        return storageList;
    }

    public ContactFileStorage getOwnFileStorage() throws IOException, CryptoException {
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

        // config entry
        IContactPublic myself = userData.getMyself();
        addContactStorage(myself, branchInfo.getBranch(), file.getPath());
    }

    public void addContactStorage(IContactPublic contact, String branch, String checkoutPath) throws IOException, CryptoException {
        ContactFileStorage contactFileStorage = storageList.get(contact.getId());
        FileStorageEntry fileStorageEntry = new FileStorageEntry(client.getContext());
        contactFileStorage.getStorageList().add(fileStorageEntry.getId(), fileStorageEntry);
        fileStorageEntry.setTo(checkoutPath, branch);
        client.getUserData().commit(true);
    }

    public void grantAccess(FileStorageEntry entry, int accessRights, ContactPublic contactPublic)
            throws IOException, JSONException, CryptoException {
        client.grantAccess(entry.getBranch(), STORAGE_CONTEXT, accessRights, contactPublic);
    }

    public void sync(FileStorageEntry entry, BranchInfo.Location location, boolean overWriteLocalChanges,
                     Task.IObserver<CheckoutDir.Update, CheckoutDir.Result> observer)
            throws Exception {
        String path = entry.getPath().get();
        if (path.equals("")) {
            String branchName = location.getBranchInfo().getBranch();
            path = StorageLib.appendDir(client.getContext().getHomeDir().getPath(), branchName);
        }
        File destination = new File(path);
        File indexDir = new File(destination, ".index");
        Index index = new Index(client.getContext(), indexDir, entry.getBranch());
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
