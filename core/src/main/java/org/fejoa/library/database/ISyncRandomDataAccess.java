/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.database;

import org.fejoa.library.crypto.CryptoException;

import java.io.IOException;


public interface ISyncRandomDataAccess {
    long length();
    long position();
    void seek(long position) throws IOException, CryptoException;
    void write(byte[] data) throws IOException;
    int read(byte[] buffer) throws IOException, CryptoException;
    void write(byte[] data, int offset, int length) throws IOException;
    int read(byte[] buffer, int offset, int length) throws IOException, CryptoException;
    void flush() throws IOException;
    void close() throws IOException, CryptoException;
}
