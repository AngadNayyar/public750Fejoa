/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library;

import junit.framework.TestCase;
import org.fejoa.library.support.StorageLib;
import org.fejoa.library.database.StorageDir;

import java.io.File;
import java.util.ArrayList;
import java.util.List;


public class AccessStoreTest extends TestCase {
    final static String TEST_DIR = "accessStoreTest";

    final private List<String> cleanUpDirs = new ArrayList<>();

    @Override
    public void setUp() throws Exception {
        super.setUp();

        cleanUpDirs.add(TEST_DIR);
        for (String dir : cleanUpDirs)
            StorageLib.recursiveDeleteFile(new File(dir));
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();

        for (String dir : cleanUpDirs)
            StorageLib.recursiveDeleteFile(new File(dir));
    }

    public void testSimple() throws Exception {
        // set up
        FejoaContext context = new FejoaContext(TEST_DIR, null);
        StorageDir serverDir = context.getPlainStorage("server");

        // create token
        AccessToken accessToken = AccessToken.create(context);
        BranchAccessRight accessRight = new BranchAccessRight(BranchAccessRight.CONTACT_ACCESS);
        accessRight.addBranchAccess("branch", BranchAccessRight.PULL);
        accessToken.setAccessEntry(accessRight.toJson().toString());
        accessToken.write(serverDir);
        serverDir.commit();

        // test to reopen it
        serverDir = context.getPlainStorage("server");
        accessToken = AccessToken.open(context, serverDir);
        String contactToken = accessToken.getContactToken().toString();

        // pass it to the contact
        AccessTokenContact accessTokenContact = new AccessTokenContact(context, contactToken);
        assertEquals(accessRight.toJson().toString(), accessTokenContact.getAccessEntry());

        // let the server verify the access
        AccessTokenServer accessTokenServer = AccessTokenServer.open(context, serverDir);
        final String authToken = "testToken";
        byte[] authSignature = accessTokenContact.signAuthToken(authToken);
        assertTrue(accessTokenServer.auth(authToken, authSignature));
        assertTrue(accessTokenServer.verify(accessTokenContact.getAccessEntry(),
                accessTokenContact.getAccessEntrySignature()));
    }
}
