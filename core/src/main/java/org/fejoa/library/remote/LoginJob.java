/*
 * Copyright 2017.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.remote;

import org.apache.commons.codec.binary.Base64;
import org.fejoa.library.Constants;
import org.fejoa.library.FejoaContext;
import org.fejoa.library.crypto.*;
import org.json.JSONException;
import org.json.JSONObject;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.io.InputStream;

import static org.fejoa.library.crypto.AuthProtocolEKE2_SHA3_256_CTR.RFC5114_2048_256;


public class LoginJob extends SimpleJsonRemoteJob {
    static final public String METHOD = "login";
    static final public String TYPE_KEY = "type";
    static final public String TYPE_FEJOA_EKE2_SHA3_256 = "Fejoa_EKE2_SHA3_256";
    static final public String STATE_KEY = "state";
    static final public String STATE_INIT_AUTH = "initAuth";
    static final public String STATE_FINISH_AUTH = "finishAuth";
    static final public String GP_GROUP_KEY = "encGroup";
    static final public String ENC_GX = "encX";
    static final public String ENC_GY = "encY";
    static final public String VERIFICATION_TOKEN_VERIFIER = "tokenVerifier";
    static final public String VERIFICATION_TOKEN_PROVER = "tokenProver";

    static public class FinishAuthJob extends SimpleJsonRemoteJob {
        final private String userName;
        final private AuthProtocolEKE2_SHA3_256_CTR.Verifier verifier;

        public FinishAuthJob(String userName, AuthProtocolEKE2_SHA3_256_CTR.Verifier verifier) {
            super(false);

            this.userName = userName;
            this.verifier = verifier;
        }

        @Override
        public String getJsonHeader(JsonRPC jsonRPC) throws IOException {
            try {
                return jsonRPC.call(METHOD,
                        new JsonRPC.Argument(Constants.USER_KEY, userName),
                        new JsonRPC.Argument(TYPE_KEY, TYPE_FEJOA_EKE2_SHA3_256),
                        new JsonRPC.Argument(STATE_KEY, STATE_FINISH_AUTH),
                        new JsonRPC.Argument(GP_GROUP_KEY, RFC5114_2048_256),
                        new JsonRPC.Argument(ENC_GY, Base64.encodeBase64String(verifier.getEncGy())),
                        new JsonRPC.Argument(VERIFICATION_TOKEN_VERIFIER,
                                Base64.encodeBase64String(verifier.getAuthToken())));
            } catch (CryptoException e) {
                throw new IOException(e);
            }
        }

        @Override
        protected Result handleJson(JSONObject returnValue, InputStream binaryData) {
            byte[] proverToken = Base64.decodeBase64(returnValue.getString(VERIFICATION_TOKEN_PROVER));
            if (!verifier.verify(proverToken))
                return new Result(Errors.ERROR, "EKE2: server fails to send correct auth token");

            return new Result(Errors.OK, "EKE2 auth successful");
        }
    }

    final private FejoaContext context;
    final private String userName;
    final private String password;

    public LoginJob(FejoaContext context, String userName, String password) throws CryptoException {
        super(false);

        this.context = context;
        this.userName = userName;
        this.password = password;
    }

    @Override
    public String getJsonHeader(JsonRPC jsonRPC) throws IOException {
        return jsonRPC.call(METHOD,
                new JsonRPC.Argument(Constants.USER_KEY, userName),
                new JsonRPC.Argument(TYPE_KEY, TYPE_FEJOA_EKE2_SHA3_256),
                new JsonRPC.Argument(GP_GROUP_KEY, RFC5114_2048_256),
                new JsonRPC.Argument(STATE_KEY, STATE_INIT_AUTH));
    }

    @Override
    protected Result handleJson(JSONObject returnValue, InputStream binaryData) {
        try {
            UserKeyParameters loginUserKeyParams
                    = new UserKeyParameters(returnValue.getJSONObject(AccountSettings.LOGIN_USER_KEY_PARAMS));

            SecretKey kdfKey = context.getKDFKey(loginUserKeyParams.kdfParameters, password);
            SecretKey secretKey = UserKeyParameters.deriveUserKey(kdfKey, loginUserKeyParams);

            // EKE2 authenticates both sides and the server auth first. So we are the verifier and the server is the
            // prover.
            byte[] encGX = Base64.decodeBase64(returnValue.getString(ENC_GX));
            AuthProtocolEKE2_SHA3_256_CTR.Verifier verifier
                    = AuthProtocolEKE2_SHA3_256_CTR.createVerifier(RFC5114_2048_256, secretKey.getEncoded(), encGX);

            setFollowUpJob(new FinishAuthJob(userName, verifier));
            return new Result(Errors.FOLLOW_UP_JOB, "parameters received");
        } catch (JSONException e) {
            e.printStackTrace();
            return new Result(Errors.ERROR, "parameter missing");
        } catch (Exception e) {
            e.printStackTrace();
            return new Result(Errors.ERROR, "Exception: " + e.getMessage());
        }
    }
}
