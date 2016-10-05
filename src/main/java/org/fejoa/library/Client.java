/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library;

import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.command.*;
import org.fejoa.library.crypto.CryptoHelper;
import org.fejoa.library.database.StorageDir;
import org.fejoa.library.remote.*;
import org.fejoa.library.support.Task;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.util.Collections;
import java.util.Scanner;


public class Client {
    final static private String USER_SETTINGS_FILE = "user.settings";
    final private FejoaContext context;
    private ConnectionManager connectionManager;
    private UserData userData;
    private UserDataConfig config;
    private SyncManager syncManager;
    private OutgoingQueueManager outgoingQueueManager;
    private IncomingCommandManager incomingCommandManager;

    private Client(File homeDir) {
        this.context = new FejoaContext(homeDir);
        this.connectionManager = new ConnectionManager();
    }

    static public Client create(File homeDir, String userName, String server, String password)
            throws IOException, CryptoException {
        Client client = new Client(homeDir);
        client.context.registerRootPassword(userName, server, password);
        client.userData = UserData.create(client.context, password);
        client.config = UserDataConfig.create(client.context, client.userData, "org.fejoa.client");
        Remote remoteRemote = new Remote(userName, server);
        client.userData.getRemoteStore().add(remoteRemote);
        //userData.getRemoteStore().setDefault(remoteRemote);
        client.userData.setGateway(remoteRemote);

        UserDataSettings userDataSettings = client.userData.getSettings();
        Writer writer = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(client.getUserDataSettingsFile())));
        writer.write(userDataSettings.toJson().toString());
        writer.flush();
        writer.close();

        return client;
    }

    static public Client open(File homeDir, String password) throws IOException, CryptoException, JSONException {
        Client client = new Client(homeDir);
        client.userData = UserData.open(client.context, client.readUserDataSettings(), password);
        StorageDir userConfigDir = client.userData.getConfigStore().getConfigDir("org.fejoa.client");
        client.config = UserDataConfig.open(client.context, userConfigDir, client.userData);

        Remote gateway = client.userData.getGateway();
        client.context.registerRootPassword(gateway.getUser(), gateway.getServer(), password);

        return client;
    }

    static public boolean exist(File homeDir) {
        return getUserDataSettingsFile(homeDir).exists();
    }

    static private File getUserDataSettingsFile(File homeDir) {
        return new File(homeDir, USER_SETTINGS_FILE);
    }

    private File getUserDataSettingsFile() {
        return getUserDataSettingsFile(context.getHomeDir());
    }

    private UserDataSettings readUserDataSettings() throws FileNotFoundException, JSONException {
        String content = new Scanner(getUserDataSettingsFile()).useDelimiter("\\Z").next();
        return new UserDataSettings(new JSONObject(content));
    }

    public void commit() throws IOException {
        userData.commit(true);
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

    public void contactRequest(String user, String server) throws Exception {
        ContactStore contactStore = userData.getContactStore();

        ContactPublic requestedContact = new ContactPublic(context, null);
        requestedContact.setId(CryptoHelper.sha1HashHex(user + "@" + server));
        requestedContact.getRemotes().add(new Remote(user, server), true);
        contactStore.getRequestedContacts().add(requestedContact);
        contactStore.commit();

        userData.getOutgoingCommandQueue().post(ContactRequestCommand.makeInitialRequest(
                userData.getMyself(),
                userData.getGateway()), user, server);
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
                                  final Task.IObserver<Void, ChunkStorePullJob.Result> observer)
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
