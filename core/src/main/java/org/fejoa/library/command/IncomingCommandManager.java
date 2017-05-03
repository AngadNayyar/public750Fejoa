/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.command;

import org.fejoa.library.Client;
import org.fejoa.library.database.StorageDir;
import org.fejoa.library.AppContext;
import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.database.DatabaseDiff;
import org.fejoa.library.UserData;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;


public class IncomingCommandManager {
    public class HandlerResponse {
        final private IncomingCommandQueue queue;
        final private String commandId;

        public HandlerResponse(IncomingCommandQueue queue, String id) {
            this.queue = queue;
            this.commandId = id;
        }

        public void setHandled() {
            removeCommand();
        }

        private boolean removeCommand() {
            try {
                queue.removeCommand(commandId);
                queue.commit();
                return true;
            } catch (Exception e) {
                e.printStackTrace();
                LOG.throwing("Can't remove command.", e.getMessage(), e);
                return false;
            }
        }

        public void setError(Exception e) {
            LOG.throwing("Error.", e.getMessage(), e);
            removeCommand();
        }

        public void setRetryLater() {
            //todo: schedule a new handling
        }
    }

    public interface IListener {
        void onError(Exception e);
    }

    public interface Handler {
        String handlerName();

        /**
         * Handler for a command.
         *
         * @param command the command entry
         * @param response to communicate with the manager
         * @return true if the command is accepted by the handler
         * @throws Exception
         */
        boolean handle(CommandQueue.Entry command, HandlerResponse response) throws Exception;

        IListener getListener();
    }

    final static private Logger LOG = Logger.getLogger(IncomingCommandManager.class.getName());
    final private List<IncomingCommandQueue> queues = new ArrayList<>();
    final private List<Handler> handlerList = new ArrayList<>();
    final private List<HandlerResponse> ongoingHandling = new ArrayList<>();

    public IncomingCommandManager(Client client)
            throws IOException, CryptoException {
        this.queues.add(client.getUserData().getIncomingCommandQueue());

        UserData userData = client.getUserData();
        addHandler(new ContactRequestCommandHandler(userData));
        addHandler(new AccessCommandHandler(userData));
        addHandler(new MigrationCommandHandler(userData));
        addHandler(new UpdateCommandHandler(client));
    }

    public void addHandler(Handler handler) {
        handlerList.add(handler);
    }

    public Handler getHandler(String name) {
        for (Handler handler : handlerList) {
            if (handler.handlerName().equals(name))
                return handler;
        }
        return null;
    }

    private List<StorageDir.IListener> hardRefList = new ArrayList<>();
    public void start() {
        for (final IncomingCommandQueue queue : queues) {
            StorageDir dir = queue.getStorageDir();
            StorageDir.IListener listener = new StorageDir.IListener() {
                @Override
                public void onTipChanged(DatabaseDiff diff) {
                    handleCommands(queue);
                }
            };
            hardRefList.add(listener);
            dir.addListener(listener);

            handleCommands(queue);
        }
    }

    private boolean isCommandHandling(CommandQueue.Entry command) {
        for (HandlerResponse response : ongoingHandling) {
            if (response.commandId.equals(command.hash()))
                return true;
        }
        return false;
    }

    public void handleCommands() {
        for (IncomingCommandQueue queue : queues)
            handleCommands(queue);
    }

    private void handleCommands(IncomingCommandQueue queue) {
        try {
            List<CommandQueue.Entry> commands = queue.getCommands();
            for (CommandQueue.Entry command : commands) {
                if (isCommandHandling(command))
                    continue;
                handleCommand(queue, command);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (CryptoException e) {
            e.printStackTrace();
        }
    }

    private boolean handleCommand(IncomingCommandQueue queue, CommandQueue.Entry command) {
        boolean accepted = false;
        HandlerResponse response = new HandlerResponse(queue, command.hash());
        ongoingHandling.add(response);
        for (Handler handler : handlerList) {
            try {
                accepted = handler.handle(command, response);
            } catch (Exception e) {
                if (handler.getListener() != null)
                    handler.getListener().onError(e);
                LOG.warning("Exception in command: " + handler.handlerName());
                response.setError(e);
            }
            if (accepted)
                break;
        }
        if (!accepted) {
            ongoingHandling.remove(response);
            LOG.warning("Unhandled command!");
        }

        return accepted;
    }
}
