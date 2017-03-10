/*
 * Copyright 2017.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.chunkstore;

import org.fejoa.library.crypto.CryptoHelper;
import org.fejoa.library.crypto.IMessageDigestFactory;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;


public class ChunkContainerRef {
    static public class Data {
        private HashValue dataHash = Config.newDataHash();
        private ChunkContainerHeader containerHeader = new ChunkContainerHeader();

        @Override
        protected Data clone() {
            Data data = new Data();
            data.dataHash = new HashValue(dataHash);
            data.containerHeader = containerHeader.clone();
            return data;
        }

        public void write(OutputStream outputStream) throws IOException {
            outputStream.write(dataHash.getBytes());
            ChunkContainerHeaderIO.write(containerHeader, outputStream);
        }

        public void read(InputStream inputStream) throws IOException {
            dataHash = Config.newDataHash();
            new DataInputStream(inputStream).readFully(dataHash.getBytes());
            containerHeader = new ChunkContainerHeader();
            ChunkContainerHeaderIO.read(containerHeader, inputStream);
        }

        public ChunkContainerHeader getContainerHeader() {
            return containerHeader;
        }

        public HashValue getDataHash() {
            return dataHash;
        }

        public void setDataHash(HashValue dataHash) {
            this.dataHash = dataHash;
        }
    }

    static public class Box {
        private HashValue boxHash = Config.newBoxHash();
        private byte[] iv;
        private BoxHeader boxHeader = new BoxHeader();

        @Override
        protected Box clone() {
            Box box = new Box();
            box.boxHash = new HashValue(boxHash);
            if (iv != null)
                box.iv = Arrays.copyOf(iv, iv.length);
            box.boxHeader = boxHeader.clone();
            return box;
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

        public void setIv(byte[] iv) {
            this.iv = iv;
        }

        public BoxHeader getBoxHeader() {
            return boxHeader;
        }

        public void write(OutputStream outputStream) throws IOException {
            outputStream.write(boxHash.getBytes());
            VarInt.write(outputStream, iv.length);
            outputStream.write(iv);

            BoxHeaderIO.write(boxHeader, outputStream);
        }

        public void read(InputStream inputStream) throws IOException {
            boxHash = Config.newBoxHash();
            new DataInputStream(inputStream).readFully(boxHash.getBytes());
            int ivLength = (int)VarInt.read(inputStream);
            iv = new byte[ivLength];
            new DataInputStream(inputStream).readFully(iv);

            BoxHeaderIO.read(boxHeader, inputStream);
        }
    }

    final private Data data;
    final private Box box;

    public ChunkContainerRef() {
        this.data = new Data();
        this.box = new Box();
    }

    public ChunkContainerRef(Data data, Box box) {
        this.data = data;
        this.box = box;
    }

    public Data getData() {
        return data;
    }

    public Box getBox() {
        return box;
    }

    public BoxPointer getBoxPointer() {
        return new BoxPointer(data.dataHash, box.boxHash, box.iv);
    }

    public HashValue getBoxHash() {
        return box.getBoxHash();
    }

    public BoxHeader getBoxHeader() {
        return box.getBoxHeader();
    }

    public byte[] getIV() {
        return box.iv;
    }

    public HashValue getDataHash() {
        return data.getDataHash();
    }

    public void setBoxHash(HashValue hash) {
        box.setBoxHash(hash);
    }

    public void setIV(byte[] iv) {
        box.iv = iv;
    }

    public void setDataHash(HashValue hash) {
        data.setDataHash(hash);
    }

    public ChunkContainerHeader getContainerHeader() {
        return data.getContainerHeader();
    }

    public IMessageDigestFactory getDataMessageDigestFactory() throws IOException {
        ChunkContainerHeader.HashType hashType = data.getContainerHeader().getHashType();
        switch (hashType) {
            case SHA_3:
                return new IMessageDigestFactory() {
                    @Override
                    public MessageDigest create() throws NoSuchAlgorithmException {
                        return CryptoHelper.sha3_256Hash();
                    }
                };
        }
        throw new IOException("Unsupported hash type");
    }

    public MessageDigest getDataMessageDigest() throws IOException {
        try {
            return getDataMessageDigestFactory().create();
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ChunkContainerRef))
            return false;
        ChunkContainerRef other = (ChunkContainerRef)o;
        if (!getDataHash().equals(other.getDataHash()))
            return false;
        if (!Arrays.equals(getIV(), other.getIV()))
            return false;
        return getBoxHash().equals(other.getBoxHash());
    }

    @Override
    protected ChunkContainerRef clone() {
        return new ChunkContainerRef(data.clone(), box.clone());
    }
}

/*
public class ChunkContainerRef {
    static class RabinConfig {
        private RabinConfig defaultConfig;

        private int minChunkSize;
        private int targetChunkSize;

        enum Tag {
            MIN_CHUNK_SIZE(1),
            TARGET_CHUNK_SIZE(2);

            final private int value;

            Tag(int value) {
                this.value = value;
            }
        }

        public RabinConfig(RabinConfig defaultConfig) {
            setDefault(defaultConfig);
        }

        private RabinConfig() {

        }

        static public RabinConfig getRabinConfig_2_8() {
            RabinConfig config = new RabinConfig();
            config.minChunkSize = 2 * 1024;
            config.targetChunkSize = 8 * 1024;
            return config;
        }

        private void setDefault(RabinConfig config) {
            this.defaultConfig = config;
            this.minChunkSize = config.minChunkSize;
            this.targetChunkSize = config.targetChunkSize;
        }

        public boolean isDefault() {
            if (minChunkSize != defaultConfig.minChunkSize)
                return false;
            if (targetChunkSize != defaultConfig.targetChunkSize)
                return false;

            return true;
        }

        public void writeAsBuffer(OutputStream outputStream) throws IOException {
            ProtocolBufferLight buffer = new ProtocolBufferLight();
            if (minChunkSize != defaultConfig.minChunkSize)
                buffer.put(MIN_CHUNK_SIZE.value, minChunkSize);
            if (targetChunkSize != defaultConfig.targetChunkSize)
                buffer.put(TARGET_CHUNK_SIZE.value, targetChunkSize);
            buffer.write(outputStream);
        }

        public void readFromBuffer(InputStream inputStream) throws IOException {
            ProtocolBufferLight buffer = new ProtocolBufferLight();
            buffer.read(inputStream);
            Long value = buffer.getLong(MIN_CHUNK_SIZE.value);
            if (value != null)
                minChunkSize = value.intValue();
            value = buffer.getLong(TARGET_CHUNK_SIZE.value);
            if (value != null)
                targetChunkSize = value.intValue();
        }
    }

    static class Plain {
        // used hash function
        static final public int SHA_3 = 0;

        // chunking strategy
        static final public int NO_CHUNKING = 0;
        static final public int FIXED_BLOCK_SPLITTER_DETAILED = 1;
        static final public int RABIN_SPLITTER_DETAILED = 2;
        static final public int RABIN_SPLITTER_2K_8K = 3;

        static boolean isRabinStrategy(int strategy) {
            if (strategy >= 2)
                return true;
            return false;
        }

        // [] means a dynamic byte, i.e. the first bit in the byte signals if another byte follows
        // {} some data of size s; the size is stored in a dynamic byte followed by the data: {data} = [s]data
        // [chunking type]{chunking details (optional)}[hash type]{extension}

        private int hashType = SHA_3;

        private int chunkingStrategy = RABIN_SPLITTER_2K_8K;
        private RabinConfig rabinConfig = null;

        public void setRabinStrategy(int strategy, RabinConfig config) {
            this.chunkingStrategy = strategy;
            this.rabinConfig = config;
        }

        public void write(OutputStream outputStream) throws IOException {
            // if first bit is set there are chunking details
            long chunkingOut = chunkingStrategy << 1;
            if (rabinConfig != null && !rabinConfig.isDefault()) {
                chunkingOut |= 0x1;
                VarInt.write(outputStream, chunkingOut);
                rabinConfig.writeAsBuffer(outputStream);
            } else
                VarInt.write(outputStream, chunkingOut);

            // if first bit set there is an extension of a proto buffer
            long hashOut = hashType << 1;
            VarInt.write(outputStream, hashOut);
        }

        public void read(InputStream inputStream) throws IOException {
            long chunkingValue = VarInt.read(inputStream);
            chunkingStrategy = (int)(chunkingValue >> 1);
            if ((chunkingValue & 0x1) != 0) {
                if (isRabinStrategy(chunkingStrategy)) {
                    rabinConfig = new RabinConfig();
                    rabinConfig.readFromBuffer(inputStream);
                } else {
                    // just read the proto buffer
                    new ProtocolBufferLight(inputStream);
                }
            }

            long hashValue = VarInt.read(inputStream);
            hashType = (int)(hashValue >> 1);
            if ((hashValue & 0x1) != 0) {
                // read the extension
                new ProtocolBufferLight(inputStream);
            }

        }
    }

    static class Box {
        // compression
        static final public int NO_COMPRESSION = 0;
        static final public int ZLIB_COMPRESSION = 1;

        // container type
        static final public int RAW_DATA = 0;
        static final public int CHUNK_CONTAINER = 1;
        static final public int SMALL_CHUNK_CONTAINER = 2;
        static final public int DELTA_CHUNK_CONTAINER = 3;

        // [container type]{container type data (optional)}[compression]{enc details(optional)}{extension}

        private int containerType = RAW_DATA;
        private boolean hasContainerTypeExtension = false;
        private int compression = NO_COMPRESSION;
        private boolean hasEncDetails = false;
        private boolean hasExtension = false;

        public void setZlibCompression() {
            this.compression = ZLIB_COMPRESSION;
        }

        public void write(OutputStream outputStream) throws IOException {
            long containerTypeOut = containerType << 1;
            if (hasContainerTypeExtension) {
                containerTypeOut &= 1;
            }
            VarInt.write(outputStream, containerTypeOut);

            int outFinal = compression << 2;
            if (hasEncDetails) {
                outFinal &= (1 << 1);
            }
            if (hasExtension) {
                outFinal &= 1;
            }
            VarInt.write(outputStream, outFinal);
        }

        public void read(InputStream inputStream) throws IOException {
            long containerTypeIn = VarInt.read(inputStream);
            if ((containerTypeIn & 1) != 0) {
                hasContainerTypeExtension = true;
                new ProtocolBufferLight(inputStream);
            }
            containerType = (int)(containerTypeIn >> 1);

            long inFinal = VarInt.read(inputStream);
            if ((inFinal & (1 << 1)) != 0) {
                hasEncDetails = true;
                new ProtocolBufferLight(inputStream);
            }
            if ((inFinal & 1) != 0) {
                hasExtension = true;
                new ProtocolBufferLight(inputStream);
            }
            compression = (int)(inFinal >> 2);
        }
    }
}
*/
