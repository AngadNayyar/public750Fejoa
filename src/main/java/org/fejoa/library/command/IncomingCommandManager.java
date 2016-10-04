/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.command;

import org.fejoa.library.UserDataConfig;
import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.database.DatabaseDiff;
import org.fejoa.library.support.WeakListenable;
import org.fejoa.library.UserData;
import org.fejoa.library.database.StorageDir;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;


public class IncomingCommandManager extends WeakListenable<IncomingCommandManager.IListener> {
    static public class ReturnValue {
        final static int HANDLED = 0;
        final static int RETRY = 1;

        final public int status;
        final public String command;

        public ReturnValue(int status, String command) {
            this.status = status;
            this.command = command;
        }
    }

    public interface IListener {
        void onCommandReceived(ReturnValue returnValue);
        void onException(Exception exception);
    }

    public interface Handler {
        String handlerName();

        /**
         * Handler for a command.
         *
         * @param command the command entry
         * @return null if unhandled
         * @throws Exception
         */
        ReturnValue handle(CommandQueue.Entry command) throws Exception;
    }

    final static private Logger LOG = Logger.getLogger(IncomingCommandManager.class.getName());
    final private List<IncomingCommandQueue> queues = new ArrayList<>();
    final private List<Handler> handlerList = new ArrayList<>();

    public IncomingCommandManager(UserDataConfig userDataConfig) throws IOException, CryptoException {
        this.queues.add(userDataConfig.getUserData().getIncomingCommandQueue());

        UserData userData = userDataConfig.getUserData();
        addHandler(new ContactRequestCommandHandler(userData));
        addHandler(new AccessCommandHandler(userData));
        addHandler(new MigrationCommandHandler(userDataConfig));
    }

    public void addHandler(Handler handler) {
        handlerList.add(handler);
    }

    private List<StorageDir.IListener> hardRefList = new ArrayList<>();
    public void start() {
        for (final IncomingCommandQueue queue : queues) {
            StorageDir dir = queue.getStorageDir();
            StorageDir.IListener listener = new StorageDir.IListener() {
                @Override
                public void onTipChanged(DatabaseDiff diff, String base, String tip) {
                    handleCommands(queue);
                }
            };
            hardRefList.add(listener);
            dir.addListener(listener);

            handleCommands(queue);
        }
    }

    private void handleCommands(IncomingCommandQueue queue) {
        try {
            List<CommandQueue.Entry> commands = queue.getCommands();
            boolean anyHandled = false;
            for (CommandQueue.Entry command : commands) {
                if (handleCommand(queue, command)) {
                    anyHandled = true;
                    break;
                }
            }
            if (anyHandled)
                queue.commit();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (CryptoException e) {
            e.printStackTrace();
        }
    }

    private boolean handleCommand(IncomingCommandQueue queue, CommandQueue.Entry command) {
        return handleCommand(queue, command, handlerList, 0);
    }

    private boolean handleCommand(IncomingCommandQueue queue, CommandQueue.Entry command, List<Handler> handlers,
                                  int retryCount) {
        if (retryCount > 1)
            return false;
        boolean handled = false;
        List<Handler> retryHandlers = new ArrayList<>();
        for (Handler handler : handlers) {
            ReturnValue returnValue = null;
            try {
                returnValue = handler.handle(command);
            } catch (Exception e) {
                LOG.warning("Exception in command: " + handler.handlerName());
                notifyOnException(e);
            }
            if (returnValue == null)
                continue;
            handled = true;
            if (returnValue.status == ReturnValue.HANDLED) {
                try {
                    queue.removeCommand(command);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                notifyOnCommandReceived(returnValue);
            } else if (returnValue.status == ReturnValue.RETRY)
                retryHandlers.add(handler);
            break;
        }
        if (!handled)
            LOG.warning("Unhandled command!");

        retryCount++;
        if (retryHandlers.size() > 0)
            handleCommand(queue, command, retryHandlers, retryCount);

        return handled;
    }

    public void notifyOnCommandReceived(ReturnValue returnValue) {
        for (IListener listener : getListeners())
            listener.onCommandReceived(returnValue);
    }

    private void notifyOnException(Exception exception) {
        for (IListener listener : getListeners())
            listener.onException(exception);
    }
}
