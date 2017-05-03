/*
 * Copyright 2017.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.command;

import org.fejoa.library.*;
import org.fejoa.library.remote.TaskUpdate;
import org.fejoa.library.support.Task;
import org.json.JSONObject;

import static org.fejoa.library.command.UpdateCommand.BRANCH_CONTEXT_KEY;


public class UpdateCommandHandler extends EnvelopeCommandHandler {
    public interface IListener extends IncomingCommandManager.IListener {
        void onBranchUpdated(Contact contact, Remote remote, BranchInfo.Location location);
    }

    private IListener listener;
    final private Client client;

    public UpdateCommandHandler(Client client) {
        super(client.getUserData(), UpdateCommand.COMMAND_NAME);

        this.client = client;
    }

    @Override
    protected boolean handle(JSONObject command, final IncomingCommandManager.HandlerResponse response) throws Exception {
        String branchContext = command.getString(BRANCH_CONTEXT_KEY);
        String senderId = command.getString(Constants.SENDER_ID_KEY);
        String remoteId = command.getString(Constants.REMOTE_ID_KEY);
        String branch = command.getString(Constants.BRANCH_KEY);

        Contact contact = userData.getContact(senderId);
        Remote remote = contact.getRemotes().get(remoteId);
        final BranchInfo contactBranch = contact.getBranchList().get(branch, branchContext);
        BranchInfo.Location location = contactBranch.getLocationEntries().iterator().next();

        if (listener != null)
            listener.onBranchUpdated(contact, remote, location);

        client.sync(userData.getStorageDir(contactBranch), remote, location.getAuthInfo(userData.getContext()),
                new Task.IObserver<TaskUpdate, String>() {
            @Override
            public void onProgress(TaskUpdate taskUpdate) {

            }

            @Override
            public void onResult(String s) {
                response.setHandled();
            }

            @Override
            public void onException(Exception exception) {

            }
        });
        return true;
    }

    @Override
    public IncomingCommandManager.IListener getListener() {
        return listener;
    }

    public void setListener(IListener listener) {
        this.listener = listener;
    }
}
