/*
 * Copyright 2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.database;

import org.fejoa.chunkstore.HashValue;
import org.fejoa.library.crypto.CryptoException;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;


public interface IDatabaseInterface {
    String getBranch();
    HashValue getTip() throws IOException;

    HashValue getHash(String path) throws IOException, CryptoException;
    byte[] readBytes(String path) throws IOException, CryptoException;
    void writeBytes(String path, byte[] bytes) throws IOException, CryptoException;
    void remove(String path) throws IOException, CryptoException;

    List<String> listFiles(String path) throws IOException, CryptoException;
    List<String> listDirectories(String path) throws IOException, CryptoException;

    HashValue commit(String message, ICommitSignature commitSignature) throws IOException, CryptoException;
    DatabaseDiff getDiff(HashValue baseCommit, HashValue endCommit) throws IOException, CryptoException;
}
