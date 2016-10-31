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
import org.fejoa.library.crypto.CryptoHelper;
import org.fejoa.library.database.IOStorageDir;
import org.fejoa.library.database.MovableStorageContainer;
import org.fejoa.library.database.MovableStorageList;
import org.fejoa.library.database.StorageDir;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;


public class MessageBranch extends MovableStorageContainer {
    final static private String PARTICIPANTS_KEY = "participants";
    final static private String MESSAGES_KEY = "messages";

    final private String id;
    final private UserData userData;
    final private MovableStorageList<Message> messages;

    public static MessageBranch create(UserData userData, BranchInfo branchInfo, Collection<ContactPublic> participants)
            throws IOException, JSONException, CryptoException {
        MessageBranch branch = new MessageBranch(null, branchInfo.getBranch(), userData);
        branch.setParticipants(participants);
        branch.setStorageDir(userData.getStorageDir(branchInfo));
        return branch;
    }

    public static MessageBranch open(StorageDir storageDir, UserData userData) {
        return new MessageBranch(storageDir, storageDir.getBranch(), userData);
    }

    public StorageDir getStorageDir() {
        return (StorageDir)storageDir;
    }

    private MessageBranch(IOStorageDir storageDir, String id, UserData userData) {
        super(storageDir);

        this.id = id;
        this.userData = userData;

        messages = new MovableStorageList<Message>(new IOStorageDir(this.storageDir, MESSAGES_KEY)) {
            @Override
            protected Message readObject(IOStorageDir storageDir) throws IOException, CryptoException {
                return Message.open(storageDir);
            }
        };
        attach(messages, MESSAGES_KEY);
    }

    private void setParticipants(Collection<ContactPublic> participants) throws JSONException, IOException {
        JSONObject jsonObject = new JSONObject();
        JSONArray contactArray = new JSONArray();
        ContactPrivate myself = userData.getMyself();
        contactArray.put(myself.getId());
        for (ContactPublic contactPublic : participants) {
            contactArray.put(contactPublic.getId());
        }
        jsonObject.put(PARTICIPANTS_KEY, contactArray);

        storageDir.writeString(PARTICIPANTS_KEY, jsonObject.toString());
    }

    public Collection<ContactPublic> getParticipants() throws IOException, JSONException {
        JSONObject jsonObject = new JSONObject(storageDir.readString(PARTICIPANTS_KEY));
        JSONArray contactArray = jsonObject.getJSONArray(PARTICIPANTS_KEY);
        List<ContactPublic> participants = new ArrayList<>();
        StorageDirList<ContactPublic> contactList = userData.getContactStore().getContactList();
        ContactPrivate myself = userData.getMyself();
        for (int i = 0; i < contactArray.length(); i++) {
            String contactId = contactArray.getString(i);
            if (!contactId.equals(myself.getId()))
                participants.add(contactList.get(contactId));
        }
        return participants;
    }

    public void addMessage(Message message) throws IOException, CryptoException {
        messages.add(message.getId(), message);
    }

    public MovableStorageList<Message> getMessages() {
        return messages;
    }

    public String getId() {
        return id;
    }

    public void commit() throws IOException {
        if (storageDir instanceof StorageDir)
            ((StorageDir) storageDir).commit();
    }
}
