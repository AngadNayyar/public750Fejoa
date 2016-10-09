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
import java.util.Arrays;


public class BoxPointer {
    final private HashValue dataHash;
    final private HashValue boxHash;
    final private static short IV_SIZE = 16;
    final private byte[] iv;

    public BoxPointer() {
        dataHash = Config.newDataHash();
        boxHash = Config.newBoxHash();
        iv = new byte[IV_SIZE];
    }

    public BoxPointer(HashValue data, HashValue box, byte[] iv) {
        assert data.size() == Config.DATA_HASH_SIZE && box.size() == Config.BOX_HASH_SIZE && iv.length == IV_SIZE;
        this.dataHash = data;
        this.boxHash = box;
        this.iv = iv;
    }

    public BoxPointer(HashValue data, HashValue box, HashValue iv) {
        this(data, box, getIv(iv.getBytes()));
        assert iv.getBytes().length >= IV_SIZE;
    }

    static private byte[] getIv(byte[] hashValue) {
        return Arrays.copyOfRange(hashValue, 0, IV_SIZE);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof BoxPointer))
            return false;
        if (!dataHash.equals(((BoxPointer) o).dataHash))
            return false;
        if (!Arrays.equals(iv, ((BoxPointer) o).getIV()))
            return false;
        return boxHash.equals(((BoxPointer) o).boxHash);
    }

    public HashValue getDataHash() {
        return dataHash;
    }

    public HashValue getBoxHash() {
        return boxHash;
    }

    public byte[] getIV() {
        return iv;
    }

    static public int getPointerLength() {
        return Config.DATA_HASH_SIZE + Config.BOX_HASH_SIZE + IV_SIZE;
    }

    public void read(DataInputStream inputStream) throws IOException {
        inputStream.readFully(dataHash.getBytes());
        inputStream.readFully(boxHash.getBytes());
        inputStream.readFully(iv);
    }

    public void write(DataOutputStream outputStream) throws IOException {
        outputStream.write(dataHash.getBytes());
        outputStream.write(boxHash.getBytes());
        outputStream.write(iv);
    }

    @Override
    public String toString() {
        return "(data:" + dataHash.toString() + " box:" + boxHash.toString() + ")";
    }
}
