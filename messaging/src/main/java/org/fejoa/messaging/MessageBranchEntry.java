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
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


public class MessageBranchEntry implements IStorageDirBundle {
    static final public String BRANCH_HOSTS_KEY = "hosts";

    private String branch;
    private String privateBranch;
    final private List<String> hosts = new ArrayList<>();

    public MessageBranchEntry() {
    }

    public MessageBranchEntry(BranchInfo branchInfo, String host) {
        this.branch = branchInfo.getBranch();
        this.hosts.add(host);
    }

    @Override
    public String toString() {
        return getId();
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

        JSONObject hostsObject = new JSONObject();
        JSONArray hostArray = new JSONArray();
        for (String host : hosts)
            hostArray.put(host);
        try {
            hostsObject.put(BRANCH_HOSTS_KEY, hostArray);
        } catch (JSONException e) {
            throw new RuntimeException("Should not happen");
        }
        dir.writeString(BRANCH_HOSTS_KEY, hostsObject.toString());
    }

    @Override
    public void read(IOStorageDir dir) throws IOException, CryptoException {
        branch = dir.readString(Constants.BRANCH_KEY);

        try {
            JSONObject hostsObject = new JSONObject(dir.readString(BRANCH_HOSTS_KEY));
            JSONArray hostArray = hostsObject.getJSONArray(BRANCH_HOSTS_KEY);
            for (int i = 0; i < hostArray.length(); i++)
                hosts.add(hostArray.getString(i));
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public BranchInfo getMessageBranchInfo(UserData userData) throws IOException, CryptoException {
        if (hosts.size() == 0)
            return null;
        String host = hosts.get(0);
        return userData.getBranchInfo(getBranch(), Messenger.MESSENGER_CONTEXT, host);
    }

    public MessageBranch getMessageBranch(UserData userData) throws IOException, CryptoException {
        BranchInfo branchInfo = getMessageBranchInfo(userData);
        StorageDir storageDir = userData.getStorageDir(branchInfo);
        return MessageBranch.open(storageDir, userData);
    }
}
