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


public interface ISyncDatabase extends IIOSyncDatabase {
    String getBranch();
    HashValue getTip();
    HashValue getHash(String path) throws IOException, CryptoException;

    HashValue commit(String message, ICommitSignature commitSignature) throws IOException, CryptoException;
    DatabaseDiff getDiff(HashValue baseCommit, HashValue endCommit) throws IOException, CryptoException;
}
