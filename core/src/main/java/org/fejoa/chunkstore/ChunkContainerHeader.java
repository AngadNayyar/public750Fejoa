/*
 * Copyright 2017.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.chunkstore;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import static org.fejoa.chunkstore.ChunkContainerHeader.ChunkingType.*;
import static org.fejoa.chunkstore.ChunkContainerHeader.HashType.SHA_3;
import static org.fejoa.chunkstore.ChunkContainerHeaderIO.FixedSizeDetailTag.SIZE;
import static org.fejoa.chunkstore.ChunkContainerHeaderIO.RabinDetailTag.MAX_CHUNK_SIZE;
import static org.fejoa.chunkstore.ChunkContainerHeaderIO.RabinDetailTag.MIN_CHUNK_SIZE;
import static org.fejoa.chunkstore.ChunkContainerHeaderIO.RabinDetailTag.TARGET_CHUNK_SIZE;


class ChunkContainerHeaderIO {
    // Format:
    // [nLevels][container type][container length][chunking type]{chunking details (optional)}[hash type]{extension}
    // [] means a dynamic byte, i.e. the first bit in the byte signals if another byte follows
    // {} some data of size s; the size is stored in a dynamic byte followed by the data: {data} = [s]data
    static public void write(ChunkContainerHeader header, OutputStream outputStream) throws IOException {
        // level
        VarInt.write(outputStream, header.getLevel());
        // container type
        VarInt.write(outputStream, header.getContainerType().value);
        // container length
        VarInt.write(outputStream, header.getDataLength());
        // if first bit is set there are chunking details
        ChunkContainerHeader.IChunkingConfig chunkingConfig = header.getChunkingConfig();
        assert chunkingConfig != null;
        long chunkingOut = chunkingConfig.getChunkingType().value << 1;
        if (!chunkingConfig.isDefault()) {
            chunkingOut |= 0x1;
            VarInt.write(outputStream, chunkingOut);
            writeDetails(chunkingConfig, outputStream);
        } else
            VarInt.write(outputStream, chunkingOut);

        // if first bit set there is an extension of a proto buffer
        long hashOut = header.getHashType().value << 1;
        VarInt.write(outputStream, hashOut);
    }

    static public void read(ChunkContainerHeader header, InputStream inputStream) throws IOException {
        // level
        header.setLevel((int)VarInt.read(inputStream));
        // container type
        long containerTypeValue = VarInt.read(inputStream);
        ChunkContainerHeader.ContainerType containerType = ChunkContainerHeader.ContainerType.fromInt(
                (int)containerTypeValue);
        if (containerType == null)
            throw new IOException("Unknown container type: " + containerTypeValue);
        header.setContainerType(containerType);
        // container length
        header.setDataLength(VarInt.read(inputStream));

        long chunkingValueRaw = VarInt.read(inputStream);
        int chunkingValue = (int)(chunkingValueRaw >> 1);
        ChunkContainerHeader.ChunkingType chunkingType = ChunkContainerHeader.ChunkingType.fromInt(chunkingValue);
        if (chunkingType == null)
            throw new IOException("Unknown chunking type: " + chunkingValue);

        if ((chunkingValueRaw & 0x1) != 0) {
            if (ChunkContainerHeader.ChunkingType.isRabin(chunkingType)) {
                ChunkContainerHeader.RabinChunkingConfig config
                        = ChunkContainerHeader.RabinChunkingConfig.getDefault(chunkingType);
                readDetails(config, inputStream);
            } else if (ChunkContainerHeader.ChunkingType.isFixedSized(chunkingType)) {
                ChunkContainerHeader.FixedSizeChunkingConfig config
                        = ChunkContainerHeader.FixedSizeChunkingConfig.getDefault(chunkingType);
                readDetails(config, inputStream);
            } else {
                // just read the proto buffer
                new ProtocolBufferLight(inputStream);
            }
        }

        long hashValueRaw = VarInt.read(inputStream);
        ChunkContainerHeader.HashType hashType = ChunkContainerHeader.HashType.fromInt((int)(hashValueRaw >> 1));
        if (hashType == null)
            throw new IOException("Unknown hash type: " + hashType.value);
        header.setHashType(hashType);
        if ((hashValueRaw & 0x1) != 0) {
            // read the extension
            new ProtocolBufferLight(inputStream);
        }
    }

    enum RabinDetailTag {
        TARGET_CHUNK_SIZE(0),
        MIN_CHUNK_SIZE(1),
        MAX_CHUNK_SIZE(2);

        final private int value;
        RabinDetailTag(int value) {
            this.value = value;
        }
    }

    enum FixedSizeDetailTag {
        SIZE(0);

        final private int value;
        FixedSizeDetailTag(int value) {
            this.value = value;
        }
    }

    static private void writeDetails(ChunkContainerHeader.IChunkingConfig chunkingConfig, OutputStream outputStream)
            throws IOException {
        if (chunkingConfig instanceof ChunkContainerHeader.RabinChunkingConfig) {
            ChunkContainerHeader.RabinChunkingConfig config = (ChunkContainerHeader.RabinChunkingConfig)chunkingConfig;
            ProtocolBufferLight buffer = new ProtocolBufferLight();
            if (config.targetSize != config.defaultConfig.targetSize)
                buffer.put(TARGET_CHUNK_SIZE.value, config.targetSize);
            if (config.minSize != config.defaultConfig.minSize)
                buffer.put(MIN_CHUNK_SIZE.value, config.minSize);
            if (config.maxSize != config.defaultConfig.maxSize)
                buffer.put(MAX_CHUNK_SIZE.value, config.maxSize);

            buffer.write(outputStream);
        } else if (chunkingConfig instanceof ChunkContainerHeader.FixedSizeChunkingConfig) {
            ChunkContainerHeader.FixedSizeChunkingConfig config
                    = (ChunkContainerHeader.FixedSizeChunkingConfig)chunkingConfig;
            ProtocolBufferLight buffer = new ProtocolBufferLight();
            if (config.size != config.defaultConfig.size)
                buffer.put(SIZE.value, config.size);
            buffer.write(outputStream);

        } else
            throw new IOException("Unknown chunking config");
    }

    static private void readDetails(ChunkContainerHeader.RabinChunkingConfig config, InputStream inputStream)
            throws IOException {
        ProtocolBufferLight buffer = new ProtocolBufferLight();
        buffer.read(inputStream);
        Long value = buffer.getLong(TARGET_CHUNK_SIZE.value);
        if (value != null)
            config.targetSize = value.intValue();
        value = buffer.getLong(MIN_CHUNK_SIZE.value);
        if (value != null)
            config.minSize = value.intValue();
        value = buffer.getLong(MAX_CHUNK_SIZE.value);
        if (value != null)
            config.maxSize = value.intValue();
    }

    static private void readDetails(ChunkContainerHeader.FixedSizeChunkingConfig config, InputStream inputStream)
            throws IOException {
        ProtocolBufferLight buffer = new ProtocolBufferLight();
        buffer.read(inputStream);
        Long value = buffer.getLong(SIZE.value);
        if (value != null)
            config.size = value.intValue();
    }
}


public class ChunkContainerHeader {
    // used hash function
    enum HashType {
        SHA_3(0);

        final public int value;
        HashType(int value) {
            this.value = value;
        }

        public static HashType fromInt(int value) {
            for (HashType type : HashType.values()) {
                if (type.value == value)
                    return type;
            }
            return null;
        }
    }

    // chunking strategy
    enum ChunkingType {
        FIXED_BLOCK_SPLITTER_DETAILED(0),
        FIXED_BLOCK_SPLITTER_8K(1),
        RABIN_SPLITTER_DETAILED(2),
        RABIN_SPLITTER_2K_8K(3);

        final public int value;
        ChunkingType(int value) {
            this.value = value;
        }

        public static boolean isRabin(ChunkingType type) {
            switch (type) {
                case RABIN_SPLITTER_DETAILED:
                case RABIN_SPLITTER_2K_8K:
                    return true;
            }
            return false;
        }

        public static boolean isFixedSized(ChunkingType type) {
            switch (type) {
                case FIXED_BLOCK_SPLITTER_DETAILED:
                case RABIN_SPLITTER_2K_8K:
                    return true;
            }
            return false;
        }

        public static ChunkingType fromInt(int value) {
            for (ChunkingType type : ChunkingType.values()) {
                if (type.value == value)
                    return type;
            }
            return null;
        }
    }

    enum ContainerType {
        CHUNK_CONTAINER(0),
        RAW(1);

        final public int value;
        ContainerType(int value) {
            this.value = value;
        }

        public static ContainerType fromInt(int value) {
            for (ContainerType type : ContainerType.values()) {
                if (type.value == value)
                    return type;
            }
            return null;
        }
    }

    interface IChunkingConfig {
        IChunkingConfig clone();
        ChunkingType getChunkingType();
        boolean isDefault();
    }

    private int level;
    private long dataSize;
    private ContainerType containerType = ContainerType.CHUNK_CONTAINER;
    private HashType hashFunc = SHA_3;
    private IChunkingConfig chunkingConfig;

    ChunkContainerHeader() {
        setRabinChunking(RABIN_SPLITTER_2K_8K);
    }

    @Override
    protected ChunkContainerHeader clone() {
        ChunkContainerHeader header = new ChunkContainerHeader();
        header.level = level;
        header.dataSize = dataSize;
        header.containerType = containerType;
        header.hashFunc = hashFunc;
        header.chunkingConfig = chunkingConfig.clone();
        return header;
    }

    public void setRabinChunking(ChunkingType type) {
        chunkingConfig = RabinChunkingConfig.create(type);
    }

    public void setRabinChunking(ChunkingType type, int targetSize, int minSize, int maxSize) {
        RabinChunkingConfig config = RabinChunkingConfig.create(type);
        chunkingConfig = config;
        config.targetSize = targetSize;
        config.minSize = minSize;
        config.maxSize = maxSize;
    }

    public void setRabinChunking(int targetSize, int minSize) {
        RabinChunkingConfig config = RabinChunkingConfig.create(RABIN_SPLITTER_DETAILED);
        chunkingConfig = config;
        config.targetSize = targetSize;
        config.minSize = minSize;
    }

    public void setFixedSizeChunking(int size) {
        FixedSizeChunkingConfig config = FixedSizeChunkingConfig.create(FIXED_BLOCK_SPLITTER_DETAILED);
        chunkingConfig = config;
        config.size = size;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public long getDataLength() {
        return dataSize;
    }

    public void setDataLength(long dataSize) {
        this.dataSize = dataSize;
    }

    public HashType getHashType() {
        return hashFunc;
    }

    public void setHashType(HashType hashFunc) {
        this.hashFunc = hashFunc;
    }

    public IChunkingConfig getChunkingConfig() {
        return chunkingConfig;
    }

    public ContainerType getContainerType() {
        return containerType;
    }

    public void setContainerType(ContainerType containerType) {
        this.containerType = containerType;
    }

    /**
     * Creates a splitter from the config.
     *
     * @param factor of how much the chunks should be smaller than in the config (see ChunkContainerNode).
     * @return
     */
    public ChunkSplitter getSplitter(float factor) {
        if (chunkingConfig == null)
            return null;
        if (chunkingConfig instanceof RabinChunkingConfig) {
            RabinChunkingConfig config = (RabinChunkingConfig)chunkingConfig;
            RabinSplitter rabinSplitter = new RabinSplitter((int)(factor * config.targetSize),
                    (int)(factor * config.minSize), (int)(factor * config.maxSize));
            return rabinSplitter;
        } else if (chunkingConfig instanceof FixedSizeChunkingConfig) {
            FixedSizeChunkingConfig config = (FixedSizeChunkingConfig)chunkingConfig;
            FixedBlockSplitter splitter = new FixedBlockSplitter((int)(factor * config.size));
            return splitter;
        }
        return null;
    }

    static public class FixedSizeChunkingConfig implements IChunkingConfig {
        private ChunkingType type = FIXED_BLOCK_SPLITTER_DETAILED;
        int size;
        FixedSizeChunkingConfig defaultConfig;


        static public FixedSizeChunkingConfig create(ChunkingType type) {
            FixedSizeChunkingConfig config = getDefault(type);
            config.defaultConfig = getDefault(type);
            return config;
        }

        private FixedSizeChunkingConfig(ChunkingType type, int size) {
            this.type = type;
            this.size = size;
        }

        private FixedSizeChunkingConfig(ChunkingType type, FixedSizeChunkingConfig defaultConfig, int size) {
            this.type = type;
            this.defaultConfig = defaultConfig;
            this.size = size;
        }

        @Override
        public IChunkingConfig clone() {
            return new FixedSizeChunkingConfig(type, defaultConfig, size);
        }

        static public FixedSizeChunkingConfig getDefault(ChunkingType type) {
            switch (type) {
                case FIXED_BLOCK_SPLITTER_DETAILED:
                    return getDefault(FIXED_BLOCK_SPLITTER_8K);
                case FIXED_BLOCK_SPLITTER_8K:
                    return new FixedSizeChunkingConfig(RABIN_SPLITTER_2K_8K, 8 * 1024);
            }
            assert false;
            return null;
        }

        @Override
        public ChunkingType getChunkingType() {
            return type;
        }

        @Override
        public boolean isDefault() {
            return false;
        }
    }

    static public class RabinChunkingConfig implements IChunkingConfig {
        RabinChunkingConfig defaultConfig;
        ChunkingType type;
        int targetSize;
        int minSize;
        int maxSize;

        static public RabinChunkingConfig create(ChunkingType type) {
            RabinChunkingConfig config = getDefault(type);
            config.defaultConfig = getDefault(type);
            return config;
        }

        private RabinChunkingConfig(ChunkingType type, int targetSize, int minSize, int maxSize) {
            this.type = type;
            this.targetSize = targetSize;
            this.minSize = minSize;
            this.maxSize = maxSize;
        }

        private RabinChunkingConfig(ChunkingType type, RabinChunkingConfig defaultConfig, int targetSize,
                                    int minSize, int maxSize) {
            this.type = type;
            this.defaultConfig = defaultConfig;
            this.targetSize = targetSize;
            this.minSize = minSize;
            this.maxSize = maxSize;
        }

        @Override
        public IChunkingConfig clone() {
            return new RabinChunkingConfig(type, defaultConfig, targetSize, minSize, maxSize);
        }

        static public RabinChunkingConfig getDefault(ChunkingType type) {
            switch (type) {
                case RABIN_SPLITTER_DETAILED:
                    return getDefault(RABIN_SPLITTER_2K_8K);
                case RABIN_SPLITTER_2K_8K:
                    return new RabinChunkingConfig(RABIN_SPLITTER_2K_8K, 8 * 1024, 2 * 1024,
                            Integer.MAX_VALUE / 2);
            }
            assert false;
            return null;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof RabinChunkingConfig))
                return false;
            if (((RabinChunkingConfig) o).type != type)
                return false;
            if (((RabinChunkingConfig) o).minSize != minSize)
                return false;
            if (((RabinChunkingConfig) o).targetSize != targetSize)
                return false;
            if (((RabinChunkingConfig) o).maxSize != maxSize)
                return false;
            return true;
        }

        @Override
        public ChunkingType getChunkingType() {
            return type;
        }

        @Override
        public boolean isDefault() {
            return this.equals(defaultConfig);
        }
    }

}
