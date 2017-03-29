/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.chunkstore;

import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.crypto.CryptoHelper;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;


public class ChunkContainerNode implements IChunk {
    static final public int DATA_LEVEL = 0;
    static final protected int LEAF_LEVEL = DATA_LEVEL + 1;

    final protected IChunkPointer that;
    final protected MessageDigest messageDigest;
    protected boolean onDisk = false;
    protected ChunkContainerNode parent;
    final protected IChunkAccessor blobAccessor;
    private byte[] data;
    private HashValue dataHash;
    final private List<IChunkPointer> slots = new ArrayList<>();
    protected ChunkSplitter nodeSplitter;

    static public ChunkContainerNode create(IChunkAccessor blobAccessor, ChunkContainerNode parent,
                                            ChunkSplitter nodeSplitter, int level, MessageDigest messageDigest) {
        return new ChunkContainerNode(blobAccessor, parent, nodeSplitter, level, messageDigest);
    }

    static public ChunkContainerNode read(IChunkAccessor blobAccessor, ChunkContainerNode parent, IChunkPointer that)
            throws IOException, CryptoException {
        ChunkContainerNode node =  new ChunkContainerNode(blobAccessor, parent, parent.nodeSplitter, that,
                parent.messageDigest);
        DataInputStream inputStream = blobAccessor.getChunk(that.getChunkPointer());
        node.read(inputStream, that.getDataLength());
        return node;
    }

    protected ChunkContainerNode(IChunkAccessor blobAccessor, ChunkContainerNode parent, ChunkSplitter nodeSplitter,
                                 int level, MessageDigest messageDigest) {
        this.blobAccessor = blobAccessor;
        this.parent = parent;
        this.that = new ChunkPointerImpl(null, this, level);
        this.messageDigest = messageDigest;
        setNodeSplitter(nodeSplitter);
    }

    private ChunkContainerNode(IChunkAccessor blobAccessor, ChunkContainerNode parent, ChunkSplitter nodeSplitter,
                               IChunkPointer that, MessageDigest messageDigest) {
        this.blobAccessor = blobAccessor;
        this.parent = parent;
        this.that = that;
        this.messageDigest = messageDigest;
        setNodeSplitter(nodeSplitter);
    }

    public IChunkPointer getChunkPointer() {
        return that;
    }

    public void setParent(ChunkContainerNode parent) {
        this.parent = parent;
    }

    public ChunkContainerNode getParent() {
        return parent;
    }

    public boolean isLeafNode() {
        return that.getLevel() == LEAF_LEVEL;
    }

    static public boolean isDataPointer(IChunkPointer pointer) {
        return pointer.getLevel() == DATA_LEVEL;
    }

    protected void setNodeSplitter(ChunkSplitter nodeSplitter) {
        this.nodeSplitter = nodeSplitter;
        if (this.nodeSplitter != null)
            this.nodeSplitter.reset();
    }

    protected int getHeaderLength() {
        return 0;
    }

    @Override
    public long getDataLength() {
        return calculateDataLength();
    }

    protected DataChunk getDataChunk(IChunkPointer pointer) throws IOException, CryptoException {
        assert(isDataPointer(pointer));
        IChunk cachedChunk = pointer.getCachedChunk();
        if (cachedChunk != null)
            return (DataChunk)cachedChunk;
        DataInputStream inputStream = blobAccessor.getChunk(pointer.getChunkPointer());
        DataChunk dataChunk = new DataChunk();
        dataChunk.read(inputStream, pointer.getDataLength());
        pointer.setCachedChunk(dataChunk);
        return dataChunk;
    }

    protected ChunkContainerNode getNode(IChunkPointer pointer) throws IOException, CryptoException {
        assert !isDataPointer(pointer);
        IChunk cachedChunk = pointer.getCachedChunk();
        if (cachedChunk != null)
            return (ChunkContainerNode)cachedChunk;

        assert slots.contains(pointer);

        ChunkContainerNode node = ChunkContainerNode.read(blobAccessor, this, pointer);
        pointer.setCachedChunk(node);
        return node;
    }

    protected long calculateDataLength() {
        long length = 0;
        for (IChunkPointer pointer : slots)
            length += pointer.getDataLength();
        return length;
    }

    public int getBlobLength() {
        int length = 4; // number of slots (int);
        length += slots.size() * that.getPointerLength();

        return length;
    }

    public int getLevel() {
        return that.getLevel();
    }

    static class SearchResult {
        final long pointerDataPosition;
        final IChunkPointer pointer;
        final ChunkContainerNode node;

        SearchResult(long pointerDataPosition, IChunkPointer pointer, ChunkContainerNode node) {
            this.pointerDataPosition = pointerDataPosition;
            this.pointer = pointer;
            this.node = node;
        }
    }

