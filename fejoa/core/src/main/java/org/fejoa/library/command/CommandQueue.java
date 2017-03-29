/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.command;

import org.fejoa.library.Constants;
import org.fejoa.library.IStorageDirBundle;
import org.fejoa.library.crypto.CryptoHelper;
import org.fejoa.library.database.IOStorageDir;
import org.fejoa.library.database.StorageDir;
import org.fejoa.library.crypto.CryptoException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;


abstract class CommandQueue<T extends CommandQueue.Entry> {
    static class Entry implements IStorageDirBundle {
        final static private String COMMAND_KEY = "command";

        private byte[] data;

        public Entry() {
        }

        public Entry(byte[] data) {
            this.data = data;
        }

        public byte[] getData() {
            return data;
        }

        public String hash() {
            return CryptoHelper.sha1HashHex(getData());
        }

        @Override
        public String toString() {
            return hash();
        }

        @Override
        public void write(IOStorageDir dir) throws IOException, CryptoException {
            dir.putBytes(COMMAND_KEY, data);
        }

        @Override
        public void read(IOStorageDir dir) throws IOException, CryptoException {
            this.data = dir.readBytes(COMMAND_KEY);
        }
    }

    final protected StorageDir storageDir;

    public CommandQueue(StorageDir dir) throws IOException {
        this.storageDir = new StorageDir(dir);

        String oldIdKey = null;
        try {
            oldIdKey = dir.readString(Constants.ID_KEY);
        } catch (IOException e) {

        }
        if (oldIdKey == null || !oldIdKey.equals(dir.getBranch()))
            dir.writeString(Constants.ID_KEY, dir.getBranch());
    }

    public String getId() {
        return storageDir.getBranch();
    }

    public void commit() throws IOException {
        storageDir.commit();
    }

    public StorageDir getStorageDir() {
        return storageDir;
    }

    protected void addCommand(T command) throws IOException, CryptoException {
        IOStorageDir dir = new IOStorageDir(storageDir, command.hash());
        command.write(dir);
    }

    public List<T> getCommands() throws IOException, CryptoException {
        List<T> commands = new ArrayList<>();
        getCommands(storageDir, commands);
        return commands;
    }

    private void getCommands(IOStorageDir dir, List<T> list) throws IOException, CryptoException {
        Collection<String> hashes = dir.listDirectories("");
        for (String hash : hashes) {
            try {
                T entry = instantiate();
                entry.read(new IOStorageDir(dir, hash));
                list.add(entry);
            } catch (IOException e) {
                dir.remove(hash);
            }
        }
    }

    public void removeCommand(T command) throws IOException, CryptoException {
        removeCommand(command.hash());
    }

    public void removeCommand(String id) throws IOException, CryptoException {
        storageDir.remove(id);
    }

    abstract protected T instantiate();
}


