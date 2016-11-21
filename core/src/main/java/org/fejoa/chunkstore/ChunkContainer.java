/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.chunkstore;

import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.support.DoubleLinkedList;

import java.io.*;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;


class ChunkPointer implements IChunkPointer {
    // length of the "real" data, this is needed to find data for random access
    // Goal: don't rewrite previous blocks, support middle extents and make random access possible.
    // Using the data length makes this possible.
    private int dataLength;
    private BoxPointer boxPointer;

    private IChunk cachedChunk = null;
    protected int level;

    protected ChunkPointer(int level) {
        this.boxPointer = new BoxPointer();
        this.level = level;
    }

    protected ChunkPointer(BoxPointer hash, int dataLength, IChunk blob, int level) {
        if (hash != null)
            this.boxPointer = hash;
        else
            this.boxPointer = new BoxPointer();
        this.dataLength = dataLength;
        cachedChunk = blob;
        this.level = level;
    }

    @Override
    public int getPointerLength() {
        return BoxPointer.getPointerLength() + 4;
    }

    @Override
    public int getDataLength() {
        if (cachedChunk != null)
            dataLength = cachedChunk.getDataLength();
        return dataLength;
    }

    public void setBoxPointer(BoxPointer boxPointer) {
        this.boxPointer = boxPointer;
    }

    public BoxPointer getBoxPointer() {
        return boxPointer;
    }

    @Override
    public IChunk getCachedChunk() {
        return cachedChunk;
    }

    @Override
    public void setCachedChunk(IChunk chunk) {
        this.cachedChunk = chunk;
    }

    @Override
    public int getLevel() {
        return level;
    }

    @Override
    public void setLevel(int level) {
        this.level = level;
    }

    public void read(DataInputStream inputStream) throws IOException {
        int value = inputStream.readInt();
        dataLength = value >> 1;
        boxPointer.read(inputStream);
    }

    public void write(DataOutputStream outputStream) throws IOException {
        int value = getDataLength() << 1;
        outputStream.writeInt(value);
        boxPointer.write(outputStream);
    }

    @Override
    public String toString() {
        String string = "l:" + dataLength;
        if (boxPointer != null)
            string+= "," + boxPointer.toString();
        return string;
    }
}


class CacheManager {
    static private class PointerEntry extends DoubleLinkedList.Entry {
        final public IChunkPointer dataChunkPointer;
        final public ChunkContainerNode parent;

        public PointerEntry(IChunkPointer dataChunkPointer, ChunkContainerNode parent) {
            this.dataChunkPointer = dataChunkPointer;
            this.parent = parent;
        }
    }
    final DoubleLinkedList<PointerEntry> queue = new DoubleLinkedList<>();
    final Map<IChunkPointer, PointerEntry> pointerMap = new HashMap<>();

    final private int targetCapacity = 10;
    final private int triggerCapacity = 15;
    final private int keptMetadataLevels = 2;

    final private ChunkContainer chunkContainer;

    public CacheManager(ChunkContainer chunkContainer) {
        this.chunkContainer = chunkContainer;
    }

    private void bringToFront(PointerEntry entry) {
        queue.remove(entry);
        queue.addFirst(entry);
    }

    public void update(IChunkPointer dataChunkPointer, ChunkContainerNode parent) {
        assert ChunkContainer.isDataPointer(dataChunkPointer);
        PointerEntry entry = pointerMap.get(dataChunkPointer);
        if (entry != null) {
            bringToFront(entry);
            return;
        }
        entry = new PointerEntry(dataChunkPointer, parent);
        queue.addFirst(entry);
        pointerMap.put(dataChunkPointer, entry);
        if (pointerMap.size() >= triggerCapacity)
            clean(triggerCapacity - targetCapacity);
    }

    public void remove(IChunkPointer dataChunkPointer) {
        DoubleLinkedList.Entry entry = pointerMap.get(dataChunkPointer);
        if (entry == null)
            return;
        queue.remove(entry);
        pointerMap.remove(dataChunkPointer);
        // don't clean parents yet, they are most likely being edited right now
    }

    private void clean(int numberOfEntries) {
        for (int i = 0; i < numberOfEntries; i++) {
            PointerEntry entry = queue.removeTail();
            pointerMap.remove(entry.dataChunkPointer);

            clean(entry);
        }
    }

    private void clean(PointerEntry entry) {
        // always clean the data cache
        entry.dataChunkPointer.setCachedChunk(null);

        IChunkPointer currentPointer = entry.dataChunkPointer;
        ChunkContainerNode currentParent = entry.parent;
        while (chunkContainer.getNLevels() - currentParent.getLevel() >= keptMetadataLevels) {
            currentPointer.setCachedChunk(null);
            if (hasCachedPointers(currentParent))
                break;

            currentPointer = currentParent.getChunkPointer();
            currentParent = currentParent.getParent();
        }
    }

