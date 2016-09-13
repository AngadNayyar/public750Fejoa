/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.server;

import org.fejoa.library.AccessTokenServer;
import org.fejoa.library.FejoaContext;
import org.fejoa.library.UserData;
import org.fejoa.library.UserDataSettings;
import org.fejoa.library.command.IncomingCommandQueue;
import org.fejoa.library.database.StorageDir;
import org.fejoa.library.remote.CreateAccountJob;
import org.fejoa.library.support.StreamHelper;
import org.json.JSONException;
import org.json.JSONObject;

import javax.servlet.http.HttpSession;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;


public class Session {
    final static String ROLES_KEY = "roles";
    static final public String ACCOUNT_INFO_FILE = "account.settings";

    final private String baseDir;
    final private HttpSession session;

    public Session(String baseDir, HttpSession session) {
        this.baseDir = baseDir;
        this.session = session;
    }

    public String getSessionId() {
        return session.getId();
    }

    public String getBaseDir() {
        return baseDir;
    }

    public String getServerUserDir(String serverUser) {
        return getBaseDir() + "/" + serverUser;
    }

    private String makeRole(String serverUser, String role) {
        return serverUser + ":" + role;
    }

    public void addRootRole(String userName) {
        addRole(userName, "root", 0);
    }

    public boolean hasRootRole(String serverUser) {
        return getRoles().containsKey(makeRole(serverUser, "root"));
    }

    public void addMigrationRole(String userName) {
        addRole(userName, "migration", 0);
    }

    public boolean hasMigrationRole(String serverUser) {
        return getRoles().containsKey(makeRole(serverUser, "migration"));
    }

    private Object getSessionLock() {
        // get an immutable lock
        return session.getId().intern();
    }

    public void addRole(String serverUser, String role, Integer rights) {
        synchronized (getSessionLock()) {
            HashMap<String, Integer> roles = getRoles();
            roles.put(makeRole(serverUser, role), rights);
            session.setAttribute(ROLES_KEY, roles);
        }
    }

    public HashMap<String, Integer> getRoles() {
        HashMap<String, Integer> roles = (HashMap<String, Integer>)session.getAttribute(ROLES_KEY);
        if (roles == null)
            return new HashMap<>();
        return roles;
    }

    public int getRoleRights(String serverUser, String role) {
        Integer rights = getRoles().get(makeRole(serverUser, role));
        if (rights == null)
            return 0;
        return rights;
    }

    private File getAccountSettingsFile(String serverUser) {
        return new File(getServerUserDir(serverUser), ACCOUNT_INFO_FILE);
    }

    public AccountSettings getAccountSettings(String serverUser) throws FileNotFoundException, JSONException {
        return AccountSettings.read(getAccountSettingsFile(serverUser));
    }

    public void writeAccountSettings(String serverUser, AccountSettings accountSettings) throws IOException {
        accountSettings.write(getAccountSettingsFile(serverUser));
    }

    public FejoaContext getContext(String serverUser) {
        return new FejoaContext(getServerUserDir(serverUser));
    }

    public UserDataSettings getUserDataSettings(String serverUser) throws Exception {
        return getAccountSettings(serverUser).userDataSettings;
    }

    public IncomingCommandQueue getIncomingCommandQueue(String serverUser) throws Exception {
        FejoaContext context = getContext(serverUser);
        UserDataSettings userDataSettings = getUserDataSettings(serverUser);
        StorageDir incomingQueueDir = context.getPlainStorage(userDataSettings.inQueue);
        return new IncomingCommandQueue(incomingQueueDir);
    }

    public AccessTokenServer getAccessToken(String serverUser, String tokenId) throws Exception {
        FejoaContext context = getContext(serverUser);
        UserDataSettings userDataSettings = getUserDataSettings(serverUser);
        StorageDir tokenDir = new StorageDir(context.getStorage(userDataSettings.accessStore), tokenId);
        try {
            return new AccessTokenServer(context, tokenDir);
        } catch (IOException e) {
            // try to read token for the migration process
            JSONObject migrationToken = StartMigrationHandler.readMigrationAccessToken(this, serverUser);
            AccessTokenServer accessToken = new AccessTokenServer(getContext(serverUser), migrationToken);
            if (accessToken.getId().equals(tokenId))
                return accessToken;
            else
                return null;
        }
    }

}
