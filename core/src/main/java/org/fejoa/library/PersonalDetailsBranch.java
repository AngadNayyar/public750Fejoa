/*
 * Copyright 2017.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library;

import java8.util.concurrent.CompletableFuture;
import java8.util.concurrent.CompletionStage;
import java8.util.function.Function;
import org.fejoa.chunkstore.HashValue;
import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.database.DBJsonObject;
import org.fejoa.library.database.DBObjectContainer;
import org.fejoa.library.database.StorageDir;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Collection;

import static org.fejoa.library.PersonalDetailsManager.PERSONAL_DETAILS_CONTEXT;


public class PersonalDetailsBranch extends DBObjectContainer {
    final private StorageDir detailsBranch;
    final private DBJsonObject details = new DBJsonObject(DETAILS_KEY);
    final static private String DETAILS_KEY = "details";

    public PersonalDetailsBranch(StorageDir detailsBranch) {
        this.detailsBranch = detailsBranch;
        add(details);
        details.setTo(detailsBranch);
    }

    public CompletableFuture<JSONObject> getDetails() {
        return details.get().exceptionally(new Function<Throwable, JSONObject>() {
            @Override
            public JSONObject apply(Throwable throwable) {
                return new JSONObject();
            }
        });
    }

    public void setDetails(JSONObject object) {
        details.set(object);
    }

    public String getBranch() {
        return detailsBranch.getBranch();
    }

    public StorageDir getStorageDir() {
        return detailsBranch;
    }

    public CompletableFuture<HashValue> commit() {
        return flush().thenCompose(new Function<Void, CompletionStage<HashValue>>() {
            @Override
            public CompletionStage<HashValue> apply(Void aVoid) {
                return detailsBranch.commitAsync();
            }
        });
    }

    public void publishDetails(Client client, Collection<ContactPublic> contacts)
            throws IOException, CryptoException {
        UserData userData = client.getUserData();
        BranchInfo branchInfo = userData.getBranchList().get(getBranch(), PERSONAL_DETAILS_CONTEXT);
        // grant access to the contacts
        for (ContactPublic contactPublic : contacts)
            client.grantAccess(branchInfo, BranchAccessRight.PULL, contactPublic);
    }
}
