/*
 * Copyright 2016-2017.
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


class ChunkPointerImpl implements IChunkPointer {
    private ChunkPointer chunkPointer;

    private IChunk cachedChunk = null;
    protected int level;

    protected ChunkPointerImpl(int level) {
        this.chunkPointer = new ChunkPointer();
        this.level = level;
    }

    protected ChunkPointerImpl(ChunkPointer hash, IChunk blob, int level) {
        if (hash != null)
            this.chunkPointer = hash;
        else
            this.chunkPointer = new ChunkPointer();
        cachedChunk = blob;
        this.level = level;
    }

    @Override
    public int getPointerLength() {
        return getPointerLengthStatic();
    }

    static public int getPointerLengthStatic() {
        return ChunkPointer.getPointerLength();
    }

    @Override
    public long getDataLength() {
        if (cachedChunk != null)
            chunkPointer.setDataLength(cachedChunk.getDataLength());
        return chunkPointer.getDataLength();
    }

    public void setChunkPointer(ChunkPointer chunkPointer) {
        this.chunkPointer = chunkPointer;
    }

    public ChunkPointer getChunkPointer() {
        return chunkPointer;
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
        chunkPointer.read(inputStream);
    }

    public void write(DataOutputStream outputStream) throws IOException {
        // update the data length
        chunkPointer.setDataLength(getDataLength());
        chunkPointer.write(outputStream);
    }

    @Override
    public String toString() {
        String string = "l:" + chunkPointer.getDataLength();
        if (chunkPointer != null)
            string+= "," + chunkPointer.toString();
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
    static public ChunkContainer read(IChunkAccessor blobAccessor, ChunkContainerRef ref)
            throws IOException, CryptoException {
        return new ChunkContainer(blobAccessor, blobAccessor.getChunk(ref.getBoxPointer()), ref);
    }

    final private ChunkContainerRef ref;
    final private CacheManager cacheManager;

    /**
     * Create a new chunk container.
     *
     * @param blobAccessor
     */
    public ChunkContainer(IChunkAccessor blobAccessor, ChunkContainerRef ref) throws IOException {
        super(blobAccessor, null, getNodeSplitter(ref.getData().getContainerHeader()), LEAF_LEVEL,
                ref.getDataMessageDigest());
        this.ref = ref;
        setNodeSplitter(getNodeSplitter(ref.getContainerHeader()));
        this.cacheManager = new CacheManager(this);
    }

    /**
     * Load an existing chunk container.
     */
    private ChunkContainer(IChunkAccessor blobAccessor, DataInputStream inputStream, ChunkContainerRef ref)
            throws IOException {
        super(blobAccessor, null, null, LEAF_LEVEL, ref.getDataMessageDigest());
        this.ref = ref;
        setNodeSplitter(getNodeSplitter(ref.getContainerHeader()));
        that.setLevel(ref.getContainerHeader().getLevel());
        that.setChunkPointer(ref.getBoxPointer());
        read(inputStream, ref.getContainerHeader().getDataLength());

        cacheManager = new CacheManager(this);
    }

    /**
     * Splitter for the nodes.
     */
    static private ChunkSplitter getNodeSplitter(ChunkContainerHeader header) {
        float kFactor = getNodeToDataSplittingRatio();
        return header.getSplitter(kFactor);
    }

    public ChunkSplitter getNodeSplitter() {
        return getNodeSplitter(ref.getContainerHeader());
    }

    static private float getNodeToDataSplittingRatio() {
        return  (float)Config.DATA_HASH_SIZE / ChunkPointerImpl.getPointerLengthStatic();
    }

    public ChunkContainerRef getRef() {
        return ref;
    }

    @Override
    public void flush(boolean childOnly) throws IOException, CryptoException {
        super.flush(childOnly);

        ref.getData().setDataHash(that.getChunkPointer().getDataHash());
        ref.getBox().setBoxHash(that.getChunkPointer().getBoxHash());
        ref.getBox().setIv(that.getChunkPointer().getIV());

        ref.getContainerHeader().setLevel(getNLevels());
        ref.getContainerHeader().setDataLength(getDataLength());
    }

    /**
     * Splitter for the leaf nodes.
     *
     * The leaf nodes are split externally, i.e.by the ChunkContainerOutputStream.
     */
    public ChunkSplitter getChunkSplitter() {
        return ref.getData().getContainerHeader().getSplitter(1f);
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
        final public long chunkDataLength;

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

        public long getDataLength() {
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
        HashValue hash = blob.hash(messageDigest);
        HashValue boxedHash = blobAccessor.putChunk(rawBlob, hash).key;
        ChunkPointer chunkPointer = new ChunkPointer(hash, boxedHash, hash, rawBlob.length);
        return new ChunkPointerImpl(chunkPointer, blob, DATA_LEVEL);
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

    public String printAll() throws Exception {
        String string = "Levels=" + that.getLevel() + ", length=" + getDataLength() + "\n";
        string += super.printAll();
        return string;
    }
}
