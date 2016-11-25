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

import java.io.*;


public interface IDatabase extends IIODatabase {
    CompletableFuture<HashValue> getHashAsync(String path);
    HashValue getHash(String path) throws CryptoException, IOException;

    String getBranch();
    HashValue getTip();

    CompletableFuture<HashValue> commitAsync(String message, ICommitSignature signature);
    HashValue commit(String message, ICommitSignature signature) throws IOException, CryptoException;

    CompletableFuture<DatabaseDiff> getDiffAsync(HashValue baseCommit, HashValue endCommit);
    DatabaseDiff getDiff(HashValue baseCommit, HashValue endCommit) throws IOException, CryptoException;
}
