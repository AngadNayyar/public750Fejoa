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
import java.util.Collection;


public interface IIODatabaseInterface {
    boolean hasFile(String path) throws IOException, CryptoException;
    byte[] readBytes(String path) throws IOException, CryptoException;
    void writeBytes(String path, byte[] bytes) throws IOException, CryptoException;
    void remove(String path) throws IOException, CryptoException;

    Collection<String> listFiles(String path) throws IOException, CryptoException;
    Collection<String> listDirectories(String path) throws IOException, CryptoException;
}
