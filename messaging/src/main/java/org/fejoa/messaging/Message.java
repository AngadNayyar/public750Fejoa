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

    static public Message open(IOStorageDir storageDir) {
        return new Message(storageDir);
    }

    static public Message create(FejoaContext context) throws IOException {
        Message message = new Message(null);
        message.setId(CryptoHelper.sha1HashHex(context.getCrypto().generateSalt()));
        return message;
    }

    private Message(IOStorageDir storageDir) {
        super(storageDir);
        this.storageDir = storageDir;
    }

    public void setBody(String body) throws IOException {
        storageDir.writeString(BODY_KEY, body);
    }

    public String getBody() throws IOException {
        return storageDir.readString(BODY_KEY);
    }

    private void setId(String id) throws IOException {
        storageDir.writeString(Constants.ID_KEY, id);
    }

    public String getId() throws IOException {
        return storageDir.readString(Constants.ID_KEY);
    }
}
