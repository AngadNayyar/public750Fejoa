/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.database;

import org.fejoa.library.crypto.CryptoException;

import java.io.IOException;
import java.util.Collection;


public class IOStorageDir implements IIODatabaseInterface {
    private String baseDir;
    final protected IIODatabaseInterface database;

    public IOStorageDir(IIODatabaseInterface database, String baseDir) {
        this.database = database;
        this.baseDir = baseDir;
    }

    public IOStorageDir(IOStorageDir database, String baseDir, boolean absoluteBaseDir) {
        this.database = database;

        if (absoluteBaseDir)
            this.baseDir = baseDir;
        else
            this.baseDir = StorageDir.appendDir(database.baseDir, baseDir);
    }

    public String getBaseDir() {
        return baseDir;
    }

    private String getRealPath(String path) {
        return StorageDir.appendDir(getBaseDir(), path);
    }

    @Override
    public boolean hasFile(String path) throws IOException, CryptoException {
        return database.hasFile(getRealPath(path));
    }

    @Override
    public byte[] readBytes(String path) throws IOException, CryptoException {
        return database.readBytes(getRealPath(path));
    }

    @Override
    public void writeBytes(String path, byte[] bytes) throws IOException, CryptoException {
        database.writeBytes(getRealPath(path), bytes);
    }

    @Override
    public void remove(String path) throws IOException, CryptoException {
        database.remove(getRealPath(path));
    }

    @Override
    public Collection<String> listFiles(String path) throws IOException, CryptoException {
        return database.listFiles(getRealPath(path));
    }

    @Override
    public Collection<String> listDirectories(String path) throws IOException, CryptoException {
        return database.listDirectories(getRealPath(path));
    }

    private byte[] readBytesInternal(String path) throws IOException {
        try {
            return readBytes(path);
        } catch (CryptoException e) {
            throw new IOException(e.getMessage());
        }
    }

    private void writeBytesInternal(String path, byte[] bytes) throws IOException {
        try {
            writeBytes(path, bytes);
        } catch (CryptoException e) {
            throw new IOException(e.getMessage());
        }
    }

    public String readString(String path) throws IOException {
        return new String(readBytesInternal(path));
    }

    public int readInt(String path) throws IOException {
        return Integer.parseInt(readString(path));
    }

    public long readLong(String path) throws IOException {
        return Long.parseLong(readString(path));
    }

    public void writeString(String path, String data) throws IOException {
        writeBytesInternal(path, data.getBytes());
    }

    public void writeInt(String path, int data) throws IOException {
        String dataString = "";
        dataString += data;
        writeString(path, dataString);
    }

    public void writeLong(String path, long data) throws IOException {
        String dataString = "";
        dataString += data;
        writeString(path, dataString);
    }
}
