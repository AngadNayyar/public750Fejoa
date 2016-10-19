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
        DirectoryBox.Entry solve(DirectoryBox.Entry ours, DirectoryBox.Entry theirs);
    }

    static public IConflictSolver ourSolver() {
        return new IConflictSolver() {
            @Override
            public DirectoryBox.Entry solve(DirectoryBox.Entry ours, DirectoryBox.Entry theirs) {
                return ours;
            }
        };
    }

    static public TreeAccessor merge(IRepoChunkAccessors.ITransaction outTransaction,
                                     IRepoChunkAccessors.ITransaction ourTransaction, CommitBox ours,
                                     IRepoChunkAccessors.ITransaction theirTransaction,
                                     CommitBox theirs, CommitBox parent, IConflictSolver conflictSolver)
            throws IOException, CryptoException {
        IChunkAccessor ourAccessor = ourTransaction.getTreeAccessor();
        IChunkAccessor theirAccessor = ourTransaction.getTreeAccessor();
        DirectoryBox ourRoot = DirectoryBox.read(ourAccessor, ours.getTree());
        DirectoryBox theirRoot = DirectoryBox.read(theirAccessor, theirs.getTree());
        TreeIterator treeIterator = new TreeIterator(ourAccessor, ourRoot, theirAccessor, theirRoot);

        DirectoryBox parentRoot = DirectoryBox.read(ourAccessor, parent.getTree());
        TreeAccessor parentTreeAccessor = new TreeAccessor(parentRoot, ourTransaction);
        TreeAccessor ourTreeAccessor = new TreeAccessor(DirectoryBox.read(ourAccessor, ours.getTree()), ourTransaction);
        TreeAccessor theirTreeAccessor = new TreeAccessor(DirectoryBox.read(theirAccessor, theirs.getTree()),
                theirTransaction);

        TreeAccessor outTree = new TreeAccessor(ourRoot, outTransaction);

        while (treeIterator.hasNext()) {
            DiffIterator.Change<DirectoryBox.Entry> change = treeIterator.next();
            if (change.type == DiffIterator.Type.ADDED) {
                DirectoryBox.Entry parentEntry = parentTreeAccessor.get(change.path);
                if (parentEntry == null) {
                    // add to ours
                    outTree.put(change.path, change.theirs);
                }
            } else if (change.type == DiffIterator.Type.REMOVED) {
                DirectoryBox.Entry parentEntry = parentTreeAccessor.get(change.path);
                if (parentEntry != null) {
                    // remove from ours
                    outTree.remove(change.path);
                }
            } else if (change.type == DiffIterator.Type.MODIFIED) {
                DirectoryBox.Entry ourEntry = ourTreeAccessor.get(change.path);
                if (!ourEntry.isFile())
                    continue;
                DirectoryBox.Entry theirEntry = theirTreeAccessor.get(change.path);
                outTree.put(change.path, conflictSolver.solve(ourEntry, theirEntry));
            }
        }

        return outTree;
    }
}