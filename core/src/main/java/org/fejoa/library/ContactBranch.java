/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library;


public class ContactBranch {
    final private String branch;
    final private SymmetricKeyData branchKey;
    final private AccessTokenContact accessToken;

    public ContactBranch(String branch, SymmetricKeyData branchKey, AccessTokenContact accessToken) {
        this.branch = branch;
        this.branchKey = branchKey;
        this.accessToken = accessToken;
    }

    public String getBranch() {
        return branch;
    }

    public AccessTokenContact getAccessToken() {
        return accessToken;
    }

    public SymmetricKeyData getBranchKey() {
        return branchKey;
    }
}
