/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.chunkstore;

import org.fejoa.library.crypto.CryptoException;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;


public class CommitCache {
    final private Map<HashValue, CommitBox> commitCache = new HashMap<>();
    // All commits in this list as well are their parent are in the cached.
    final private LinkedList<CommitBox> tailList = new LinkedList<>();
    final private Repository repository;

    public CommitCache(Repository repository) {
        this.repository = repository;
    }

    public CommitBox getCommit(HashValue hashValue) throws IOException, CryptoException {
        CommitBox commitBox = commitCache.get(hashValue);
        if (commitBox != null)
            return commitBox;
        return loadCommit(hashValue);
    }

    public boolean isParent(HashValue commit, HashValue isParentCommit) throws IOException, CryptoException {
        CommitBox commitBox = getCommit(commit);
        for (BoxPointer parent : commitBox.getParents()) {
            CommitBox parentCommit = CommitBox.read(getCommitAccessor(), parent);
            HashValue parentHash = parentCommit.dataHash();
            if (parentHash.equals(isParentCommit))
                return true;
            if (isParent(parentHash, isParentCommit))
                return true;
        }
        return false;
    }

    private CommitBox loadCommit(final HashValue hashValue) throws IOException, CryptoException {
        CommitBox head = repository.getHeadCommit();
        if (head == null)
            return null;
        HashValue headHash = head.dataHash();
        if (!commitCache.containsKey(headHash)) {
            commitCache.put(headHash, head);
            tailList.addFirst(head);
            if (headHash.equals(hashValue))
                return head;
        }

        while (tailList.size() > 0) {
            CommitBox currentCommit = tailList.removeFirst();
            CommitBox foundCommit = null;
            for (BoxPointer boxPointer : currentCommit.getParents()) {
                CommitBox parent = CommitBox.read(getCommitAccessor(), boxPointer);
                HashValue parentHash = parent.dataHash();
                if (parentHash.equals(hashValue))
                    foundCommit = parent;
                if (!commitCache.containsKey(parentHash)) {
                    tailList.add(parent);
                    commitCache.put(parentHash, parent);
                }
            }
            if (foundCommit != null)
                return foundCommit;
        }
        return null;
    }

    private IChunkAccessor getCommitAccessor() {
        return repository.getCurrentTransaction().getCommitAccessor();
    }
}
