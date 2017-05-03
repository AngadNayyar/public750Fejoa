/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.remote;

import org.bouncycastle.util.encoders.Base64;
import org.fejoa.library.UserDataSettings;
import org.fejoa.library.crypto.UserKeyParameters;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.util.Scanner;


public class AccountSettings {
    static final public String USER_NAME_KEY = "userName";
    static final public String LOGIN_PASSWORD_KEY = "loginPassword";
    static final public String LOGIN_USER_KEY_PARAMS = "loginUserKeyParams";

    static final public String USER_DATA_SETTINGS_KEY = "userDataSettings";

    final public String userName;
    final public byte[] derivedPassword;
    final public UserKeyParameters loginUserKeyParams;

    final public UserDataSettings userDataSettings;

    public AccountSettings(String userName, byte[] derivedPassword, UserKeyParameters loginUserKeyParams,
                           UserDataSettings userDataSettings) {
        this.userName = userName;
        this.derivedPassword = derivedPassword;
        this.loginUserKeyParams = loginUserKeyParams;

        this.userDataSettings = userDataSettings;
    }

    public AccountSettings(JSONObject object) throws JSONException {
        userName = object.getString(USER_NAME_KEY);
        derivedPassword = Base64.decode(object.getString(LOGIN_PASSWORD_KEY));
        loginUserKeyParams = new UserKeyParameters((object.getJSONObject(LOGIN_USER_KEY_PARAMS)));
        userDataSettings = new UserDataSettings(object.getJSONObject(USER_DATA_SETTINGS_KEY));
    }

    static public AccountSettings read(File settingsFile) throws FileNotFoundException, JSONException {
        String content = new Scanner(settingsFile).useDelimiter("\\Z").next();
        return new AccountSettings(new JSONObject(content));
    }

    public void write(File settingsFile) throws IOException {
        Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(settingsFile)));
        writer.write(toJson().toString());
        writer.flush();
        writer.close();
    }

    public JSONObject toJson() {
        JSONObject object = new JSONObject();
        try {
            object.put(USER_NAME_KEY, userName);
            object.put(LOGIN_PASSWORD_KEY, Base64.toBase64String(derivedPassword));
            object.put(LOGIN_USER_KEY_PARAMS, loginUserKeyParams.toJson());
            object.put(USER_DATA_SETTINGS_KEY, userDataSettings.toJson());
        } catch (JSONException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
        return object;
    }
}
