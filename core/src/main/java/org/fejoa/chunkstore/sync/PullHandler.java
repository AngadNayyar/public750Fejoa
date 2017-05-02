/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.chunkstore.sync;

import org.fejoa.chunkstore.ChunkStore;
import org.fejoa.chunkstore.Config;
import org.fejoa.chunkstore.HashValue;
import org.fejoa.library.remote.IRemotePipe;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


public class PullHandler {
    static public void handleGetChunks(ChunkStore.Transaction chunkStore, IRemotePipe pipe, DataInputStream inputStream)
            throws IOException {
        long nRequestedChunks = inputStream.readLong();
        List<HashValue> requestedChunks = new ArrayList<>();
        for (int i = 0; i < nRequestedChunks; i++) {
            HashValue hashValue = Config.newBoxHash();
            inputStream.readFully(hashValue.getBytes());
            requestedChunks.add(hashValue);
        }

        DataOutputStream outputStream = new DataOutputStream(pipe.getOutputStream());
        Request.writeResponseHeader(outputStream, Request.GET_CHUNKS, Request.OK);

        outputStream.writeLong(requestedChunks.size());

        for (HashValue hashValue : requestedChunks) {
            byte[] chunk = chunkStore.getChunk(hashValue);
            //TODO: Return error if chunk is not found
            if (chunk == null)
                throw new IOException("Missing Chunk: " + hashValue.toHex());
            outputStream.write(hashValue.getBytes());
            outputStream.writeInt(chunk.length);
            outputStream.write(chunk);
        }
    }

    static public void handleGetAllChunks(ChunkStore.Transaction chunkStore, IRemotePipe pipe)
            throws IOException {
        DataOutputStream outputStream = new DataOutputStream(pipe.getOutputStream());
        Request.writeResponseHeader(outputStream, Request.GET_ALL_CHUNKS, Request.OK);

        outputStream.writeLong(chunkStore.size());
        ChunkStore.IChunkStoreIterator iterator = chunkStore.iterator();
        while (iterator.hasNext()) {
            ChunkStore.Entry entry = iterator.next();
            outputStream.write(entry.key.getBytes());
            outputStream.writeInt(entry.data.length);
            outputStream.write(entry.data);
        }
        iterator.unlock();
    }
}
