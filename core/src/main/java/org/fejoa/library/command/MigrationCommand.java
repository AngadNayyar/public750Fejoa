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

    static final public String NEW_USER_KEY = "newUser";
    static final public String NEW_SERVER_KEY = "newServer";

    static private String makeCommand(ContactPrivate sender, Remote newRemote)
            throws JSONException {
        JSONObject command = new JSONObject();
        command.put(Constants.COMMAND_NAME_KEY, COMMAND_NAME);
        command.put(Constants.SENDER_ID_KEY, sender.getId());
        command.put(NEW_USER_KEY, newRemote.getUser());
        command.put(NEW_SERVER_KEY, newRemote.getServer());
        return command.toString();
    }

    public MigrationCommand(FejoaContext context, Remote newRemote, ContactPrivate sender,
                            ContactPublic receiver)
            throws IOException, CryptoException, JSONException {
        super(context, makeCommand(sender, newRemote), sender, receiver);
    }
}
