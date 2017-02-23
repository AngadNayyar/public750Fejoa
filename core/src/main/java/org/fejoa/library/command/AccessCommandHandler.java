/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.command;

import org.fejoa.library.*;
import org.fejoa.library.remote.AuthInfo;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

import static org.fejoa.library.command.AccessCommand.BRANCH_CONTEXT_KEY;


public class AccessCommandHandler extends EnvelopeCommandHandler {
    public interface IListener extends IncomingCommandManager.IListener {
        void onAccessGranted(String contactId, AccessTokenContact accessTokenContact);
    }

    public interface IContextHandler {
        boolean handle(String senderId, BranchInfo branchInfo) throws Exception;
    }

    final private ContactStore contactStore;
    private IListener listener;
    final private Map<String, IContextHandler> contextHandlerList = new HashMap<>();

    public AccessCommandHandler(UserData userData) {
        super(userData, AccessCommand.COMMAND_NAME);
        this.contactStore = userData.getContactStore();
    }

    public void addContextHandler(String appContext, IContextHandler handler) {
        contextHandlerList.put(appContext, handler);
    }

    public void setListener(IListener listener) {
        this.listener = listener;
    }

    @Override
    public IListener getListener() {
        return listener;
    }

    @Override
    protected boolean handle(JSONObject command, IncomingCommandManager.HandlerResponse response) throws Exception {
        if (!command.getString(Constants.COMMAND_NAME_KEY).equals(AccessCommand.COMMAND_NAME))
            return false;

        String branchContext = command.getString(BRANCH_CONTEXT_KEY);
        for (Map.Entry<String, IContextHandler> entry : contextHandlerList.entrySet()) {
            if (!entry.getKey().equals(branchContext))
                continue;
            String remoteId = command.getString(Constants.REMOTE_ID_KEY);

            String branch = command.getString(Constants.BRANCH_KEY);
            String description = command.getString(Constants.BRANCH_DESCRIPTION_KEY);
            SymmetricKeyData keyData = null;
            if (command.has(AccessCommand.BRANCH_KEY_KEY)) {
                keyData = new SymmetricKeyData();
                keyData.fromJson(command.getJSONObject(AccessCommand.BRANCH_KEY_KEY));
            }
            JSONObject accessToken = command.getJSONObject(AccessCommand.TOKEN_KEY);
            AccessTokenContact accessTokenContact = new AccessTokenContact(context, accessToken);

            BranchInfo branchInfo = BranchInfo.create(branch, description, branchContext);
            if (keyData != null)
                branchInfo.setCryptoKey(keyData);
            branchInfo.addLocation(remoteId, new AuthInfo.Token(accessTokenContact));

            String senderId = command.getString(Constants.SENDER_ID_KEY);
            if (entry.getValue().handle(senderId, branchInfo)) {

                ContactPublic sender = contactStore.getContactList().get(senderId);
                BranchList branchList = sender.getBranchList();
                branchList.add(branchInfo);
                contactStore.commit();

                if (listener != null)
                    listener.onAccessGranted(senderId, accessTokenContact);

                response.setHandled();
                return true;
            }
        }
        return false;
    }
}
