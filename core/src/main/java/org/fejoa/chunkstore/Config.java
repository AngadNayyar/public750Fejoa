/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.chunkstore;


public class Config {
    final public static short SHA1_SIZE = 20;
    final public static short SHA256_SIZE = 32;

    static public HashValue newSha1Hash() {
        return new HashValue(SHA1_SIZE);
    }

    static final public short BOX_HASH_SIZE = SHA256_SIZE;
    static public HashValue newBoxHash() {
        return new HashValue(BOX_HASH_SIZE);
    }

    static final public short DATA_HASH_SIZE = SHA256_SIZE;
    static public HashValue newDataHash() {
        return new HashValue(DATA_HASH_SIZE);
    }
}
