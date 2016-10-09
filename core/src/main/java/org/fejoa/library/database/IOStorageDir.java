/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.database;

import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.support.StorageLib;

import java.io.IOException;
import java.util.Collection;


public class IOStorageDir {
    private String baseDir;
    final protected IIODatabaseInterface database;

    public IOStorageDir(IIODatabaseInterface database, String baseDir) {
        this.database = database;
        this.baseDir = baseDir;
    }

    public IOStorageDir(IOStorageDir storageDir, String baseDir) {
        this(storageDir, baseDir, false);
    }

    public IOStorageDir(IOStorageDir storageDir, String baseDir, boolean absoluteBaseDir) {
        this.database = storageDir.database;

        if (absoluteBaseDir)
            this.baseDir = baseDir;
        else
            this.baseDir = StorageLib.appendDir(storageDir.baseDir, baseDir);
    }

    public String getBaseDir() {
        return baseDir;
    }

    private String getRealPath(String path) {
        return StorageLib.appendDir(getBaseDir(), path);
    }

    public boolean hasFile(String path) throws IOException, CryptoException {
        return database.hasFile(getRealPath(path));
    }

    public byte[] readBytes(String path) throws IOException, CryptoException {
        return database.readBytes(getRealPath(path));
    }

    public void writeBytes(String path, byte[] bytes) throws IOException, CryptoException {
        database.writeBytes(getRealPath(path), bytes);
    }

    public void remove(String path) throws IOException, CryptoException {
        database.remove(getRealPath(path));
    }

    public Collection<String> listFiles(String path) throws IOException, CryptoException {
        return database.listFiles(getRealPath(path));
    }

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

    public void copyTo(IOStorageDir target) throws IOException, CryptoException {
        copyTo(target, "");
    }

    private void copyTo(IOStorageDir target, String currentDir) throws IOException, CryptoException {
        for (String file : listFiles(currentDir)) {
            String path = StorageLib.appendDir(currentDir, file);
            target.writeBytes(path, readBytes(path));
        }
        for (String dir : listDirectories(currentDir))
            copyTo(target, StorageLib.appendDir(currentDir, dir));
    }
}