    private boolean hasCachedPointers(ChunkContainerNode node) {
        for (IChunkPointer pointer : node.getChunkPointers()) {
            if (pointer.getCachedChunk() != null)
                return true;
        }
        return false;
    }
}

public class ChunkContainer extends ChunkContainerNode {

    static public ChunkContainer read(IChunkAccessor blobAccessor, BoxPointer boxPointer)
            throws IOException, CryptoException {
        return new ChunkContainer(blobAccessor, boxPointer);
    }

    static class ChunkAccessor implements IChunkAccessor {
        final private IChunkAccessor child;
        private boolean compression = true;

        public ChunkAccessor(IChunkAccessor child) {
            this.child = child;
        }

        @Override
        public DataInputStream getChunk(BoxPointer hash) throws IOException, CryptoException {
            DataInputStream inputStream = child.getChunk(hash);
            if (!compression)
                return inputStream;
            return getInputStream(inputStream);
        }

        @Override
        public PutResult<HashValue> putChunk(byte[] data, HashValue ivHash) throws IOException, CryptoException {
            if (compression) {
                ByteArrayOutputStream dataOutputStream = new ByteArrayOutputStream();
                DeflaterOutputStream outputStream = new DeflaterOutputStream(dataOutputStream);
                outputStream.write(data);
                outputStream.close();
                data = dataOutputStream.toByteArray();
            }
            return child.putChunk(data, ivHash);
        }

        @Override
        public void releaseChunk(HashValue data) {
            child.releaseChunk(data);
        }

        public DataOutputStream getOutputStream(DataOutputStream outputStream) {
            if (!compression)
                return outputStream;
            return new DataOutputStream(new DeflaterOutputStream(outputStream));
        }

        public DataInputStream getInputStream(DataInputStream inputStream) {
            if (!compression)
                return inputStream;
            return new DataInputStream(new InflaterInputStream(inputStream));
        }
    }

    final private ChunkAccessor chunkContainerAccessor;
    final private Config config = new Config();
    final private CacheManager cacheManager;

    /**
     * Create a new chunk container.
     *
     * @param blobAccessor
     */
    public ChunkContainer(IChunkAccessor blobAccessor, ChunkSplitter nodeSplitter) {
        super(new ChunkAccessor(blobAccessor), null, nodeSplitter, LEAF_LEVEL);
        chunkContainerAccessor = (ChunkAccessor)this.blobAccessor;

        // reset the node splitter to get the config
        setNodeSplitter(nodeSplitter);

        cacheManager = new CacheManager(this);
    }

    /**
     * Load an existing chunk container.
     *
     * @param blobAccessor
     * @param boxPointer
     * @throws IOException
     * @throws CryptoException
     */
    private ChunkContainer(IChunkAccessor blobAccessor, BoxPointer boxPointer)
            throws IOException, CryptoException {
        this(blobAccessor, blobAccessor.getChunk(boxPointer));
        that.setBoxPointer(boxPointer);
    }

    private ChunkContainer(IChunkAccessor blobAccessor, DataInputStream inputStream)
            throws IOException {
        super(new ChunkAccessor(blobAccessor), null, null, LEAF_LEVEL);
        chunkContainerAccessor = (ChunkAccessor)this.blobAccessor;
        read(inputStream);

        cacheManager = new CacheManager(this);
    }

    @Override
    public void setNodeSplitter(ChunkSplitter nodeSplitter) {
        super.setNodeSplitter(nodeSplitter);

        if (nodeSplitter == null || config == null)
            return;
        if (nodeSplitter instanceof RabinSplitter) {
            config.setSplitterType(RABIN_SPLITTER_DETAILED);
        } else if (nodeSplitter instanceof  FixedBlockSplitter) {
            config.setSplitterType(FIXED_BLOCK_SPLITTER_DETAILED);
        }
    }

    public void setZLibCompression(boolean compression) {
        if (compression)
            config.setCompressionType(ZLIB_COMPRESSION);
        else
            config.setCompressionType(NO_COMPRESSION);
    }

    @Override
    public int getBlobLength() {
        // number of slots;
        int length = getHeaderLength();
        length += super.getBlobLength();
        return length;
    }

    public int getNLevels() {
        return that.getLevel();
    }

    public class DataChunkPointer {
        final private IChunkPointer pointer;
        private DataChunk cachedChunk;
        final public long position;
        final public int chunkDataLength;

        private DataChunkPointer(IChunkPointer pointer, long position) throws IOException {
            this.pointer = pointer;
            this.position = position;
            this.chunkDataLength = pointer.getDataLength();
        }

        public DataChunk getDataChunk() throws IOException, CryptoException {
            if (cachedChunk == null)
                cachedChunk = ChunkContainer.this.getDataChunk(pointer);
            return cachedChunk;
        }

