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

    final private UserDataSettings userDataSettings;

    public CreateAccountJob(String userName, String password, UserDataSettings userDataSettings) {
        super(false);

        this.userName = userName;
        this.password = password;

        this.userDataSettings = userDataSettings;
    }

    @Override
    public String getJsonHeader(JsonRPC jsonRPC) throws IOException, JSONException {
        // Derive the new user key params. Reuse the kdf params from the userdata keystore (so the the kdf only needs to
        // be evaluated once).
        KDFParameters kdfParameters = userDataSettings.keyStoreSettings.kdfCrypto.userKeyParameters.kdfParameters;
        ICryptoInterface crypto = Crypto.get();
        UserKeyParameters loginUserKeyParams = new UserKeyParameters(kdfParameters, crypto.generateSalt(),
                CryptoSettings.SHA3_256);

        byte[] derivedPassword;

        try {
            derivedPassword = UserKeyParameters.deriveUserKey(password, crypto, loginUserKeyParams).getEncoded();
        } catch (CryptoException e) {
            e.printStackTrace();
            throw new IOException(e.getMessage());
        }

        AccountSettings accountSettings = new AccountSettings(userName, derivedPassword, loginUserKeyParams,
                userDataSettings);
        return jsonRPC.call(METHOD, new JsonRPC.Argument(ACCOUNT_SETTINGS_KEY, accountSettings.toJson()));
    }

    @Override
    protected Result handleJson(JSONObject returnValue, InputStream binaryData) {
        return getResult(returnValue);
    }
}
