/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.command;

import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.database.StorageDir;

import java.io.IOException;


public class IncomingCommandQueue extends CommandQueue<CommandQueue.Entry> {
    public IncomingCommandQueue(StorageDir dir) throws IOException {
        super(dir);
    }

    @Override
    protected Entry instantiate() {
        return new Entry();
    }

    public void addCommand(byte[] bytes) throws IOException, CryptoException {
        addCommand(new Entry(bytes));
    }
}
