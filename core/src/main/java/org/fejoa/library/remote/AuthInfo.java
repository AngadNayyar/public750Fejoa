/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.remote;

import org.fejoa.library.AccessTokenContact;


public class AuthInfo {
    static public class Plain extends AuthInfo {
        public Plain() {
            super(PLAIN);
        }
    }

    static public class Password extends AuthInfo {
        final public String password;

        public Password(String password) {
            super(PASSWORD);
            this.password = password;
        }
    }

    static public class Token extends AuthInfo {
        final public AccessTokenContact token;

        public Token(AccessTokenContact token) {
            super(TOKEN);
            this.token = token;
        }
    }

    final static public String PLAIN = "plain";
    final static public String PASSWORD = "password";
    final static public String TOKEN = "token";

    final public String authType;

    protected AuthInfo(String type) {
        this.authType = type;
    }
}
