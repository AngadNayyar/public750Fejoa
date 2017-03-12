/*
 * Copyright 2017.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.server;

import org.apache.commons.codec.binary.Base64;
import org.fejoa.library.Constants;
import org.fejoa.library.crypto.CryptoHelper;
import org.fejoa.library.crypto.JsonCryptoSettings;
import org.fejoa.library.crypto.ZeroKnowledgeCompare;
import org.fejoa.library.remote.*;
import org.json.JSONObject;

import java.io.InputStream;
import java.math.BigInteger;

import static org.fejoa.library.remote.LoginJob.*;


public class LoginRequestHandler extends JsonRequestHandler {
    public LoginRequestHandler() {
        super(LoginJob.METHOD);
    }

    @Override
    public void handle(Portal.ResponseHandler responseHandler, JsonRPCHandler jsonRPCHandler, InputStream data,
                       Session session) throws Exception {
        JSONObject params = jsonRPCHandler.getParams();
        String userName = params.getString(Constants.USER_KEY);
        String type = params.getString(TYPE_KEY);
        if (!type.equals(TYPE_SCHNORR)) {
            responseHandler.setResponseHeader(jsonRPCHandler.makeResult(Errors.ERROR,
                    "Login type not support: " + type));
            return;
        }

        String state = params.getString(STATE_KEY);
        AccountSettings accountSettings = session.getAccountSettings(userName);
        if (state.equals(LoginJob.STATE_0)) {
            String gpGroup = params.getString(GP_GROUP_KEY);
            BigInteger commitment = new BigInteger(params.getString(COMMITMENT_KEY), 16);
            byte[] secret = CryptoHelper.fromHex(accountSettings.derivedPassword);
            ZeroKnowledgeCompare.Verifier verifier = ZeroKnowledgeCompare.createVerifier(gpGroup, secret, commitment);
            session.setLoginSchnorrVerifier(userName, verifier);

            String saltBase64 = Base64.encodeBase64String(accountSettings.salt);
            String response = jsonRPCHandler.makeResult(Errors.OK, "root login parameter",
                    new JsonRPC.Argument(AccountSettings.LOGIN_KDF_SALT_KEY, saltBase64),
                    new JsonRPC.Argument(AccountSettings.LOGIN_KDF_SETTINGS_KEY,
                            JsonCryptoSettings.toJson(accountSettings.loginSettings)),
                    new JsonRPC.Argument(CHALLENGE_KEY, verifier.getB().toString(16)));
            responseHandler.setResponseHeader(response);
        } else if (state.equals(LoginJob.STATE_1)) {
            BigInteger verificationValue = new BigInteger(params.getString(VERIFICATION_VALUE_KEY), 16);
            ZeroKnowledgeCompare.Verifier verifier = session.getLoginSchnorrVerifier(userName);
            if (verifier == null) {
                responseHandler.setResponseHeader(jsonRPCHandler.makeResult(Errors.ERROR, "invalid state"));
                return;
            }
            session.setLoginSchnorrVerifier(userName, null);
            if (verifier.verify(verificationValue)) {
                session.addRootRole(userName);
                responseHandler.setResponseHeader(jsonRPCHandler.makeResult(Errors.OK, "login successful"));
            } else
                responseHandler.setResponseHeader(jsonRPCHandler.makeResult(Errors.ERROR, "login failed"));
        } else
            throw new Exception("Invalid login state: " + state);

    }
}
