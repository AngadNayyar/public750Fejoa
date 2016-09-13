/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.server;

import org.apache.commons.codec.binary.Base64;
import org.fejoa.library.Constants;
import org.fejoa.library.UserDataSettings;
import org.fejoa.library.crypto.JsonCryptoSettings;
import org.fejoa.library.remote.CreateAccountJob;
import org.fejoa.library.remote.JsonRPC;
import org.fejoa.library.remote.JsonRPCHandler;
import org.fejoa.library.remote.RootLoginJob;
import org.json.JSONObject;

import java.io.InputStream;


public class RootLoginRequestHandler extends JsonRequestHandler {
    public RootLoginRequestHandler() {
        super(RootLoginJob.METHOD);
    }

    @Override
    public void handle(Portal.ResponseHandler responseHandler, JsonRPCHandler jsonRPCHandler, InputStream data,
                       Session session) throws Exception {
        JSONObject params = jsonRPCHandler.getParams();
        String request = params.getString(RootLoginJob.REQUEST_KEY);
        String userName = params.getString(Constants.USER_KEY);

        AccountSettings accountSettings = session.getAccountSettings(userName);

        if (request.equals(RootLoginJob.PARAMETER_REQUEST)) {
            String saltBase64 = Base64.encodeBase64String(accountSettings.salt);

            String response = jsonRPCHandler.makeResult(Portal.Errors.OK, "root login parameter",
                    new JsonRPC.Argument(AccountSettings.LOGIN_KDF_SALT_KEY, saltBase64),
                    new JsonRPC.Argument(AccountSettings.LOGIN_KDF_SETTINGS_KEY,
                            JsonCryptoSettings.toJson(accountSettings.loginSettings)));
            responseHandler.setResponseHeader(response);
        } else if (request.equals(RootLoginJob.LOGIN_REQUEST)) {
            String receivedPassword = params.getString(Constants.PASSWORD);
            if (receivedPassword.equals(accountSettings.derivedPassword)) {
                session.addRootRole(userName);
                responseHandler.setResponseHeader(jsonRPCHandler.makeResult(Portal.Errors.OK, "login successful"));
            } else
                responseHandler.setResponseHeader(jsonRPCHandler.makeResult(Portal.Errors.ERROR, "login failed"));
        } else
            throw new Exception("Invalid root login request: " + request);
    }
}
