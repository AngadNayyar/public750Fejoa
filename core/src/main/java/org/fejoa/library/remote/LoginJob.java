/*
 * Copyright 2017.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.remote;

import org.fejoa.library.Constants;
import org.fejoa.library.crypto.CryptoSettings;
import org.fejoa.library.crypto.JsonCryptoSettings;
import org.bouncycastle.util.encoders.Base64;
import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.crypto.ZeroKnowledgeCompare;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;


public class LoginJob extends SimpleJsonRemoteJob {
    static final public String METHOD = "login";
    static final public String TYPE_KEY = "type";
    static final public String TYPE_SCHNORR = "Schnorr";
    static final public String STATE_KEY = "state";
    static final public String STATE_0 = "0";
    static final public String STATE_1 = "1";
    static final public String GP_GROUP_KEY = "encGroup";
    static final public String COMMITMENT_KEY = "commitment";
    static final public String CHALLENGE_KEY = "challenge";
    static final public String VERIFICATION_VALUE_KEY = "verificationValue";

    static public class SendPasswordJob extends SimpleJsonRemoteJob {
        final private String userName;
        final private BigInteger s;

        public SendPasswordJob(String userName, BigInteger s) {
            super(false);

            this.userName = userName;
            this.s = s;
        }

        @Override
        public String getJsonHeader(JsonRPC jsonRPC) throws IOException {
            return jsonRPC.call(METHOD,
                    new JsonRPC.Argument(Constants.USER_KEY, userName),
                    new JsonRPC.Argument(TYPE_KEY, TYPE_SCHNORR),
                    new JsonRPC.Argument(STATE_KEY, STATE_1),
                    new JsonRPC.Argument(VERIFICATION_VALUE_KEY, s.toString(16)));
        }

        @Override
        protected Result handleJson(JSONObject returnValue, InputStream binaryData) {
            return getResult(returnValue);
        }
    }

    final private String userName;
    final private String password;

    final private ZeroKnowledgeCompare.ProverState0 prover;

    public LoginJob(String userName, String password) throws CryptoException {
        super(false);

        this.userName = userName;
        this.password = password;

        this.prover = ZeroKnowledgeCompare.createProver(ZeroKnowledgeCompare.RFC5114_2048_256);
    }

    @Override
    public String getJsonHeader(JsonRPC jsonRPC) throws IOException {
        return jsonRPC.call(METHOD,
                new JsonRPC.Argument(Constants.USER_KEY, userName),
                new JsonRPC.Argument(TYPE_KEY, TYPE_SCHNORR),
                new JsonRPC.Argument(STATE_KEY, STATE_0),
                new JsonRPC.Argument(GP_GROUP_KEY, ZeroKnowledgeCompare.RFC5114_2048_256),
                new JsonRPC.Argument(COMMITMENT_KEY, prover.getH().toString(16)));
    }

    @Override
    protected Result handleJson(JSONObject returnValue, InputStream binaryData) {
        try {
            byte[] salt = Base64.decode(returnValue.getString(AccountSettings.LOGIN_KDF_SALT_KEY));
            CryptoSettings.Password kdfSettings = JsonCryptoSettings.passwordFromJson(
                    returnValue.getJSONObject(AccountSettings.LOGIN_KDF_SETTINGS_KEY));

            byte[] secret = CreateAccountJob.makeServerPassword(password, salt, kdfSettings.kdfAlgorithm,
                    kdfSettings.passwordSize, kdfSettings.kdfIterations);

            BigInteger challenge = new BigInteger(returnValue.getString(CHALLENGE_KEY), 16);
            ZeroKnowledgeCompare.ProverState1 proverState1 = prover.setVerifierChallenge(challenge);
            setFollowUpJob(new SendPasswordJob(userName, proverState1.getS(secret)));
            return new Result(Errors.FOLLOW_UP_JOB, "parameters received");
        } catch (JSONException e) {
            e.printStackTrace();
            return new Result(Errors.ERROR, "parameter missing");
        } catch (CryptoException e) {
            e.printStackTrace();
            return new Result(Errors.ERROR, "parameter missing");
        }
    }
}
