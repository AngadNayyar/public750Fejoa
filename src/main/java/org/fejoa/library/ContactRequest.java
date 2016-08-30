/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library;

import org.fejoa.library.command.ContactRequestCommand;
import org.fejoa.library.command.ContactRequestCommandHandler;
import org.fejoa.library.command.IncomingCommandManager;


public class ContactRequest {
    public interface IHandler {
        /**
         *  Called after the reply is received.
         * @param contactPublic
         * @return true to continue
         */
        boolean onReplyRequest(ContactPublic contactPublic);
        void onAborted();
        void onFinish();
        void onException(Exception exception);
    }

    abstract static public class AutoAcceptHandler implements IHandler {
        @Override
        public boolean onReplyRequest(ContactPublic contactPublic) {
            return true;
        }

        @Override
        public void onAborted() {

        }
    }

    final private Client client;
    final IncomingCommandManager.IListener listener = new IncomingCommandManager.IListener() {
        @Override
        public void onCommandReceived(IncomingCommandManager.ReturnValue returnValue) {
            if (returnValue.command.equals(ContactRequestCommand.COMMAND_NAME)) {
                String state = ((ContactRequestCommandHandler.ReturnValue)returnValue).state;

                if (state.equals(ContactRequestCommand.INITIAL_STATE)) {
                    // we are not interested in that state
                    return;
                } else if (state.equals(ContactRequestCommand.REPLY_STATE)) {
                    onReplyRequest((ContactRequestCommandHandler.ReturnValue)returnValue);
                    client.getIncomingCommandManager().removeListener(listener);
                    handler.onFinish();
                } else if (state.equals(ContactRequestCommand.FINISH_STATE)) {

                } else
                    onException(new Exception("unexpected state"));
            } else
                onException(new Exception("unexpected command"));
        }

        @Override
        public void onException(Exception exception) {
            onException(exception);
        }
    };
    private IHandler handler;

    public ContactRequest(Client client) {
        this.client = client;
    }

    public void startRequest(String user, String server, IHandler handler) {
        this.handler = handler;

        client.getIncomingCommandManager().addListener(listener);
        try {
            initialRequest(user, server);
        } catch (Exception e) {
            onException(e);
        }
    }

    private void abort() {
        client.getIncomingCommandManager().removeListener(listener);
        handler.onAborted();
    }

    private void onException(Exception e) {
        client.getIncomingCommandManager().removeListener(listener);
        handler.onException(e);
    }

    private void initialRequest(String user, String server) throws Exception {
        client.getUserData().getOutgoingCommandQueue().post(ContactRequestCommand.makeInitialRequest(client.getContext(),
                client.getUserData().getMyself(),
                client.getUserData().getGateway()), user, server);
    }

    private void onReplyRequest(ContactRequestCommandHandler.ReturnValue returnValue) {
        String contactId = returnValue.contactId;
        ContactPublic contactPublic = client.getUserData().getContactStore().getContactList().get(
                contactId);
        if (contactPublic == null) {
            onException(new Exception("contact not found!"));
            return;
        }
        if (!handler.onReplyRequest(contactPublic)) {
            abort();
            return;
        }
        Remote remote = contactPublic.getRemotes().getDefault();
        try {
            client.getUserData().getOutgoingCommandQueue().post(ContactRequestCommand.makeFinish(
                    client.getContext(),
                    client.getUserData().getMyself(), contactPublic),
                    remote.getUser(), remote.getServer());
        } catch (Exception e) {
            onException(e);
        }
    }
}
