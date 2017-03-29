/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.chunkstore;

import org.fejoa.library.crypto.IMessageDigestFactory;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;


public class ChunkHash {
    static private class BufferedHash {
        final MessageDigest hash;
        byte[] buffer = new byte[1024 * 128];
        int bufferSize = 0;

        public BufferedHash(MessageDigest hash) {
            this.hash = hash;
        }

        public void update(byte b) {
            buffer[bufferSize] = b;
            bufferSize++;
            if (bufferSize == buffer.length) {
                hash.update(buffer);
                bufferSize = 0;
            }
        }

        public byte[] digest() {
            if (bufferSize > 0) {
                hash.update(buffer, 0, bufferSize);
                bufferSize = 0;
            }
            return hash.digest();
        }

        public void reset() {
            bufferSize = 0;
            hash.reset();
        }
    }

    private class Layer {
        final ChunkSplitter splitter;
        Layer upperLayer;
        Layer cachedUpperLayer;
        BufferedHash hash;
        // dataHash of the first chunk, only if there are more then one chunk an upper layer is started
        byte[] firstChunkHash;

        public Layer(ChunkSplitter splitter) {
            this.splitter = splitter;
        }

        void reset() {
            this.splitter.reset();
            if (this.upperLayer != null) {
                this.upperLayer.reset();
                this.cachedUpperLayer = this.upperLayer;
            }
            this.upperLayer = null;
            this.hash = null;
            this.firstChunkHash = null;
        }

        void update(byte... data) {
            if (hash == null)
                hash = getMessageDigest();

            //dataHash.update(data);
            for (byte b : data) {
                hash.update(b);
                splitter.update(b);
            }

            if (splitter.isTriggered()) {
                splitter.reset();
                finalizeChunk();
            }
        }

        private void finalizeChunk() {
            if (hash == null)
                return;

            byte[] chunkHash = hash.digest();

            if (firstChunkHash == null && upperLayer == null)
                firstChunkHash = chunkHash;
            else {
                Layer upper = ensureUpperLayer();
                if (firstChunkHash != null) {
                    upper.update(firstChunkHash);
                    firstChunkHash = null;
                }
                upper.update(chunkHash);
            }
            hash = null;
        }

        public byte[] digest() {
            finalizeChunk();
            if (firstChunkHash != null)
                return firstChunkHash;
            // empty data
            if (upperLayer == null)
                return new byte[0];
            return upperLayer.digest();
        }

        Layer ensureUpperLayer() {
            if (upperLayer == null) {
                if (cachedUpperLayer != null)
                    upperLayer = cachedUpperLayer;
                else
                    upperLayer = new Layer(newNodeSplitter());
            }
            return upperLayer;
        }
    }

    final private ChunkSplitter dataSplitter;
    final private ChunkSplitter nodeSplitter;
    private Layer currentLayer;
    final private IMessageDigestFactory messageDigestFactory;

    public ChunkHash(ChunkSplitter dataSplitter, ChunkSplitter nodeSplitter, IMessageDigestFactory messageDigestFactory)
            throws NoSuchAlgorithmException {
        this.dataSplitter = dataSplitter;
        this.nodeSplitter = nodeSplitter;
        this.messageDigestFactory = messageDigestFactory;
        dataSplitter.reset();
        nodeSplitter.reset();

        reset();
    }

    private BufferedHash getMessageDigest() {
        try {
            return new BufferedHash(messageDigestFactory.create());
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public void update(byte[] data) {
        for (byte b : data)
            update(b);
    }

    public void update(byte data) {
        currentLayer.update(data);
    }

    public byte[] digest() {
        return currentLayer.digest();
    }

    public void reset() {
        if (currentLayer != null)
            this.currentLayer.reset();
        else
            currentLayer = new Layer(dataSplitter);
    }

    protected ChunkSplitter newNodeSplitter() {
        return nodeSplitter.newInstance();
    }
}
