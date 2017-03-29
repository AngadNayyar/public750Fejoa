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


/**
 * Data class.
 */
public class ChunkPointer {
    final private static int LENGTH_SIZE = 8;
    final private static short IV_SIZE = 16;

    private long dataLength;
    private HashValue dataHash;
    private HashValue boxHash;
    private byte[] iv;

    public ChunkPointer() {
        dataHash = Config.newDataHash();
        boxHash = Config.newBoxHash();
        iv = new byte[IV_SIZE];
        dataLength = -1;
    }

    public ChunkPointer(HashValue data, HashValue box, byte[] iv, long length) {
        assert data.size() == Config.DATA_HASH_SIZE && box.size() == Config.BOX_HASH_SIZE && iv.length == IV_SIZE;
        this.dataHash = data;
        this.boxHash = box;
        this.iv = iv;
        this.dataLength = length;
    }

    public ChunkPointer(HashValue data, HashValue box, byte[] iv) {
        this(data, box, iv, 0);
    }

    public ChunkPointer(HashValue data, HashValue box, HashValue iv) {
        this(data, box, iv, 0);
    }

    public ChunkPointer(HashValue data, HashValue box, HashValue iv, long length) {
        this(data, box, getIv(iv.getBytes()), length);
        assert iv.getBytes().length >= IV_SIZE;
    }

    static private byte[] getIv(byte[] hashValue) {
        return Arrays.copyOfRange(hashValue, 0, IV_SIZE);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ChunkPointer))
            return false;
        if (!dataHash.equals(((ChunkPointer) o).dataHash))
            return false;
        if (!Arrays.equals(iv, ((ChunkPointer) o).getIV()))
            return false;
        return boxHash.equals(((ChunkPointer) o).boxHash);
    }

    public long getDataLength() {
        return dataLength;
    }

    public void setDataLength(long dataLength) {
        this.dataLength = dataLength;
    }

    public byte[] getIv() {
        return iv;
    }

    public void setIv(byte[] iv) {
        this.iv = iv;
    }

    public HashValue getDataHash() {
        return dataHash;
    }

    public void setDataHash(HashValue dataHash) {
        this.dataHash = dataHash;
    }

    public HashValue getBoxHash() {
        return boxHash;
    }

    public void setBoxHash(HashValue boxHash) {
        this.boxHash = boxHash;
    }

    public byte[] getIV() {
        return iv;
    }

    static public int getPointerLength() {
        return LENGTH_SIZE + Config.DATA_HASH_SIZE + Config.BOX_HASH_SIZE + IV_SIZE;
    }

    public void read(DataInputStream inputStream) throws IOException {
        dataLength = inputStream.readLong();
        inputStream.readFully(dataHash.getBytes());
        inputStream.readFully(boxHash.getBytes());
        inputStream.readFully(iv);
    }

    public void write(DataOutputStream outputStream) throws IOException {
        outputStream.writeLong(dataLength);
        outputStream.write(dataHash.getBytes());
        outputStream.write(boxHash.getBytes());
        outputStream.write(iv);
    }

    @Override
    public String toString() {
        return "(length: " + dataLength + " data:" + dataHash.toString() + " box:" + boxHash.toString() + ")";
    }
}