        public int getDataLength() {
            return chunkDataLength;
        }
    }

    public Iterator<DataChunkPointer> getChunkIterator(final long startPosition) {
        return new Iterator<DataChunkPointer>() {
            private long position = startPosition;

            @Override
            public boolean hasNext() {
                if (position >= getDataLength())
                    return false;
                return true;
            }

            @Override
            public DataChunkPointer next() {
                try {
                    DataChunkPointer dataChunkPointer = get(position);
                    position = dataChunkPointer.position + dataChunkPointer.getDataLength();
                    return dataChunkPointer;
                } catch (Exception e) {
                    e.printStackTrace();
                    return null;
                }
            }

            @Override
            public void remove() {

            }
        };
    }

    public DataChunkPointer get(long position) throws IOException, CryptoException {
        SearchResult searchResult = findLevel0Node(position);
        if (searchResult.pointer == null)
            throw new IOException("Invalid position");
        cacheManager.update(searchResult.pointer, searchResult.node);
        return new DataChunkPointer(searchResult.pointer, searchResult.pointerDataPosition);
    }

    private SearchResult findLevel0Node(long position) throws IOException, CryptoException {
        long currentPosition = 0;
        IChunkPointer pointer = null;
        ChunkContainerNode containerNode = this;
        for (int i = 0; i < that.getLevel(); i++) {
            SearchResult result = findInNode(containerNode, position - currentPosition);
            if (result == null) {
                // find right most node blob
                return new SearchResult(getDataLength(), null, findRightMostNode());
            }
            currentPosition += result.pointerDataPosition;
            pointer = result.pointer;
            if (i == that.getLevel() - 1)
                break;
            else
                containerNode = containerNode.getNode(result.pointer);

        }

        return new SearchResult(currentPosition, pointer, containerNode);
    }

    private ChunkContainerNode findRightMostNode() throws IOException, CryptoException {
        ChunkContainerNode current = this;
        for (int i = 0; i < that.getLevel() - 1; i++) {
            IChunkPointer pointer = current.get(current.size() - 1);
            current = current.getNode(pointer);
        }
        return current;
    }

    private IChunkPointer putDataChunk(DataChunk blob) throws IOException, CryptoException {
        byte[] rawBlob = blob.getData();
        HashValue hash = blob.hash();
        HashValue boxedHash = blobAccessor.putChunk(rawBlob, hash).key;
        BoxPointer boxPointer = new BoxPointer(hash, boxedHash, hash);
        return new ChunkPointer(boxPointer, rawBlob.length, blob, DATA_LEVEL);
    }

    static class InsertSearchResult {
        final ChunkContainerNode containerNode;
        final int index;

        InsertSearchResult(ChunkContainerNode containerNode, int index) {
            this.containerNode = containerNode;
            this.index = index;
        }
    }

    private InsertSearchResult findInsertPosition(final long position) throws IOException, CryptoException {
        long currentPosition = 0;
        ChunkContainerNode node = this;
        int index = 0;
        for (int i = 0; i < that.getLevel(); i++) {
            long nodePosition = 0;
            long inNodeInsertPosition = position - currentPosition;
            index = 0;
            IChunkPointer pointer = null;
            for (; index < node.size(); index++) {
                pointer = node.get(index);
                long dataLength = pointer.getDataLength();
                if (nodePosition + dataLength > inNodeInsertPosition)
                    break;
                nodePosition += dataLength;
            }
            currentPosition += nodePosition;
            if (nodePosition > inNodeInsertPosition
                    || (i == that.getLevel() - 1 && nodePosition != inNodeInsertPosition)) {
                throw new IOException("Invalid insert position");
            }

            if (i < that.getLevel() - 1 && pointer != null)
                node = node.getNode(pointer);
        }

        return new InsertSearchResult(node, index);
    }

    public void insert(final DataChunk blob, final long position) throws IOException, CryptoException {
        InsertSearchResult searchResult = findInsertPosition(position);
        ChunkContainerNode containerNode = searchResult.containerNode;
        IChunkPointer blobChunkPointer = putDataChunk(blob);
        containerNode.addBlobPointer(searchResult.index, blobChunkPointer);

        cacheManager.update(blobChunkPointer, containerNode);
    }

    public void append(final DataChunk blob) throws IOException, CryptoException {
        insert(blob, getDataLength());
    }

    public void remove(long position, DataChunk dataChunk) throws IOException, CryptoException {
        remove(position, dataChunk.getDataLength());
    }

    public void remove(long position, long length) throws IOException, CryptoException {
        SearchResult searchResult = findLevel0Node(position);
        if (searchResult.pointer == null)
            throw new IOException("Invalid position");
        if (searchResult.pointer.getDataLength() != length)
            throw new IOException("Data length mismatch");

        ChunkContainerNode containerNode = searchResult.node;
        int indexInParent = containerNode.indexOf(searchResult.pointer);
        containerNode.removeBlobPointer(indexInParent, true);

        cacheManager.remove(searchResult.pointer);
    }

