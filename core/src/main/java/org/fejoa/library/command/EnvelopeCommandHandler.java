/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.command;

import org.apache.commons.io.IOUtils;
import org.fejoa.library.Constants;
import org.fejoa.library.FejoaContext;
import org.fejoa.library.UserData;
import org.fejoa.library.messages.Envelope;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.util.logging.Logger;


abstract class EnvelopeCommandHandler implements IncomingCommandManager.Handler {
    final static private Logger LOG = Logger.getLogger(EnvelopeCommandHandler.class.getName());

    final protected FejoaContext context;
    final protected UserData userData;
    final protected String commandType;

    public EnvelopeCommandHandler(UserData userData, String commandType) {
        this.context = userData.getContext();
        this.userData = userData;
        this.commandType = commandType;
    }

    @Override
    public String handlerName() {
        return commandType;
    }

    @Override
    public boolean handle(CommandQueue.Entry command, IncomingCommandManager.HandlerResponse response) throws Exception {
        byte[] request;
        Envelope envelope = new Envelope();
        try {
            request = IOUtils.toByteArray(envelope.unpack(new ByteArrayInputStream(command.getData()),
                    userData.getMyself(),
                    userData.getContactStore().getContactFinder(), context));
        } catch (Exception e) {
            LOG.warning("Can't open envelop or not an enveloped command: " + e.getMessage());
            LOG.info("Command as string: " + new String(command.getData()));
            return false;
        }

        JSONObject object = new JSONObject(new String(request));
        if (!object.has(Constants.COMMAND_NAME_KEY)
                || !object.getString(Constants.COMMAND_NAME_KEY).equals(this.commandType))
            return false;

        LOG.info("Handle command: " + object.toString());

        // verify that the sender id matches the sender id in the unpacked message
        String senderId = envelope.getSenderId();
        if (senderId != null) {
            if (!senderId.equals(object.getString(Constants.SENDER_ID_KEY))) {
                LOG.warning("Command with mismatching sender id. Signature sender id: " + senderId +
                        " command sender id: " + object.getString(Constants.SENDER_ID_KEY));
                return false;
            }
        }
        return handle(object, response);
    }

    /**
     * Handle an unpacked json command.
     *
     * @param command
     * @return
     * @throws Exception
     */
    abstract protected boolean handle(JSONObject command, IncomingCommandManager.HandlerResponse response)
            throws Exception;
}
