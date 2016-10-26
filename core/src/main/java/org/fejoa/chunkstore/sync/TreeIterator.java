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
import org.fejoa.chunkstore.IChunkAccessor;
import org.fejoa.library.crypto.CryptoException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;


public class TreeIterator implements Iterator<DiffIterator.Change<FlatDirectoryBox.Entry>> {
    final private IChunkAccessor ourAccessor;
    final private IChunkAccessor theirAccessor;
    final private List<DirBoxDiffIterator> iterators = new ArrayList<>();
    private DirBoxDiffIterator current;

    public TreeIterator(IChunkAccessor ourAccessor, CommitBox ours, IChunkAccessor theirAccessor,
                        CommitBox theirs) throws IOException, CryptoException {
        this(ourAccessor, ours == null ? null : FlatDirectoryBox.read(ourAccessor, ours.getTree()),
                theirAccessor, FlatDirectoryBox.read(theirAccessor, theirs.getTree()));
    }

    public TreeIterator(IChunkAccessor ourAccessor, FlatDirectoryBox ours, IChunkAccessor theirAccessor,
                        FlatDirectoryBox theirs) {
        this.ourAccessor = ourAccessor;
        this.theirAccessor = theirAccessor;
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
                iterators.add(new DirBoxDiffIterator(next.path, FlatDirectoryBox.read(ourAccessor, next.ours.getDataPointer()),
                        FlatDirectoryBox.read(theirAccessor, next.theirs.getDataPointer())));
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