    @Override
    protected int getHeaderLength() {
        // 1 byte for number of levels
        int length = 1;
        return length;
    }

    @Override
    public void read(DataInputStream inputStream) throws IOException {
        readHeader(inputStream);
        super.read(chunkContainerAccessor.getInputStream(inputStream));
    }

    @Override
    public void write(DataOutputStream outputStream) throws IOException {
        writeHeader(outputStream);
        outputStream = chunkContainerAccessor.getOutputStream(outputStream);
        super.write(outputStream);
        outputStream.close();
    }

    @Override
    protected HashValue writeNode() throws IOException, CryptoException {
        // We are the root node and compression is handled in read() and write()
        byte[] data = getData();
        return chunkContainerAccessor.child.putChunk(data, rawHash()).key;
    }

    public String printAll() throws Exception {
        String string = "Header: levels=" + that.getLevel() + ", length=" + getDataLength() + "\n";
        string += super.printAll();
        return string;
    }

    static final public byte FIXED_BLOCK_SPLITTER_DETAILED = 0;
    static final public byte RABIN_SPLITTER_DETAILED = 1;

    static final public byte NO_COMPRESSION = 0;
    static final public byte ZLIB_COMPRESSION = 1;

    static class Config {
        byte config = 0;
        // 0 1 2 3 4     5 6        7
        // {-------}     {-}       {-}
        //  splitter compression  reserved
        // This results in 32 splitter types, 3 compression types and on reserved bits (e.g. to extent the config by
        // another byte).
        static final private byte SPLITTER_MASK = (byte)0x1f;
        static final private int SPLITTER_SHIFT = 0;
        static final private byte COMPRESSION_MASK = (byte)0x60;
        static final private int COMPRESSION_SHIFT = 5;
        static final private byte RESERVED_MASK = (byte)0x80;
        static final private int RESERVED_SHIFT = 8;

        public void setSplitterType(int splitterType) {
            if (splitterType < 0 || splitterType > 32)
                throw new RuntimeException("invalid splitter type");
            config &= ~SPLITTER_MASK;
            config |= splitterType << SPLITTER_SHIFT;
        }

        public int getSplitterType() {
            return (config & SPLITTER_MASK) >> SPLITTER_SHIFT;
        }

        public void setCompressionType(int compressionType) {
            if (compressionType < 0 || compressionType > 4)
                throw new RuntimeException("invalid compression type");
            config &= ~COMPRESSION_MASK;
            config |= compressionType << COMPRESSION_SHIFT;
        }

        public int getCompressionType() {
            return (config & COMPRESSION_MASK) >> COMPRESSION_SHIFT;
        }
    }

    private void readHeader(DataInputStream inputStream) throws IOException {
        that.setLevel(inputStream.readByte());

        config.config = inputStream.readByte();
        int splitterType = config.getSplitterType();
        switch (splitterType) {
            case FIXED_BLOCK_SPLITTER_DETAILED:
                int blockSize = inputStream.readInt();
                setNodeSplitter(new FixedBlockSplitter(blockSize));
                break;
            case RABIN_SPLITTER_DETAILED:
                int targetSize = inputStream.readInt();
                int minSize = inputStream.readInt();
                int maxSize = inputStream.readInt();
                setNodeSplitter(new RabinSplitter(targetSize, minSize, maxSize));
                break;
            default:
                throw new IOException("Unknown node splitter type.");
        }
        int compressionType = config.getCompressionType();
        switch (compressionType) {
            case NO_COMPRESSION:
                setZLibCompression(false);
                break;
            case ZLIB_COMPRESSION:
                setZLibCompression(true);
                break;
            default:
                throw new IOException("Unknown compression type.");
        }
    }

    @Override
    protected void writeHeader(DataOutputStream outputStream) throws IOException {
        outputStream.writeByte(that.getLevel());
        outputStream.writeByte(config.config);
        switch (config.getSplitterType()) {
            case FIXED_BLOCK_SPLITTER_DETAILED:
                FixedBlockSplitter fixedBlockSplitter = (FixedBlockSplitter) nodeSplitter;
                outputStream.writeInt(fixedBlockSplitter.getBlockSize());
                break;
            case RABIN_SPLITTER_DETAILED:
                RabinSplitter rabinSplitter = (RabinSplitter) nodeSplitter;
                outputStream.writeInt(rabinSplitter.getTargetChunkSize());
                outputStream.writeInt(rabinSplitter.getMinChunkSize());
                outputStream.writeInt(rabinSplitter.getMaxChunkSize());
                break;
            default:
                throw new IOException("Unsupported node splitter.");
        }

        super.writeHeader(outputStream);
    }
}
