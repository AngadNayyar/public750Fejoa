/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.chunkstore;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;


public class BoxPointer {
    private HashValue dataHash;
    private HashValue boxHash;

    public BoxPointer() {
        dataHash = Config.newDataHash();
        boxHash = Config.newBoxHash();
    }

    public BoxPointer(HashValue data, HashValue box) {
        assert data.size() == Config.DATA_HASH_SIZE && box.size() == Config.BOX_HASH_SIZE;
        this.dataHash = data;
        this.boxHash = box;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof BoxPointer))
            return false;
        if (!dataHash.equals(((BoxPointer) o).dataHash))
            return false;
        return boxHash.equals(((BoxPointer) o).boxHash);
    }

    public HashValue getDataHash() {
        return dataHash;
    }

    public HashValue getBoxHash() {
        return boxHash;
    }

    static public int getPointerLength() {
        return Config.DATA_HASH_SIZE + Config.BOX_HASH_SIZE;
    }

    public void read(DataInputStream inputStream) throws IOException {
        inputStream.readFully(dataHash.getBytes());
        inputStream.readFully(boxHash.getBytes());
    }

    public void write(DataOutputStream outputStream) throws IOException {
        outputStream.write(dataHash.getBytes());
        outputStream.write(boxHash.getBytes());
    }

    @Override
    public String toString() {
        return "(data:" + dataHash.toString() + " box:" + boxHash.toString() + ")";
    }
}
