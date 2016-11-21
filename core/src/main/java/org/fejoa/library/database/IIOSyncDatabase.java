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


public interface IIOSyncDatabase {
    enum Mode {
        READ(0x01),
        WRITE(0x02),
        TRUNCATE((WRITE.getValue() | 0x04));

        private int mode;

        Mode(int mode) {
            this.mode = mode;
        }

        private int getValue() {
            return mode;
        }

        public void add(Mode otherMode) {
            this.mode |= otherMode.getValue();
        }

        public boolean has(Mode otherMode) {
            return (this.mode & otherMode.getValue()) != 0;
        }
    }

    boolean hasFile(String path) throws IOException, CryptoException;

    ISyncRandomDataAccess open(String path, Mode mode) throws IOException, CryptoException;

    byte[] readBytes(String path) throws IOException, CryptoException;
    void putBytes(String path, byte[] data) throws IOException, CryptoException;

    void remove(String path) throws IOException, CryptoException;

    Collection<String> listFiles(String path) throws IOException, CryptoException;
    Collection<String> listDirectories(String path) throws IOException, CryptoException;
}
