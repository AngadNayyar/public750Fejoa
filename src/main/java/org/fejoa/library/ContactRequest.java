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
    static public void startRequest(UserData userData, String user, String server) throws Exception {
        userData.getOutgoingCommandQueue().post(ContactRequestCommand.makeInitialRequest(
                userData.getMyself(),
                userData.getGateway()), user, server);
    }
}
