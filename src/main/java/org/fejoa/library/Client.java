/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library;

import org.fejoa.chunkstore.HashValue;
import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.crypto.CryptoSettings;
import org.fejoa.library.database.JGitInterface;
import org.fejoa.library.command.*;
import org.fejoa.library.database.StorageDir;
import org.fejoa.library.remote.*;
import org.fejoa.library.support.Task;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Collections;


public class Client {
    final private FejoaContext context;
    private ConnectionManager connectionManager;
    private UserData userData;
    private UserDataConfig config;
    private SyncManager syncManager;
    private OutgoingQueueManager outgoingQueueManager;
    private IncomingCommandManager incomingCommandManager;

    private IncomingContactRequestHandler contactRequestHandler = new IncomingContactRequestHandler(this, null);

    public Client(String home) {
        this.context = new FejoaContext(home);
        this.connectionManager = new ConnectionManager();
    }

    public void create(String userName, String server, String password) throws IOException, CryptoException {
        context.registerRootPassword(userName, server, password);
        userData = UserData.create(context, password);
        config = UserDataConfig.create(context, userData, "org.fejoa.client");
        Remote remoteRemote = new Remote(userName, server);
        userData.getRemoteStore().add(remoteRemote);
        //userData.getRemoteStore().setDefault(remoteRemote);
        userData.setGateway(remoteRemote);
    }

    public void open(UserDataSettings config, String password) throws IOException, CryptoException, JSONException {
        userData = UserData.open(context, config, password);

        Remote defaultRemote = userData.getRemoteStore().getDefault();
        context.registerRootPassword(defaultRemote.getUser(), defaultRemote.getServer(), password);
    }

    public void commit() throws IOException {
        userData.commit();
    }

    public FejoaContext getContext() {
        return context;
    }

    public UserData getUserData() {
        return userData;
    }

    public UserDataConfig getUserDataConfig() {
        return config;
    }

    public ConnectionManager getConnectionManager() {
        return connectionManager;
    }

    public void createAccount(String userName, String password, String server,
                              Task.IObserver<Void, RemoteJob.Result> observer) throws IOException {
        connectionManager.submit(new CreateAccountJob(userName, password, userData.getSettings()),
                new ConnectionManager.ConnectionInfo(userName, server),
                new ConnectionManager.AuthInfo(),
                observer);
    }

    public void createAccount(String userName, String password, UserData userData, String server,
                              Task.IObserver<Void, RemoteJob.Result> observer) throws IOException {
        connectionManager.submit(new CreateAccountJob(userName, password, userData.getSettings()),
                new ConnectionManager.ConnectionInfo(userName, server),
                new ConnectionManager.AuthInfo(),
                observer);
    }

    public void startSyncing(Task.IObserver<TaskUpdate, Void> observer) throws IOException {
        Remote defaultRemote = getUserData().getGateway();
        syncManager = new SyncManager(context, userData, getConnectionManager(), defaultRemote);
        syncManager.startWatching(getUserData().getBranchList().getEntries(), observer);
    }

    public void stopSyncing() {
        if (syncManager == null)
            return;
        syncManager.stopWatching();
        syncManager = null;
    }

    public void startCommandManagers(Task.IObserver<TaskUpdate, Void> outgoingCommandObserver)
            throws IOException, CryptoException {
        outgoingQueueManager = new OutgoingQueueManager(userData.getOutgoingCommandQueue(), connectionManager);
        outgoingQueueManager.start(outgoingCommandObserver);

        incomingCommandManager = new IncomingCommandManager(config);
        incomingCommandManager.start();

        contactRequestHandler.start();
    }

    public IncomingCommandManager getIncomingCommandManager() {
        return incomingCommandManager;
    }

    public void grantAccess(String branch, int rights, ContactPublic contact) throws CryptoException, JSONException,
            IOException {
        // create and add new access token
        BranchAccessRight accessRight = new BranchAccessRight(BranchAccessRight.CONTACT_ACCESS);
        accessRight.addBranchAccess(branch, rights);
        AccessToken accessToken = AccessToken.create(context);
        accessToken.setAccessEntry(accessRight.toJson().toString());
        AccessStore accessStore = userData.getAccessStore();
        accessStore.addAccessToken(accessToken);

        // todo: record in ContactPublic that we share this branch
        BranchInfo branchInfo = userData.getBranchList().get(branch);
        // send command to contact
        AccessCommand accessCommand = new AccessCommand(context, userData.getMyself(), contact,
                branchInfo, userData.getKeyData(branchInfo), accessToken);
        accessStore.commit();
        OutgoingCommandQueue queue =userData.getOutgoingCommandQueue();
        queue.post(accessCommand, contact.getRemotes().getDefault(), true);
    }

    // Requires to be root user. TODO: implement peek for contact branches?
    public void peekRemoteStatus(String branchId, Task.IObserver<Void, WatchJob.Result> observer) throws IOException {
        BranchInfo branch = getUserData().getBranchList().get(branchId);
        Remote remote = userData.getGateway();
        connectionManager.submit(new WatchJob(context, remote.getUser(), Collections.singletonList(branch), true),
                new ConnectionManager.ConnectionInfo(remote.getUser(), remote.getServer()),
                context.getRootAuthInfo(remote),
                observer);
    }

    public void pullContactBranch(String user, String server, ContactBranch contactBranch,
                                  final Task.IObserver<Void, String> observer)
            throws IOException, CryptoException {
        if ((contactBranch.getAccessToken().getAccessRights().getEntries().get(0).getRights()
                & BranchAccessRight.PULL) == 0)
            throw new IOException("missing rights!");

        final StorageDir contactBranchDir = getContext().getStorage(contactBranch.getBranch(),
                contactBranch.getBranchKey(), userData.getCommitSignature());

        SyncManager.pull(getConnectionManager(), contactBranchDir,
                new ConnectionManager.ConnectionInfo(user, server),
                new ConnectionManager.AuthInfo(user, contactBranch.getAccessToken()), observer);
    }

    public void migrate(String newUserName, String newServer) {

    }
}