    /**
     *
     * @param dataPosition relative to this node
     * @return
     */
    protected SearchResult findInNode(ChunkContainerNode node, final long dataPosition)
            throws IOException, CryptoException {
        if (dataPosition > node.getDataLength())
            return null;

        long position = 0;
        for (int i = 0; i < node.slots.size(); i++) {
            IChunkPointer pointer = node.slots.get(i);
            long dataLength = pointer.getDataLength();
            if (position + dataLength > dataPosition)
                return new SearchResult(position, pointer, node);
            position += dataLength;
        }
        return new SearchResult(position, null, node);
    }

    @Override
    public void read(DataInputStream inputStream, long dataLength) throws IOException {
        slots.clear();
        long dataLengthRead = 0;
        while (dataLengthRead < dataLength) {
            IChunkPointer pointer = new ChunkPointerImpl(that.getLevel() - 1);
            pointer.read(inputStream);
            dataLengthRead += pointer.getDataLength();
            addBlobPointer(pointer);
        }
        if (dataLengthRead != dataLength) {
            throw new IOException("Chunk container node addresses " + dataLengthRead + " bytes but " + dataLength
                    + " bytes expected");
        }
        onDisk = true;
    }

    protected void writeHeader(DataOutputStream outputStream) throws IOException {

    }

    @Override
    public void write(DataOutputStream outputStream) throws IOException {
        for (int i = 0; i < slots.size(); i++) {
            IChunkPointer pointer = slots.get(i);
            pointer.write(outputStream);
        }
    }

    private ChunkContainerNode findRightNeighbour() throws IOException, CryptoException {
        if (parent == null)
            return null;

        // find common parent
        int indexInParent = -1;
        ChunkContainerNode parent = this;
        int levelDiff = 0;
        while (parent.getParent() != null) {
            levelDiff++;
            IChunkPointer pointerInParent = parent.that;
            parent = parent.getParent();
            indexInParent = parent.indexOf(pointerInParent);
            assert indexInParent >= 0;
            if (indexInParent != parent.size() - 1)
                break;
        }

        // is last pointer?
        if (indexInParent == parent.size() - 1)
            return null;

        ChunkContainerNode neighbour =  parent.getNode(parent.get(indexInParent + 1));
        for (int i = 0; i < levelDiff - 1; i++)
            neighbour = neighbour.getNode(neighbour.get(0));

        assert neighbour.that.getLevel() == that.getLevel();
        return neighbour;
    }

    private void splitAt(int index) throws IOException {
        ChunkContainerNode right = ChunkContainerNode.create(blobAccessor, parent, nodeSplitter, that.getLevel(),
                messageDigest);
        right.setNodeSplitter(nodeSplitter);
        while (size() > index)
            right.addBlobPointer(removeBlobPointer(index));
        if (parent != null) {
            int inParentIndex = parent.indexOf(that);
            parent.addBlobPointer(inParentIndex + 1, right.that);
        } else {
            // move item item to new child
            ChunkContainerNode left = ChunkContainerNode.create(blobAccessor, parent, nodeSplitter, that.getLevel(),
                    messageDigest);
            while (size() > 0)
                left.addBlobPointer(removeBlobPointer(0));
            addBlobPointer(left.that);
            addBlobPointer(right.that);
            that.setLevel(that.getLevel() + 1);
        }
    }

    private void balance() throws IOException, CryptoException {
        nodeSplitter.reset();
        int size = size();
        for (int i = 0; i < size; i++) {
            IChunkPointer child = get(i);
            nodeSplitter.write(child.getChunkPointer().getDataHash().getBytes());
            if (nodeSplitter.isTriggered()) {
                if (i == size - 1) // all good
                    return;
                // split left over into a right node
                splitAt(i + 1);
                return;
            }
        }

        // we are not full; get pointers from the right neighbour till we are full
        ChunkContainerNode neighbour = findRightNeighbour();
        if (neighbour != null) {
            while (neighbour.size() > 0) {
                ChunkContainerNode nextNeighbour = null;
                // we need one item to find the next right neighbour
                if (neighbour.size() == 1)
                    nextNeighbour = neighbour.findRightNeighbour();

                IChunkPointer pointer = neighbour.removeBlobPointer(0, true);
                addBlobPointer(pointer);
                nodeSplitter.write(pointer.getChunkPointer().getDataHash().getBytes());
                if (nodeSplitter.isTriggered()) {
                    // if the parent is the root node check if the root node is redundant else we are done
                    if (getParent() != null && getParent().getParent() == null)
                        break;
                    else
                        return;
                }

                if (nextNeighbour != null)
                    neighbour = nextNeighbour;
            }
        }

        // Since we merged all right neighbours in; we have to check if the root is redundant
        if (getParent() != null && getParent().size() == 1) {
            int level = that.getLevel();
            ChunkContainerNode root = getRoot();
            root.removeBlobPointer(0);
            while (size() > 0)
                root.addBlobPointer(removeBlobPointer(0));

            root.that.setLevel(level);
        }
    }

