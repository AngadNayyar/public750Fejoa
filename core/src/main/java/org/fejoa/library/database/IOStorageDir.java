/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.database;

import java8.util.concurrent.CompletableFuture;
import java8.util.function.BiConsumer;
import java8.util.function.Function;
import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.support.StorageLib;
import org.fejoa.library.support.StreamHelper;

import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.ExecutionException;

import static org.fejoa.library.database.IIOSyncDatabase.Mode.READ;
import static org.fejoa.library.database.IIOSyncDatabase.Mode.TRUNCATE;


public class IOStorageDir {
    private String baseDir;
    final protected IIODatabase database;

    public IOStorageDir(IIODatabase database, String baseDir) {
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

    protected String getRealPath(String path) {
        return StorageLib.appendDir(getBaseDir(), path);
    }

    public boolean hasFile(String path) throws IOException, CryptoException {
        return database.hasFile(getRealPath(path));
    }

    static public byte[] readBytes(IIOSyncDatabase database, String path) throws IOException, CryptoException {
        ISyncRandomDataAccess randomDataAccess = database.open(path, READ);
        byte[] date = StreamHelper.readAll(randomDataAccess);
        randomDataAccess.close();
        return date;
    }

    static public void putBytes(IIOSyncDatabase database, String path, byte[] bytes) throws IOException, CryptoException {
        ISyncRandomDataAccess randomDataAccess = database.open(path, TRUNCATE);
        randomDataAccess.write(bytes);
        randomDataAccess.close();
    }

    public byte[] readBytes(String path) throws IOException, CryptoException {
        return readBytes(database, getRealPath(path));
    }

    public void putBytes(String path, byte[] bytes) throws IOException, CryptoException {
        putBytes(database, getRealPath(path), bytes);
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
            putBytes(path, bytes);
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
            target.putBytes(path, readBytes(path));
        }
        for (String dir : listDirectories(currentDir))
            copyTo(target, StorageLib.appendDir(currentDir, dir));
    }

    public IRandomDataAccess open(String path, IIOSyncDatabase.Mode mode) throws IOException, CryptoException {
        try {
            return database.openAsync(getRealPath(path), mode).get();
        } catch (Exception e) {
            if (e.getCause() instanceof IOException)
                throw (IOException)e.getCause();
            else if (e.getCause() instanceof CryptoException)
                throw (CryptoException)e.getCause();
            else
                throw new RuntimeException("Unexpected Exception");
        }
    }

    public CompletableFuture<IRandomDataAccess> openAsync(String path, IIOSyncDatabase.Mode mode) {
        return database.openAsync(getRealPath(path), mode);
    }

    public CompletableFuture<Void> putBytesAsync(String path, byte[] data) {
        return database.putBytesAsync(getRealPath(path), data);
    }

    public CompletableFuture<Void> putStringAsync(String path, String data) {
        return putBytesAsync(path, data.getBytes());
    }

    public CompletableFuture<Void> putIntAsync(String path, int data) {
        String dataString = "";
        dataString += data;
        return putStringAsync(path, dataString);
    }

    public CompletableFuture<Void> putLongAsync(String path, long data) {
        String dataString = "";
        dataString += data;
        return putStringAsync(path, dataString);
    }

    public CompletableFuture<byte[]> readBytesAsync(String path) {
        return database.readBytesAsync(getRealPath(path));
    }

    public CompletableFuture<String> readStringAsync(String path) {
        return readBytesAsync(path).thenApply(new Function<byte[], String>() {
            @Override
            public String apply(byte[] bytes) {
                return new String(bytes);
            }
        });
    }

    public CompletableFuture<Integer> readIntAsync(String path) {
        return readStringAsync(path).thenApply(new Function<String, Integer>() {
            @Override
            public Integer apply(String s) {
                return Integer.parseInt(s);
            }
        });
    }

    public CompletableFuture<Long> readLongAsync(String path) {
        return readStringAsync(path).thenApply(new Function<String, Long>() {
            @Override
            public Long apply(String s) {
                return Long.parseLong(s);
            }
        });
    }

    public CompletableFuture<Collection<String>> listFilesAsync(String path) {
        return database.listFilesAsync(getRealPath(path));
    }

    public CompletableFuture<Collection<String>> listDirectoriesAsync(String path) {
        return database.listDirectoriesAsync(getRealPath(path));
    }
}
