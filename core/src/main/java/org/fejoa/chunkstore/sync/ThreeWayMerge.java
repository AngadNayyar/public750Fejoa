/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.chunkstore.sync;

import org.fejoa.chunkstore.*;
import org.fejoa.library.crypto.CryptoException;

import java.io.IOException;


public class ThreeWayMerge {
    public interface IConflictSolver {
        FlatDirectoryBox.Entry solve(FlatDirectoryBox.Entry ours, FlatDirectoryBox.Entry theirs);
    }

    static public IConflictSolver ourSolver() {
        return new IConflictSolver() {
            @Override
            public FlatDirectoryBox.Entry solve(FlatDirectoryBox.Entry ours, FlatDirectoryBox.Entry theirs) {
                return ours;
            }
        };
    }

    static public TreeAccessor merge(IRepoChunkAccessors.ITransaction outTransaction,
                                     IRepoChunkAccessors.ITransaction ourTransaction, CommitBox ours,
                                     IRepoChunkAccessors.ITransaction theirTransaction,
                                     CommitBox theirs, CommitBox parent, IConflictSolver conflictSolver,
                                     boolean compression)
            throws IOException, CryptoException {
        IChunkAccessor ourAccessor = ourTransaction.getTreeAccessor();
        IChunkAccessor theirAccessor = ourTransaction.getTreeAccessor();
        FlatDirectoryBox ourRoot = FlatDirectoryBox.read(ourAccessor, ours.getTree());
        FlatDirectoryBox theirRoot = FlatDirectoryBox.read(theirAccessor, theirs.getTree());
        TreeIterator treeIterator = new TreeIterator(ourAccessor, ourRoot, theirAccessor, theirRoot);

        FlatDirectoryBox parentRoot = FlatDirectoryBox.read(ourAccessor, parent.getTree());
        TreeAccessor parentTreeAccessor = new TreeAccessor(parentRoot, ourTransaction, compression);
        TreeAccessor ourTreeAccessor = new TreeAccessor(FlatDirectoryBox.read(ourAccessor, ours.getTree()),
                ourTransaction, compression);
        TreeAccessor theirTreeAccessor = new TreeAccessor(FlatDirectoryBox.read(theirAccessor, theirs.getTree()),
                theirTransaction, compression);

        TreeAccessor outTree = new TreeAccessor(ourRoot, outTransaction, compression);

        while (treeIterator.hasNext()) {
            DiffIterator.Change<FlatDirectoryBox.Entry> change = treeIterator.next();
            if (change.type == DiffIterator.Type.ADDED) {
                FlatDirectoryBox.Entry parentEntry = parentTreeAccessor.get(change.path);
                if (parentEntry == null) {
                    // add to ours
                    outTree.put(change.path, change.theirs);
                }
            } else if (change.type == DiffIterator.Type.REMOVED) {
                FlatDirectoryBox.Entry parentEntry = parentTreeAccessor.get(change.path);
                if (parentEntry != null) {
                    // remove from ours
                    outTree.remove(change.path);
                }
            } else if (change.type == DiffIterator.Type.MODIFIED) {
                FlatDirectoryBox.Entry ourEntry = ourTreeAccessor.get(change.path);
                if (!ourEntry.isFile())
                    continue;
                FlatDirectoryBox.Entry theirEntry = theirTreeAccessor.get(change.path);
                outTree.put(change.path, conflictSolver.solve(ourEntry, theirEntry));
            }
        }

        return outTree;
    }
}
