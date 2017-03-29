/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library;

import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.database.IOStorageDir;

import java.io.IOException;


public interface IStorageDirBundle {
    void write(IOStorageDir dir) throws IOException, CryptoException;
    void read(IOStorageDir dir) throws IOException, CryptoException;
}
