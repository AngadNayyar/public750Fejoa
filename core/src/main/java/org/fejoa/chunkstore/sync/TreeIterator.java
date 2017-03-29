/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.chunkstore.sync;

import org.fejoa.chunkstore.CommitBox;
import org.fejoa.chunkstore.FlatDirectoryBox;
import org.fejoa.chunkstore.IRepoChunkAccessors;
import org.fejoa.library.crypto.CryptoException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;


public class TreeIterator implements Iterator<DiffIterator.Change<FlatDirectoryBox.Entry>> {
    final private IRepoChunkAccessors.ITransaction ourTransaction;
    final private IRepoChunkAccessors.ITransaction theirTransaction;
    final private List<DirBoxDiffIterator> iterators = new ArrayList<>();
    private DirBoxDiffIterator current;

    public TreeIterator(IRepoChunkAccessors.ITransaction ourTransaction, CommitBox ours,
                        IRepoChunkAccessors.ITransaction theirTransaction,
                        CommitBox theirs) throws IOException, CryptoException {
        this(ourTransaction, ours == null ? null : FlatDirectoryBox.read(ourTransaction.getTreeAccessor(ours.getTree()),
                ours.getTree()), theirTransaction,
                FlatDirectoryBox.read(theirTransaction.getTreeAccessor(theirs.getTree()), theirs.getTree()));
    }

    public TreeIterator(IRepoChunkAccessors.ITransaction ourTransaction, FlatDirectoryBox ours,
                        IRepoChunkAccessors.ITransaction theirTransaction, FlatDirectoryBox theirs) {
        this.ourTransaction = ourTransaction;
        this.theirTransaction = theirTransaction;
        current = new DirBoxDiffIterator("", ours, theirs);
    }

    @Override
    public boolean hasNext() {
        return current.hasNext();
    }

    @Override
    public DiffIterator.Change<FlatDirectoryBox.Entry> next() {
        DiffIterator.Change<FlatDirectoryBox.Entry> next = current.next();
        if (next.type == DiffIterator.Type.MODIFIED && !next.ours.isFile() && !next.theirs.isFile()) {
            try {
                iterators.add(new DirBoxDiffIterator(next.path, FlatDirectoryBox.read(
                        ourTransaction.getTreeAccessor(next.ours.getDataPointer()),
                        next.ours.getDataPointer()),
                        FlatDirectoryBox.read(theirTransaction.getTreeAccessor(next.theirs.getDataPointer()),
                                next.theirs.getDataPointer())));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (!hasNext() && iterators.size() > 0)
            current = iterators.remove(0);
        return next;
    }

    @Override
    public void remove() {

    }
}
