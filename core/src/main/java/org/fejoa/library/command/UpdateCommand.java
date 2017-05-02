/*
 * Copyright 2017.
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


public class UpdateCommand extends EncryptedZipSignedCommand {
    static final public String COMMAND_NAME = "branchUpdates";
    static final public String BRANCH_CONTEXT_KEY = "context";

    static private String makeCommand(ContactPrivate sender, Remote remote, BranchInfo branchInfo, String context)
            throws JSONException, CryptoException {
        JSONObject command = new JSONObject();
        command.put(Constants.COMMAND_NAME_KEY, COMMAND_NAME);
        command.put(Constants.SENDER_ID_KEY, sender.getId());
        command.put(Constants.REMOTE_ID_KEY, remote.getId());
        command.put(BRANCH_CONTEXT_KEY, context);
        command.put(Constants.BRANCH_KEY, branchInfo.getBranch());
        return command.toString();
    }

    public UpdateCommand(FejoaContext context, ContactPrivate sender, ContactPublic receiver, Remote remote,
                         BranchInfo branchInfo,
                         String branchContext)
            throws IOException, CryptoException, JSONException {
        super(context, makeCommand(sender, remote, branchInfo, branchContext), sender, receiver);
    }
}
