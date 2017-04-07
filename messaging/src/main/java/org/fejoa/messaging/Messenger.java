/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.messaging;

import org.fejoa.library.*;
import org.fejoa.library.command.AccessCommandHandler;
import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.database.IOStorageDir;
import org.json.JSONException;

import java.io.IOException;
import java.util.Collection;
import java.util.List;


public class Messenger {
    final static public String MESSENGER_CONTEXT = "org.fejoa.messenger";

    final private Client client;
    final private StorageDirList<MessageBranchEntry> branchList;
    final private AppContext appContext;

    public Messenger(Client client) {
        this.client = client;

        this.appContext = client.getUserData().getConfigStore().getAppContext(MESSENGER_CONTEXT);
        branchList = new StorageDirList<>(appContext.getStorageDir(),
                new StorageDirList.AbstractEntryIO<MessageBranchEntry>() {
                    @Override
                    public String getId(MessageBranchEntry entry) {
                        return entry.getId();
                    }

                    @Override
                    public MessageBranchEntry read(IOStorageDir dir) throws IOException, CryptoException {
                        MessageBranchEntry entry = new MessageBranchEntry();
                        entry.read(dir);
                        return entry;
                    }
                });

        appContext.addAccessGrantedHandler(client.getIncomingCommandManager(), new AccessCommandHandler.IContextHandler() {
            @Override
            public boolean handle(String senderId, BranchInfo branchInfo) throws Exception {
                branchList.add(new MessageBranchEntry(branchInfo, senderId));
                return true;
            }
        });
    }

    public MessageBranch createMessageBranch(List<ContactPublic> participants)
            throws IOException, JSONException, CryptoException {
        UserData userData = client.getUserData();
        BranchInfo branchInfo = userData.createNewEncryptedStorage(MESSENGER_CONTEXT, "Message branch");
        Remote remote = userData.getGateway();
        branchInfo.addLocation(remote.getId(), userData.getContext().getRootAuthInfo(remote));

        MessageBranch messageBranch = MessageBranch.create(userData, branchInfo, participants);

        branchList.add(new MessageBranchEntry(branchInfo, client.getUserData().getMyself().getId()));

        return messageBranch;
    }

    public void publishMessageBranch(MessageBranch messageBranch) throws IOException, JSONException, CryptoException {
        UserData userData = client.getUserData();
        BranchInfo branchInfo = userData.getBranchList().get(messageBranch.getId(), MESSENGER_CONTEXT);
        // grant access to participants
        Collection<ContactPublic> participants = messageBranch.getParticipants();
        for (ContactPublic contactPublic : participants)
            client.grantAccess(branchInfo, BranchAccessRight.PULL_PUSH, contactPublic);
    }

    public StorageDirList<MessageBranchEntry> getBranchList() {
        return branchList;
    }

    public AppContext getAppContext() {
        return appContext;
    }
}
