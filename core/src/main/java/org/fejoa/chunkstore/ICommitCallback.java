/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.chunkstore;

import org.fejoa.library.crypto.CryptoException;


public interface ICommitCallback {
    HashValue logHash(ChunkContainerRef commitPointer);

    String commitPointerToLog(ChunkContainerRef commitPointer) throws CryptoException;

    ChunkContainerRef commitPointerFromLog(String logEntry) throws CryptoException;
}
