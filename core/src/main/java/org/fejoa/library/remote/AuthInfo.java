/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.remote;

import org.fejoa.library.AccessTokenContact;
import org.fejoa.library.FejoaContext;
import org.fejoa.library.Remote;
import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.database.IOStorageDir;

import java.io.IOException;


abstract public class AuthInfo {
    static public class Plain extends AuthInfo {
        public Plain() {
            super(PLAIN);
        }

        @Override
        String getId() {
            return PLAIN;
        }
    }

    static public class Password extends AuthInfo {
        final public FejoaContext context;
        final public String password;

        public Password(FejoaContext context, String password) {
            super(PASSWORD);
            this.context = context;
            this.password = password;
        }

        @Override
        String getId() {
            return PASSWORD;
        }
    }

    static public class Token extends AuthInfo {
        final public AccessTokenContact token;

        public Token(AccessTokenContact token) {
            super(TOKEN);
            this.token = token;
        }

        @Override
        public void write(IOStorageDir dir) throws IOException {
            super.write(dir);

            dir.writeString(AUTH_RAW_TOKEN_KEY, token.toJson().toString());
        }

        @Override
        String getId() {
            return token.getId();
        }

        public AccessTokenContact getToken() {
            return token;
        }
    }

    final static public String AUTH_TYPE_KEY = "authType";
    final static public String AUTH_RAW_TOKEN_KEY = "token";

    static public AuthInfo read(IOStorageDir storageDir, Remote remote, FejoaContext context)
            throws IOException, CryptoException {
        String type = storageDir.readString(AUTH_TYPE_KEY);
        switch (type) {
            case PLAIN:
                return new Plain();
            case PASSWORD:
                String password = context.getRootPassword(remote.getUser(), remote.getServer());
                if (password == null)
                    password = "";
                return new AuthInfo.Password(context, password);
            case TOKEN:
                String rawToken = storageDir.readString(AUTH_RAW_TOKEN_KEY);
                AccessTokenContact tokenContact = new AccessTokenContact(context, rawToken);
                return new Token(tokenContact);
            default:
                return null;
        }
    }

    final static public String PLAIN = "plain";
    final static public String PASSWORD = "password";
    final static public String TOKEN = "token";

    final public String authType;

    protected AuthInfo(String type) {
        this.authType = type;
    }

    public void write(IOStorageDir dir) throws IOException {
        dir.writeString(AUTH_TYPE_KEY, authType);
    }

    abstract String getId();
}
