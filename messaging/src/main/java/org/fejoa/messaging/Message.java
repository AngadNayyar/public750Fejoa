/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.messaging;

import org.fejoa.library.ContactPrivate;
import org.fejoa.library.FejoaContext;
import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.crypto.CryptoHelper;
import org.fejoa.library.database.IOStorageDir;
import org.fejoa.library.database.MovableStorage;
import org.fejoa.library.support.StorageLib;

import java.io.IOException;
import java.util.Collection;


public class Message extends MovableStorage {
    final static private String TIME_KEY = "time";
    final static private String SENDER_KEY = "sender";
    final static private String BODY_KEY = "body";

    final private String id;

    static public Message create(FejoaContext context, ContactPrivate myself) throws IOException {
        Message message = new Message(null, CryptoHelper.sha1HashHex(context.getCrypto().generateSalt()));
        message.setTime(System.currentTimeMillis());
        message.setSender(myself.getId());
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

    final static private String ATTACHMENT_DIR = "attachments";
    final static private String DATA_KEY = "data";
    final static private String MIME_TYPE_KEY = "mime";

    private IOStorageDir getAttachmentDir(String name) {
        return new IOStorageDir(storageDir, StorageLib.appendDir(ATTACHMENT_DIR, name));
    }

    public void addAttachment(String name, byte[] attachment, String mime) throws IOException, CryptoException {
        IOStorageDir attachmentDir = getAttachmentDir(name);
        attachmentDir.putBytes(DATA_KEY, attachment);
        attachmentDir.writeString(MIME_TYPE_KEY, mime);
    }

    public byte[] getAttachmentData(String name) throws IOException, CryptoException {
        IOStorageDir attachmentDir = getAttachmentDir(name);
        return attachmentDir.readBytes(DATA_KEY);
    }

    public String getAttachmentMimeType(String name) throws IOException, CryptoException {
        IOStorageDir attachmentDir = getAttachmentDir(name);
        return attachmentDir.readString(MIME_TYPE_KEY);
    }

    public Collection<String> listAttachments() throws IOException, CryptoException {
        return storageDir.listDirectories(ATTACHMENT_DIR);
    }

    public String getBody() throws IOException {
        return storageDir.readString(BODY_KEY);
    }

    public void setTime(long time) throws IOException {
        storageDir.writeLong(TIME_KEY, time);
    }

    public long getTime() throws IOException {
        return storageDir.readLong(TIME_KEY);
    }

    public void setSender(String senderId) throws IOException {
        storageDir.writeString(SENDER_KEY, senderId);
    }

    public String getSender() throws IOException {
        return storageDir.readString(SENDER_KEY);
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
