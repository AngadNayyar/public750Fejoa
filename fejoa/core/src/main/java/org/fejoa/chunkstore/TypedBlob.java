/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.chunkstore;

import org.fejoa.library.crypto.CryptoException;

import java.io.*;
import java.security.MessageDigest;


/**
 * Blob to be written to a ChunkContainer.
 */
abstract public class TypedBlob {
    final protected short type;
    private ChunkContainerRef ref;

    protected TypedBlob(short type) {
        this.type = type;
    }

    public ChunkContainerRef getRef() {
        return ref;
    }

    public void setRef(ChunkContainerRef ref) {
        this.ref = ref;
    }

    abstract protected void readInternal(DataInputStream inputStream, MessageDigest messageDigest) throws IOException;

    /**
     * Write the blob content to the outputStream.
     *
     * @param outputStream
     * @return the HashValue associated with the plain data.
     * @throws IOException
     * @throws CryptoException
     */
    abstract protected HashValue writeInternal(DataOutputStream outputStream, MessageDigest messageDigest)
            throws IOException, CryptoException;

    public void read(DataInputStream inputStream, ChunkContainerRef ref) throws IOException {
        this.ref = ref;
        short t = inputStream.readShort();
        assert t == type;
        MessageDigest messageDigest = ref.getDataMessageDigest();
        readInternal(inputStream, messageDigest);
    }

    /**
     *
     * @param outputStream
     * @return the HashValue associated with the plain data.
     * @throws IOException
     * @throws CryptoException
     */
    public HashValue write(DataOutputStream outputStream, ChunkContainerRef ref)
            throws IOException, CryptoException {
        this.ref = ref;
        outputStream.writeShort(type);
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        DataOutputStream dataOut = new DataOutputStream(byteArrayOutputStream);
        MessageDigest messageDigest = ref.getDataMessageDigest();
        HashValue dataHash = writeInternal(dataOut, messageDigest);
        dataOut.close();
        byte[] data = byteArrayOutputStream.toByteArray();
        outputStream.write(data);
        return dataHash;
    }
}
