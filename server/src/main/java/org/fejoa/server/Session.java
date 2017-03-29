/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.server;

import org.fejoa.library.AccessTokenServer;
import org.fejoa.library.BranchAccessRight;
import org.fejoa.library.FejoaContext;
import org.fejoa.library.UserDataSettings;
import org.fejoa.library.command.IncomingCommandQueue;
import org.fejoa.library.crypto.AuthProtocolEKE2_SHA3_256_CTR;
import org.fejoa.library.database.StorageDir;
import org.fejoa.library.remote.AccountSettings;
import org.json.JSONException;
import org.json.JSONObject;

import javax.servlet.http.HttpSession;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;


public class Session {
    final static String LOGIN_SCHNORR_VERIFIER_KEY = "loginSchnorrVerifier";
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
        addRole(userName, "root", BranchAccessRight.ALL);
    }

    public boolean hasRootRole(String serverUser) {
        return getRoles().containsKey(makeRole(serverUser, "root"));
    }

    public void addMigrationRole(String userName) {
        addRole(userName, "migration", BranchAccessRight.PULL_CHUNK_STORE);
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
        return new FejoaContext(getServerUserDir(serverUser), null);
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
        StorageDir tokenDir = new StorageDir(context.getStorage(userDataSettings.accessStore, null, null), tokenId);
        try {
            return AccessTokenServer.open(context, tokenDir);
        } catch (IOException e) {
            // try to read token for the migration process
            JSONObject migrationToken = StartMigrationHandler.readMigrationAccessToken(this, serverUser);
            if (migrationToken == null)
                return null;
            AccessTokenServer accessToken = new AccessTokenServer(getContext(serverUser), migrationToken);
            if (accessToken.getId().equals(tokenId))
                return accessToken;
            else
                return null;
        }
    }

    public void setLoginEKE2Prover(String user, AuthProtocolEKE2_SHA3_256_CTR.ProverState0 prover) {
        session.setAttribute(user + ":" + LOGIN_SCHNORR_VERIFIER_KEY, prover);
    }

    public AuthProtocolEKE2_SHA3_256_CTR.ProverState0 getLoginSchnorrVerifier(String user) {
        return (AuthProtocolEKE2_SHA3_256_CTR.ProverState0)session.getAttribute(user + ":" +LOGIN_SCHNORR_VERIFIER_KEY);
    }
}
