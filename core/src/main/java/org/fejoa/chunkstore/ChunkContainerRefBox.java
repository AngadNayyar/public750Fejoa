/*
 * Copyright 2017.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.chunkstore;


import org.fejoa.library.crypto.CryptoException;

import java.io.*;
import java.security.DigestInputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

/**
 * Stores data that links to ChunkContainerRefs, i.e., CommitBox and DirBox
 *
 * This class makes it easier to split ChunkContainerRefs in a plain and in a box part.
 */
abstract public class ChunkContainerRefBox extends TypedBlob {
    private HashValue plainHash = Config.newDataHash();

    protected ChunkContainerRefBox(short type) {
        super(type);
    }

    // implementation has to fill the writtenRefs list
    abstract protected void writePlain(DataOutputStream outputStream, List<ChunkContainerRef> writtenRefs)
            throws IOException;
    // implementation has to fill the readRefs list (only the plain part is read in readPlain)
    abstract protected void readPlain(DataInputStream inputStream, List<ChunkContainerRef> readRefs) throws IOException;

    @Override
    protected HashValue writeInternal(DataOutputStream outputStreamIn, MessageDigest messageDigest)
            throws IOException, CryptoException {
        messageDigest.reset();
        DataOutputStream outputStream = new DataOutputStream(new DigestOutputStream(outputStreamIn, messageDigest));

        List<ChunkContainerRef> writtenRefs = new ArrayList<>();
        writePlain(outputStream, writtenRefs);

        for (ChunkContainerRef ref : writtenRefs)
            ref.getBox().write(outputStream);
        plainHash = new HashValue(messageDigest.digest());
        return plainHash;
    }

    @Override
    protected void readInternal(DataInputStream inputStreamIn, MessageDigest messageDigest) throws IOException {
        messageDigest.reset();
        DataInputStream inputStream = new DataInputStream(new DigestInputStream(inputStreamIn, messageDigest));
        List<ChunkContainerRef> readRefs = new ArrayList<>();
        readPlain(inputStream, readRefs);

        for (ChunkContainerRef ref : readRefs)
            ref.getBox().read(inputStream);

        plainHash = new HashValue(messageDigest.digest());
        // verify that the read hash matches the advertised hash from the ChunkContainerRef
        if (!plainHash.equals(getRef().getDataHash()))
            throw new IOException("Hash miss match!");
    }

    public HashValue getPlainHash() {
        return plainHash;
    }
}
