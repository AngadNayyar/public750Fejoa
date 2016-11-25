/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.database;

import java8.util.concurrent.CompletableFuture;
import org.fejoa.chunkstore.HashValue;
import org.fejoa.library.crypto.CryptoException;

import java.io.IOException;


public class DatabaseDecorator extends IODatabaseDecorator<IDatabase> implements IDatabase {
    public DatabaseDecorator(IDatabase database) {
        super(database);
    }

    @Override
    public CompletableFuture<HashValue> getHashAsync(String path) {
        return database.getHashAsync(path);
    }

    @Override
    public HashValue getHash(String path) throws CryptoException, IOException {
        return database.getHash(path);
    }

    @Override
    public String getBranch() {
        return database.getBranch();
    }

    @Override
    public HashValue getTip() {
        return database.getTip();
    }

    @Override
    public CompletableFuture<HashValue> commitAsync(String message, ICommitSignature signature) {
        return database.commitAsync(message, signature);
    }

    @Override
    public HashValue commit(String message, ICommitSignature signature) throws IOException, CryptoException {
        return database.commit(message, signature);
    }

    @Override
    public CompletableFuture<DatabaseDiff> getDiffAsync(HashValue baseCommit, HashValue endCommit) {
        return database.getDiffAsync(baseCommit, endCommit);
    }

    @Override
    public DatabaseDiff getDiff(HashValue baseCommit, HashValue endCommit) throws IOException, CryptoException {
        return database.getDiff(baseCommit, endCommit);
    }
}
