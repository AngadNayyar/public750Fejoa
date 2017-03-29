/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.command;

import org.fejoa.library.*;
import org.json.JSONObject;

import static org.fejoa.library.command.MigrationCommand.REMOTE_ID_KEY;


public class MigrationCommandHandler extends EnvelopeCommandHandler {
    public interface IListener extends IncomingCommandManager.IListener {
        void onContactMigrated(String contactId);
    }

    private IListener listener;

    public MigrationCommandHandler(UserData userData) {
        super(userData, MigrationCommand.COMMAND_NAME);

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
        String senderId = command.getString(Constants.SENDER_ID_KEY);
        String remoteId = command.getString(REMOTE_ID_KEY);
        String newUserName = command.getString(MigrationCommand.NEW_USER_KEY);
        String newServer = command.getString(MigrationCommand.NEW_SERVER_KEY);

        // update contact entry
        StorageDirList<ContactPublic> contactList = userData.getContactStore().getContactList();
        ContactPublic contact = contactList.get(senderId);
        Remote oldRemote = contact.getRemotes().get(remoteId);
        Remote newRemote = new Remote(oldRemote.getId(), newUserName, newServer);
        contact.getRemotes().add(newRemote);
        userData.getContactStore().commit();

        // update outgoing commands
        OutgoingCommandQueue queue = userData.getOutgoingCommandQueue();
        queue.updateReceiver(oldRemote.getUser(), oldRemote.getServer(),
                newRemote.getUser(), newRemote.getServer());
        queue.commit();

        if (listener != null)
            listener.onContactMigrated(senderId);

        return true;
    }
}
