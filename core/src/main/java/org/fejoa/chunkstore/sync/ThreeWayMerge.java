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
        FlatDirectoryBox.Entry solve(String path, FlatDirectoryBox.Entry ours, FlatDirectoryBox.Entry theirs);
    }

    static public IConflictSolver ourSolver() {
        return new IConflictSolver() {
            @Override
            public FlatDirectoryBox.Entry solve(String path, FlatDirectoryBox.Entry ours,
                                                FlatDirectoryBox.Entry theirs) {
                return ours;
            }
        };
    }

    static public TreeAccessor merge(IRepoChunkAccessors.ITransaction outTransaction,
                                     IRepoChunkAccessors.ITransaction ourTransaction, CommitBox ours,
                                     IRepoChunkAccessors.ITransaction theirTransaction,
                                     CommitBox theirs, CommitBox parent, IConflictSolver conflictSolver)
            throws IOException, CryptoException {
        FlatDirectoryBox ourRoot = FlatDirectoryBox.read(ourTransaction.getTreeAccessor(ours.getTree()),
                ours.getTree());
        FlatDirectoryBox theirRoot = FlatDirectoryBox.read(theirTransaction.getTreeAccessor(theirs.getTree()),
                theirs.getTree());
        TreeIterator treeIterator = new TreeIterator(ourTransaction, ourRoot, theirTransaction, theirRoot);

        FlatDirectoryBox parentRoot = FlatDirectoryBox.read(ourTransaction.getTreeAccessor(parent.getTree()),
                parent.getTree());
        TreeAccessor parentTreeAccessor = new TreeAccessor(parentRoot, ourTransaction);
        TreeAccessor ourTreeAccessor = new TreeAccessor(FlatDirectoryBox.read(
                ourTransaction.getTreeAccessor(ours.getTree()), ours.getTree()), ourTransaction);
        TreeAccessor theirTreeAccessor = new TreeAccessor(FlatDirectoryBox.read(
                theirTransaction.getTreeAccessor(theirs.getTree()), theirs.getTree()), theirTransaction);

        TreeAccessor outTree = new TreeAccessor(ourRoot, outTransaction);

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
                outTree.put(change.path, conflictSolver.solve(change.path, ourEntry, theirEntry));
            }
        }

        return outTree;
    }
}
