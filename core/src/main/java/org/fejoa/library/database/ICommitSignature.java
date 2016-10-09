/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.database;

import org.fejoa.chunkstore.HashValue;
import org.fejoa.library.crypto.CryptoException;

import java.util.Collection;

public interface ICommitSignature {
    String signMessage(String message, HashValue rootHashValue, Collection<HashValue> parents) throws CryptoException;
    boolean verifySignedMessage(String signedMessage, HashValue rootHashValue, Collection<HashValue> parents);
}
