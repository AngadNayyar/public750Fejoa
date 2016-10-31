/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.messaging;

import org.fejoa.library.Constants;
import org.fejoa.library.FejoaContext;
import org.fejoa.library.crypto.CryptoHelper;
import org.fejoa.library.database.IOStorageDir;
import org.fejoa.library.database.MovableStorage;

import java.io.IOException;


public class Message extends MovableStorage {
    final static private String BODY_KEY = "body";

    final private String id;

    static public Message create(FejoaContext context) throws IOException {
        Message message = new Message(null, CryptoHelper.sha1HashHex(context.getCrypto().generateSalt()));
        return message;
    }

    static public Message open(IOStorageDir storageDir) {
        String id = getIdFromDir(storageDir);
        return new Message(storageDir, id);
    }

    private Message(IOStorageDir storageDir, String id) {
        super(storageDir);

        this.id = id;
    }

    public void setBody(String body) throws IOException {
        storageDir.writeString(BODY_KEY, body);
    }

    public String getBody() throws IOException {
        return storageDir.readString(BODY_KEY);
    }

    public String getId() throws IOException {
        return id;
    }
    static public String getIdFromDir(IOStorageDir storageDir) {
        String baseDir = storageDir.getBaseDir();
        int i = baseDir.lastIndexOf("/");
        if (i < 0)
            return "";

        return baseDir.substring(i + 1);
    }
}
