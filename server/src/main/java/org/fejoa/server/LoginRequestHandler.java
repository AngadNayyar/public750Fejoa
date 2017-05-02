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
import org.fejoa.library.crypto.AuthProtocolEKE2_SHA3_256_CTR;
import org.fejoa.library.crypto.CryptoHelper;
import org.fejoa.library.crypto.JsonCryptoSettings;
import org.fejoa.library.remote.*;
import org.json.JSONObject;

import java.io.InputStream;

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
        if (!type.equals(TYPE_FEJOA_EKE2_SHA3_256)) {
            responseHandler.setResponseHeader(jsonRPCHandler.makeResult(Errors.ERROR,
                    "Login type not support: " + type));
            return;
        }

        String state = params.getString(STATE_KEY);
        AccountSettings accountSettings = session.getAccountSettings(userName);
        if (state.equals(LoginJob.STATE_INIT_AUTH)) {
            String gpGroup = params.getString(GP_GROUP_KEY);
            if (!gpGroup.equals(AuthProtocolEKE2_SHA3_256_CTR.RFC5114_2048_256)) {
                responseHandler.setResponseHeader(jsonRPCHandler.makeResult(Errors.ERROR,
                        "Unsupported group: " + gpGroup));
                return;
            }

            byte[] secret = accountSettings.derivedPassword;
            AuthProtocolEKE2_SHA3_256_CTR.ProverState0 proverState0
                    = AuthProtocolEKE2_SHA3_256_CTR.createProver(gpGroup, secret);
            session.setLoginEKE2Prover(userName, proverState0);

            String response = jsonRPCHandler.makeResult(Errors.OK, "EKE2 auth initiated",
                    new JsonRPC.Argument(AccountSettings.LOGIN_USER_KEY_PARAMS,
                            accountSettings.loginUserKeyParams.toJson()),
                    new JsonRPC.Argument(ENC_GX, Base64.encodeBase64String(proverState0.getEncGX())));
            responseHandler.setResponseHeader(response);
        } else if (state.equals(LoginJob.STATE_FINISH_AUTH)) {
            byte[] encGY = Base64.decodeBase64(params.getString(ENC_GY));
            byte[] vericationToken = Base64.decodeBase64(params.getString(VERIFICATION_TOKEN_VERIFIER));

            AuthProtocolEKE2_SHA3_256_CTR.ProverState0 proverState0 = session.getLoginSchnorrVerifier(userName);
            if (proverState0 == null) {
                responseHandler.setResponseHeader(jsonRPCHandler.makeResult(Errors.ERROR, "invalid state"));
                return;
            }
            session.setLoginEKE2Prover(userName, null);

            AuthProtocolEKE2_SHA3_256_CTR.ProverState1 proverState1 = proverState0.setVerifierResponse(encGY,
                    vericationToken);

            if (proverState1 != null) {
                session.addRootRole(userName);

                String response = jsonRPCHandler.makeResult(Errors.OK, "EKE2 auth succeeded",
                        new JsonRPC.Argument(VERIFICATION_TOKEN_PROVER,
                                Base64.encodeBase64String(proverState1.getAuthToken())));
                responseHandler.setResponseHeader(response);
            } else
                responseHandler.setResponseHeader(jsonRPCHandler.makeResult(Errors.ERROR, "login failed"));
        } else
            throw new Exception("Invalid login state: " + state);

    }
}
