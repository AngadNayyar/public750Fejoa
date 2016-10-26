/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library;

import org.fejoa.library.command.MigrationCommand;
import org.fejoa.library.command.OutgoingCommandQueue;
import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.support.Task;
import org.fejoa.library.remote.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


public class MigrationManager {
    final private Client client;

    public MigrationManager(Client client) {
        this.client = client;
    }

    public void migrate(final Remote updatedRemote, String password,
                        final Task.IObserver<Void, RemoteJob.Result> observer)
            throws Exception {
        client.getContext().registerRootPassword(updatedRemote.getUser(), updatedRemote.getServer(), password);

        // create access token for the new server
        final AccessToken accessToken = AccessToken.create(client.getContext());
        BranchAccessRight accessRight = new BranchAccessRight(BranchAccessRight.MIGRATION_ACCESS);
        final List<BranchInfo> branchesToCopy = new ArrayList<>();
        for (BranchInfo branchInfo : client.getUserData().getBranchList().getEntries()) {
            accessRight.addBranchAccess(branchInfo.getBranch(), BranchAccessRight.PULL_CHUNK_STORE);
            branchesToCopy.add(branchInfo);
        }
        if (branchesToCopy.size() == 0)
            throw new Exception("no branches to migrate");
        accessToken.setAccessEntry(accessRight.toJson().toString());
        final AccessTokenContact accessTokenContact = accessToken.toContactToken();

        final Remote currentRemote = client.getUserData().getGateway();
        client.getConnectionManager().submit(new StartMigrationJob(currentRemote.getUser(),
                        accessToken.toServerToken()),
                currentRemote,
                client.getContext().getRootAuthInfo(currentRemote.getUser(), currentRemote.getServer()),
                new Task.IObserver<Void, RemoteJob.Result>() {
                    @Override
                    public void onProgress(Void update) {

                    }

                    @Override
                    public void onResult(RemoteJob.Result result) {
                        if (result.status != Errors.DONE) {
                            observer.onException(new Exception(result.message));
                            return;
                        }
                        copyBranches(currentRemote, branchesToCopy, updatedRemote, accessTokenContact, observer);
                    }

                    @Override
                    public void onException(Exception exception) {
                        observer.onException(exception);
                    }
                });
    }

    private void copyBranches(final Remote currentRemote, final List<BranchInfo> branchesToCopy,
                              final Remote updatedRemote,
                              final AccessTokenContact accessTokenContact,
                              final Task.IObserver<Void, RemoteJob.Result> observer) {
        client.getConnectionManager().submit(
                new RemotePullJob(updatedRemote.getUser(), accessTokenContact, branchesToCopy.get(0).getBranch(),
                        currentRemote.getUser(),
                        currentRemote.getServer()),
                updatedRemote,
                client.getContext().getRootAuthInfo(updatedRemote.getUser(), updatedRemote.getServer()),
                new Task.IObserver<Void, RemoteJob.Result>() {
                    @Override
                    public void onProgress(Void update) {

                    }

                    @Override
                    public void onResult(RemoteJob.Result result) {
                        if (result.status != Errors.DONE) {
                            observer.onException(new Exception(result.message));
                            return;
                        }
                        branchesToCopy.remove(0);
                        if (branchesToCopy.size() > 0)
                            copyBranches(currentRemote, branchesToCopy, updatedRemote, accessTokenContact, observer);
                        else {
                            // update own remote
                            try {
                                UserData userData = client.getUserData();
                                userData.getRemoteStore().add(updatedRemote);
                                userData.commit();
                            } catch (Exception e) {
                                onException(e);
                            }
                            notifyContacts(updatedRemote, observer);
                        }
                    }

                    @Override
                    public void onException(Exception exception) {
                        observer.onException(exception);
                    }
                });
    }

    private void notifyContacts(final Remote updatedRemote,
                                final Task.IObserver<Void, RemoteJob.Result> observer) {
        ContactPrivate myself = client.getUserData().getMyself();
        for (ContactPublic contactPublic : client.getUserData().getContactStore().getContactList().getEntries()) {
            try {
                OutgoingCommandQueue queue = client.getUserData().getOutgoingCommandQueue();
                queue.post(new MigrationCommand(client.getContext(), updatedRemote, myself, contactPublic),
                        contactPublic.getRemotes().getDefault(), true);
            } catch (Exception e) {
                observer.onException(e);
            }
        }
        observer.onResult(new RemoteJob.Result(Errors.DONE, "migration done"));
    }
}
