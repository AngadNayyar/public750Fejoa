/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.remote;

import org.fejoa.library.UserDataSettings;
import org.fejoa.library.crypto.*;
import org.json.JSONException;
import org.json.JSONObject;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.io.InputStream;


public class CreateAccountJob extends SimpleJsonRemoteJob<RemoteJob.Result> {
    static final public String METHOD = "createAccount";

    static final public String ACCOUNT_SETTINGS_KEY = "accountSettings";

    final private String userName;
    final private String password;
    final private CryptoSettings.Password loginSettings;

    final private UserDataSettings userDataSettings;

    public CreateAccountJob(String userName, String password, UserDataSettings userDataSettings) {
        super(false);

        this.userName = userName;
        this.password = password;
        this.loginSettings = CryptoSettings.getDefault().masterPassword;

        this.userDataSettings = userDataSettings;
    }

    static public String makeServerPassword(String password, byte[] salt, String kdfAlgorithm, int keySize,
                                            int kdfIterations) throws CryptoException {
        ICryptoInterface crypto = Crypto.get();
        SecretKey secretKey = crypto.deriveKey(password, salt, kdfAlgorithm, keySize, kdfIterations);
        return CryptoHelper.sha256HashHex(secretKey.getEncoded());
    }

    @Override
    public String getJsonHeader(JsonRPC jsonRPC) throws IOException, JSONException {
        ICryptoInterface crypto = Crypto.get();
        byte[] salt = crypto.generateSalt();
        String derivedPassword;

        try {
            derivedPassword = makeServerPassword(password, salt, loginSettings.kdfAlgorithm, loginSettings.passwordSize,
                    loginSettings.kdfIterations);
        } catch (CryptoException e) {
            e.printStackTrace();
            throw new IOException(e.getMessage());
        }

        AccountSettings accountSettings = new AccountSettings(userName, derivedPassword, salt, loginSettings,
                userDataSettings);
        return jsonRPC.call(METHOD, new JsonRPC.Argument(ACCOUNT_SETTINGS_KEY, accountSettings.toJson()));
    }

    @Override
    protected Result handleJson(JSONObject returnValue, InputStream binaryData) {
        return getResult(returnValue);
    }
}
