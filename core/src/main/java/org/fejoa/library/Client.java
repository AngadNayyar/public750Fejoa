/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library;

import org.fejoa.library.command.*;
import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.crypto.CryptoHelper;
import org.fejoa.library.database.DatabaseDiff;
import org.fejoa.library.database.StorageDir;
import org.fejoa.library.remote.*;
import org.fejoa.library.support.Task;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;


public class Client {
    final static private String USER_SETTINGS_FILE = "user.settings";
    final private FejoaContext context;
    private ConnectionManager connectionManager;
    private UserData userData;
    private SyncManager syncManager;
    private Task.IObserver<TaskUpdate, Void> syncObserver = null;
    private OutgoingQueueManager outgoingQueueManager;
    private IncomingCommandManager incomingCommandManager;

    final private StorageDir.IListener userDataStorageListener = new StorageDir.IListener() {
        @Override
        public void onTipChanged(DatabaseDiff diff, String base, String tip) {
            if (syncObserver != null) {
                try {
                    startSyncing(syncObserver);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    };

    private Client(File homeDir) {
        this.context = new FejoaContext(homeDir);
        this.connectionManager = new ConnectionManager();
    }

    static public Client create(File homeDir, String userName, String server, String password)
            throws IOException, CryptoException {
        Client client = new Client(homeDir);
        client.context.registerRootPassword(userName, server, password);
        client.userData = UserData.create(client.context, password);
        Remote remoteRemote = new Remote(userName, server);
        client.userData.getRemoteStore().add(remoteRemote);
        //userData.getRemoteStore().setDefault(remoteRemote);
        client.userData.setGateway(remoteRemote);
        // connect branches
        for (BranchInfo branchInfo : client.getUserData().getBranchList().getEntries(UserData.USER_DATA_CONTEXT, true))
            branchInfo.addLocation(remoteRemote.getId(), new AuthInfo.Password(password));

        client.loadCommandManagers();
        client.userData.getStorageDir().addListener(client.userDataStorageListener);

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

        Remote gateway = client.userData.getGateway();
        client.context.registerRootPassword(gateway.getUser(), gateway.getServer(), password);

        client.loadCommandManagers();
        client.userData.getStorageDir().addListener(client.userDataStorageListener);

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

    public void createAccount(Remote remote, String password,
                              Task.IObserver<Void, RemoteJob.Result> observer) throws IOException, CryptoException {
        connectionManager.submit(new CreateAccountJob(remote.getUser(), password, userData.getSettings()),
                remote, new AuthInfo.Plain(), observer);
    }

    public void createAccount(Remote remote, String password, UserData userData,
                              Task.IObserver<Void, RemoteJob.Result> observer) throws IOException, CryptoException {
        connectionManager.submit(new CreateAccountJob(remote.getUser(), password, userData.getSettings()),
                remote, new AuthInfo.Plain(), observer);
    }

    public void startSyncing(Task.IObserver<TaskUpdate, Void> observer) throws IOException, CryptoException {
        if (syncManager != null)
            stopSyncing();
        this.syncObserver = observer;
        Remote defaultRemote = getUserData().getGateway();
        syncManager = new SyncManager(context, userData, getConnectionManager(), defaultRemote);
        List<BranchInfo.Location> locations = new ArrayList<>();
        for (BranchInfo branchInfo : getUserData().getBranchList().getEntries()) {
            for (BranchInfo.Location location : branchInfo.getLocationEntries())
                locations.add(location);
        }
        syncManager.startWatching(locations, observer);
    }

    public void stopSyncing() {
        if (syncManager == null)
            return;
        syncManager.stop();
        syncManager = null;
        syncObserver = null;
    }

    private void loadCommandManagers() throws IOException, CryptoException {
        outgoingQueueManager = new OutgoingQueueManager(userData.getOutgoingCommandQueue(), connectionManager);
        incomingCommandManager = new IncomingCommandManager(userData);
    }

    public void startCommandManagers(Task.IObserver<TaskUpdate, Void> outgoingCommandObserver)
            throws IOException, CryptoException {
        outgoingQueueManager.start(outgoingCommandObserver);
        incomingCommandManager.start();
    }

    public IncomingCommandManager getIncomingCommandManager() {
        return incomingCommandManager;
    }

    public void grantAccess(String branch, String branchContext, int rights, ContactPublic contact)
            throws CryptoException, JSONException,
            IOException {
        // create and add new access token
        BranchAccessRight accessRight = new BranchAccessRight(BranchAccessRight.CONTACT_ACCESS);
        accessRight.addBranchAccess(branch, rights);
        AccessToken accessToken = AccessToken.create(context);
        accessToken.setAccessEntry(accessRight.toJson().toString());
        AccessStore accessStore = userData.getAccessStore();
        accessStore.addAccessToken(accessToken.toServerToken());

        // record with whom we share the branch
        BranchInfo branchInfo = userData.getBranchList().get(branch, branchContext);
        branchInfo.getContactAccessList().add(contact, accessToken);
        userData.commit();

        // send command to contact
        AccessCommand accessCommand = new AccessCommand(context, userData.getMyself(), contact, userData.getGateway(),
                branchInfo, branchContext, userData.getKeyData(branchInfo), accessToken);
        accessStore.commit();
        OutgoingCommandQueue queue = userData.getOutgoingCommandQueue();
        queue.post(accessCommand, contact.getRemotes().getDefault(), true);
    }

    // Requires to be root user. TODO: implement peek for contact branches?
    public void peekRemoteStatus(BranchInfo.Location location, Task.IObserver<Void, WatchJob.Result> observer)
            throws IOException, CryptoException {
        Remote remote = userData.getGateway();
        AuthInfo authInfo = location.getAuthInfo(context);
        connectionManager.submit(new WatchJob(context, Collections.singletonList(location), true), remote, authInfo,
                observer);
    }

    public void pullContactBranch(Remote remote, BranchInfo.Location location,
                                  final Task.IObserver<Void, ChunkStorePullJob.Result> observer)
            throws IOException, CryptoException {
        AuthInfo authInfo = location.getAuthInfo(context);
        if (!(authInfo instanceof AuthInfo.Token))
            throw new IOException("AuthInfo.Token expected");
        AccessTokenContact token = ((AuthInfo.Token) authInfo).getToken();
        if ((token.getAccessRights().getEntries().get(0).getRights()
                & BranchAccessRight.PULL) == 0)
            throw new IOException("missing rights!");

        BranchInfo branchInfo = location.getBranchInfo();
        final StorageDir contactBranchDir = getContext().getStorage(branchInfo.getBranch(),
                branchInfo.getCryptoKey(), userData.getCommitSignature());

        SyncManager.pull(getConnectionManager(), contactBranchDir,
                remote, authInfo, observer);
    }

    public void migrate(String newUserName, String newServer) {

    }
}