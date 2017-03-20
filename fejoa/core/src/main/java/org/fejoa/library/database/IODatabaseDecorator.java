/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.database;


import java8.util.concurrent.CompletableFuture;
import org.fejoa.library.crypto.CryptoException;

import java.io.IOException;
import java.util.Collection;

public class IODatabaseDecorator<T extends IIODatabase> implements IIODatabase {
    final protected T database;

    public IODatabaseDecorator(T database) {
        this.database = database;
    }

    @Override
    public CompletableFuture<Boolean> hasFileAsync(String path) {
        return database.hasFileAsync(path);
    }

    @Override
    public CompletableFuture<IRandomDataAccess> openAsync(String path, Mode mode) {
        return database.openAsync(path, mode);
    }

    @Override
    public CompletableFuture<Void> removeAsync(String path) {
        return database.removeAsync(path);
    }

    @Override
    public CompletableFuture<byte[]> readBytesAsync(String path) {
        return database.readBytesAsync(path);
    }

    @Override
    public CompletableFuture<Void> putBytesAsync(String path, byte[] data) {
        return database.putBytesAsync(path, data);
    }

    @Override
    public CompletableFuture<Collection<String>> listFilesAsync(String path) {
        return database.listFilesAsync(path);
    }

    @Override
    public CompletableFuture<Collection<String>> listDirectoriesAsync(String path) {
        return database.listDirectoriesAsync(path);
    }

    @Override
    public boolean hasFile(String path) throws IOException, CryptoException {
        return database.hasFile(path);
    }

    @Override
    public ISyncRandomDataAccess open(String path, Mode mode) throws IOException, CryptoException {
        return database.open(path, mode);
    }

    @Override
    public byte[] readBytes(String path) throws IOException, CryptoException {
        return database.readBytes(path);
    }

    @Override
    public void putBytes(String path, byte[] data) throws IOException, CryptoException {
        database.putBytes(path, data);
    }

    @Override
    public void remove(String path) throws IOException, CryptoException {
        database.remove(path);
    }

    @Override
    public Collection<String> listFiles(String path) throws IOException, CryptoException {
        return database.listFiles(path);
    }

    @Override
    public Collection<String> listDirectories(String path) throws IOException, CryptoException {
        return database.listDirectories(path);
    }
}
