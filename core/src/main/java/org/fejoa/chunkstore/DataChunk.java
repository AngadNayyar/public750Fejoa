/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.chunkstore;

import org.fejoa.library.crypto.CryptoHelper;
import org.fejoa.library.support.StreamHelper;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.MessageDigest;


public class DataChunk implements IChunk {
    protected byte[] data;

    public DataChunk() {
    }

    public DataChunk(byte[] data) {
        this.data = data;
    }

    @Override
    public HashValue hash(MessageDigest messageDigest) {
        return new HashValue(CryptoHelper.hash(data, messageDigest));
    }

    @Override
    public void read(DataInputStream inputStream, long dataLength) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        StreamHelper.copyBytes(inputStream, outputStream, (int)dataLength);
        this.data = outputStream.toByteArray();
    }

    @Override
    public void write(DataOutputStream outputStream) throws IOException {
        outputStream.write(data);
    }

    @Override
    public byte[] getData() throws IOException {
        return data;
    }

    @Override
    public long getDataLength() {
        return data.length;
    }
}
