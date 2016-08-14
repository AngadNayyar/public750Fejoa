/*
 * Copyright 2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.database;

import org.fejoa.chunkstore.HashValue;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;


public interface IDatabaseInterface {
    String getBranch();

    HashValue getHash(String path) throws IOException;
    byte[] readBytes(String path) throws IOException;
    void writeBytes(String path, byte[] bytes) throws IOException;

    void remove(String path) throws IOException;

    HashValue commit() throws IOException;

    List<String> listFiles(String path) throws IOException;
    List<String> listDirectories(String path) throws IOException;

    HashValue getTip() throws IOException;

    void merge(HashValue theirCommitId) throws IOException;

    DatabaseDiff getDiff(HashValue baseCommit, HashValue endCommit) throws IOException;
}
