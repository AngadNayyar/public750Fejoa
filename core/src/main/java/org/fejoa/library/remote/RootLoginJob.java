/*
 * Copyright 2015.
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
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;


public class RootLoginJob extends SimpleJsonRemoteJob {
    static final public String METHOD = "rootLogin";
    static final public String REQUEST_KEY = "request";
    static final public String PARAMETER_REQUEST = "getParameters";
    static final public String LOGIN_REQUEST = "login";

    static public class SendPasswordJob extends SimpleJsonRemoteJob {
        final private String userName;
        final private String serverPassword;

        public SendPasswordJob(String userName, String serverPassword) {
            super(false);

            this.userName = userName;
            this.serverPassword = serverPassword;
        }

        @Override
        public String getJsonHeader(JsonRPC jsonRPC) throws IOException {
            return jsonRPC.call(METHOD, new JsonRPC.Argument(REQUEST_KEY, LOGIN_REQUEST),
                    new JsonRPC.Argument(Constants.USER_KEY, userName),
                    new JsonRPC.Argument(Constants.PASSWORD, serverPassword));
        }

        @Override
        protected Result handleJson(JSONObject returnValue, InputStream binaryData) {
            return getResult(returnValue);
        }
    }

    final private String userName;
    final private String password;

    public RootLoginJob(String userName, String password) {
        super(false);

        this.userName = userName;
        this.password = password;
    }

    @Override
    public String getJsonHeader(JsonRPC jsonRPC) throws IOException {
        return jsonRPC.call(METHOD, new JsonRPC.Argument(REQUEST_KEY, PARAMETER_REQUEST),
                new JsonRPC.Argument(Constants.USER_KEY, userName));
    }

    @Override
    protected Result handleJson(JSONObject returnValue, InputStream binaryData) {
        try {
            byte[] salt = Base64.decode(returnValue.getString(AccountSettings.LOGIN_KDF_SALT_KEY));
            CryptoSettings.Password kdfSettings = JsonCryptoSettings.passwordFromJson(
                    returnValue.getJSONObject(AccountSettings.LOGIN_KDF_SETTINGS_KEY));

            String serverPassword = CreateAccountJob.makeServerPassword(password, salt, kdfSettings.kdfAlgorithm,
                    kdfSettings.passwordSize, kdfSettings.kdfIterations);
            setFollowUpJob(new SendPasswordJob(userName, serverPassword));
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
