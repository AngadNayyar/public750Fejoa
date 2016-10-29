/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.messaging;


import org.fejoa.library.*;
import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.database.IOStorageDir;
import org.fejoa.library.database.StorageDir;
import org.json.JSONException;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

class MessageBranchEntry implements IStorageDirBundle {
    private String branch;
    private String privateBranch;

    public MessageBranchEntry() {
    }

    public MessageBranchEntry(BranchInfo branchInfo) {
        this.branch = branchInfo.getBranch();
    }

    public String getBranch() {
        return branch;
    }

    public String getId() {
        return getBranch();
    }

    @Override
    public void write(IOStorageDir dir) throws IOException, CryptoException {
        dir.writeString(Constants.BRANCH_KEY, branch);
    }

    @Override
    public void read(IOStorageDir dir) throws IOException, CryptoException {
        branch = dir.readString(Constants.BRANCH_KEY);
    }

    public MessageBranch getMessageBranch(UserData userData) throws IOException, CryptoException {
        BranchInfo branchInfo = userData.findBranchInfo(getBranch(), Messenger.MESSENGER_CONTEXT);
        StorageDir storageDir = userData.getStorageDir(branchInfo);
        return MessageBranch.open(storageDir, userData);
    }
}

public class Messenger {
    final static public String MESSENGER_CONTEXT = "org.fejoa.messenger";

    final private Client client;
    final private StorageDirList<MessageBranchEntry> branchList;

    public Messenger(Client client) {
        this.client = client;

        AppContext appContext = client.getUserData().getConfigStore().getAppContext(MESSENGER_CONTEXT);
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
    }

    public MessageBranch createMessageBranch(List<ContactPublic> participants)
            throws IOException, JSONException, CryptoException {
        UserData userData = client.getUserData();
        BranchInfo branchInfo = userData.createNewEncryptedStorage(MESSENGER_CONTEXT, "Message branch");
        Remote remote = userData.getGateway();
        branchInfo.addLocation(remote.getId(), userData.getContext().getRootAuthInfo(remote));
        userData.addBranch(branchInfo);

        MessageBranch messageBranch = MessageBranch.create(userData, participants);
        messageBranch.setStorageDir(userData.getStorageDir(branchInfo));

        branchList.add(new MessageBranchEntry(branchInfo));

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
}
