/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.server;

import org.eclipse.jgit.util.Base64;
import org.fejoa.library.UserDataSettings;
import org.fejoa.library.crypto.CryptoSettings;
import org.fejoa.library.crypto.JsonCryptoSettings;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.util.Scanner;


public class AccountSettings {
    static final public String USER_NAME_KEY = "userName";
    static final public String LOGIN_PASSWORD_KEY = "loginPassword";
    static final public String LOGIN_KDF_SALT_KEY = "loginKdfSalt";
    static final public String LOGIN_KDF_SETTINGS_KEY = "loginKdfSettings";

    static final public String USER_DATA_SETTINGS_KEY = "userDataSettings";

    final public String userName;
    final public String derivedPassword;
    final public byte[] salt;
    final public CryptoSettings.Password loginSettings;

    final public UserDataSettings userDataSettings;

    public AccountSettings(String userName, String derivedPassword, byte[] salt, CryptoSettings.Password loginSettings,
                           UserDataSettings userDataSettings) {
        this.userName = userName;
        this.derivedPassword = derivedPassword;
        this.salt = salt;
        this.loginSettings = loginSettings;

        this.userDataSettings = userDataSettings;
    }

    public AccountSettings(JSONObject object) throws JSONException {
        userName = object.getString(USER_NAME_KEY);
        derivedPassword = object.getString(LOGIN_PASSWORD_KEY);
        salt = Base64.decode(object.getString(LOGIN_KDF_SALT_KEY));
        loginSettings = JsonCryptoSettings.passwordFromJson(object.getJSONObject(LOGIN_KDF_SETTINGS_KEY));
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
        String saltBase64 = Base64.encodeBytes(salt);

        JSONObject object = new JSONObject();
        try {
            object.put(USER_NAME_KEY, userName);
            object.put(LOGIN_PASSWORD_KEY, derivedPassword);
            object.put(LOGIN_KDF_SALT_KEY, saltBase64);
            object.put(LOGIN_KDF_SETTINGS_KEY, JsonCryptoSettings.toJson(loginSettings));
            object.put(USER_DATA_SETTINGS_KEY, userDataSettings.toJson());
        } catch (JSONException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
        return object;
    }
}
