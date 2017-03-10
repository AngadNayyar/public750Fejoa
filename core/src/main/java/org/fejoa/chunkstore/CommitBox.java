/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.chunkstore;

import org.fejoa.library.crypto.CryptoException;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


public class CommitBox extends ChunkContainerRefBox {
    private ChunkContainerRef tree;
    final private List<ChunkContainerRef> parents = new ArrayList<>();
    private byte[] commitMessage;

    private CommitBox() {
        super(BlobTypes.COMMIT);
    }

    static public CommitBox create() {
        CommitBox commitBox = new CommitBox();
        commitBox.setRef(new ChunkContainerRef());
        return commitBox;
    }

    static public CommitBox read(IChunkAccessor accessor, ChunkContainerRef ref)
            throws IOException, CryptoException {
        ChunkContainer chunkContainer = ChunkContainer.read(accessor, ref);
        return read(chunkContainer);
    }

    static public CommitBox read(ChunkContainer chunkContainer)
            throws IOException, CryptoException {
        return read(BlobTypes.COMMIT, new DataInputStream(new ChunkContainerInputStream(chunkContainer)),
                chunkContainer.getRef());
    }

    static private CommitBox read(short type, DataInputStream inputStream, ChunkContainerRef ref) throws IOException {
        assert type == BlobTypes.COMMIT;
        CommitBox commitBox = new CommitBox();
        commitBox.read(inputStream, ref);
        return commitBox;
    }

    public void setTree(ChunkContainerRef tree) {
        this.tree = tree;
    }

    public ChunkContainerRef getTree() {
        return tree;
    }

    public void setCommitMessage(byte[] commitMessage) {
        this.commitMessage = commitMessage;
    }

    public byte[] getCommitMessage() {
        return commitMessage;
    }

    public void addParent(ChunkContainerRef parent) {
        parents.add(parent);
    }

    public List<ChunkContainerRef> getParents() {
        return parents;
    }

    @Override
    protected void readPlain(DataInputStream inputStream, List<ChunkContainerRef> readRefs) throws IOException {
        this.setTree(new ChunkContainerRef());
        this.getTree().getData().read(inputStream);
        readRefs.add(this.getTree());

        int commitMessageSize = inputStream.readInt();
        this.commitMessage = new byte[commitMessageSize];
        inputStream.readFully(commitMessage);

        short numberOfParents = inputStream.readShort();
        for (int i = 0; i < numberOfParents; i++) {
            ChunkContainerRef parent = new ChunkContainerRef();
            parent.getData().read(inputStream);
            addParent(parent);
            readRefs.add(parent);
        }
    }

    @Override
    protected void writePlain(DataOutputStream outputStream, List<ChunkContainerRef> writtenRefs) throws IOException {
        getTree().getData().write(outputStream);
        writtenRefs.add(getTree());

        outputStream.writeInt(commitMessage.length);
        outputStream.write(commitMessage);

        List<ChunkContainerRef> parents = getParents();
        outputStream.writeShort(parents.size());
        for (ChunkContainerRef parent : parents) {
            parent.getData().write(outputStream);
            writtenRefs.add(parent);
        }
    }

    @Override
    public String toString() {
        if (tree == null || commitMessage == null)
            return "invalid";
        String out = "Tree: " + tree + "\n";
        out += "Commit data: " + new String(commitMessage) + "\n";
        out += "" + parents.size() + " parents: ";
        for (ChunkContainerRef parent : parents)
            out += "\n\t" + parent;
        return out;
    }
}