    private ChunkContainerNode getRoot() {
        ChunkContainerNode root = this;
        while (root.getParent() != null)
            root = root.getParent();
        return root;
    }

    /**
     * The idea is to flush item from the left to the right
     *
     * @param childOnly only child nodes are flushed
     * @throws IOException
     */
    public void flush(boolean childOnly) throws IOException, CryptoException {
        int level = that.getLevel();
        if (level > LEAF_LEVEL) {
            // IMPORTANT: the slot size may grow when flushing the child so check in each iteration!
            for (int i = 0; i < slots.size(); i++) {
                IChunkPointer pointer = slots.get(i);
                ChunkContainerNode blob = (ChunkContainerNode) pointer.getCachedChunk();
                if (blob == null || blob.onDisk)
                    continue;
                blob.flush(false);
                if (that.getLevel() != level) {
                    // only the root node can change its level
                    assert getParent() == null;
                    flush(childOnly);
                    return;
                }
            }
        }

        if (!childOnly) {
            balance();
            if (that.getLevel() != level) {
                // only the root node can change its level
                assert getParent() == null;
                flush(childOnly);
                return;
            }

            HashValue oldBoxHash = that.getChunkPointer().getBoxHash();
            HashValue boxHash = writeNode();
            // cleanup old chunk
            if (!boxHash.equals(oldBoxHash) && !oldBoxHash.isZero())
                blobAccessor.releaseChunk(oldBoxHash);

            if (parent != null)
                parent.invalidate();

            that.setChunkPointer(new ChunkPointer(hash(messageDigest), boxHash, rawHash()));

            onDisk = true;
        }
    }

    protected HashValue writeNode() throws IOException, CryptoException {
        byte[] data = getData();
        return blobAccessor.putChunk(data, rawHash()).key;
    }

    @Override
    public byte[] getData() throws IOException {
        if (data != null)
            return data;
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        write(new DataOutputStream(outputStream));
        data = outputStream.toByteArray();
        return data;
    }

    @Override
    public String toString() {
        String string = "Data Hash: " + hash(messageDigest) + "\n";
        for (IChunkPointer pointer : slots)
            string += pointer.toString() + "\n";
        return string;
    }

    protected String printAll() throws Exception {
        String string = toString();

        if (that.getLevel() == LEAF_LEVEL)
            return string;
        for (IChunkPointer pointer : slots)
            string += getNode(pointer).printAll();
        return string;
    }

    public HashValue hash() {
        return hash(messageDigest);
    }

    @Override
    public HashValue hash(MessageDigest messageDigest) {
        if (dataHash == null)
            dataHash = calculateDataHash(messageDigest);
        // TODO: that seems wrong, why not just return dataHash?
        return calculateDataHash(messageDigest);
    }

    protected HashValue rawHash() throws IOException {
        return new HashValue(CryptoHelper.hash(getData(), messageDigest));
    }

    protected ChunkPointer getBoxPointer() {
        return that.getChunkPointer();
    }

    private HashValue calculateDataHash(MessageDigest messageDigest) {
        messageDigest.reset();
        for (IChunkPointer pointer : slots)
            messageDigest.update(pointer.getChunkPointer().getDataHash().getBytes());
        return new HashValue(messageDigest.digest());
    }

    protected void invalidate() {
        data = null;
        dataHash = null;
        onDisk = false;
        if (parent != null)
            parent.invalidate();
    }

    protected void addBlobPointer(int index, IChunkPointer pointer) throws IOException {
        slots.add(index, pointer);
        if (!isDataPointer(pointer) && pointer.getCachedChunk() != null)
            ((ChunkContainerNode)pointer.getCachedChunk()).setParent(this);
        invalidate();
    }

    protected void addBlobPointer(IChunkPointer pointer) throws IOException {
        addBlobPointer(slots.size(), pointer);
    }

    protected IChunkPointer removeBlobPointer(int i) {
        return removeBlobPointer(i, false);
    }

    protected IChunkPointer removeBlobPointer(int i, boolean updateParentsIfEmpty) {
        IChunkPointer pointer = slots.remove(i);
        invalidate();
        if (updateParentsIfEmpty && parent != null && slots.size() == 0) {
            int inParentIndex = parent.indexOf(that);
            parent.removeBlobPointer(inParentIndex, true);
        }
        return pointer;
    }

    protected int size() {
        return slots.size();
    }

    protected IChunkPointer get(int index) {
        return slots.get(index);
    }

    public List<IChunkPointer> getChunkPointers() {
        return slots;
    }

    protected int indexOf(IChunkPointer pointer) {
        return slots.indexOf(pointer);
    }

    protected void clear() {
        slots.clear();
        invalidate();
    }
}
