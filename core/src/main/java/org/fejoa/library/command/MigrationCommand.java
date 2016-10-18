/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.command;

import org.fejoa.library.*;
import org.fejoa.library.crypto.CryptoException;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;


public class MigrationCommand extends EncryptedZipSignedCommand  {
    static final public String COMMAND_NAME = "migration";

    static final public String REMOTE_ID_KEY = "remoteID";
    static final public String NEW_USER_KEY = "newUser";
    static final public String NEW_SERVER_KEY = "newServer";

    static private String makeCommand(ContactPrivate sender, Remote updatedRemote)
            throws JSONException {
        JSONObject command = new JSONObject();
        command.put(Constants.COMMAND_NAME_KEY, COMMAND_NAME);
        command.put(Constants.SENDER_ID_KEY, sender.getId());
        command.put(REMOTE_ID_KEY, updatedRemote.getId());
        command.put(NEW_USER_KEY, updatedRemote.getUser());
        command.put(NEW_SERVER_KEY, updatedRemote.getServer());
        return command.toString();
    }

    public MigrationCommand(FejoaContext context, Remote updatedRemote, ContactPrivate sender,
                            ContactPublic receiver)
            throws IOException, CryptoException, JSONException {
        super(context, makeCommand(sender, updatedRemote), sender, receiver);
    }
}
