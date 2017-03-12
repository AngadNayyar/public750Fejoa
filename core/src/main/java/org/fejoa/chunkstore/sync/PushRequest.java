/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.chunkstore.sync;

import org.fejoa.library.remote.IRemotePipe;
import org.fejoa.library.support.StorageLib;
import org.fejoa.library.support.StreamHelper;
import org.fejoa.chunkstore.*;
import org.fejoa.library.crypto.CryptoException;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


public class PushRequest {
    final private Repository repository;

    public PushRequest(Repository repository) {
        this.repository = repository;
    }

    private void getChunkContainerNodeChildChunks(ChunkContainerNode node, IChunkAccessor accessor,
                                                  List<HashValue> chunks) throws IOException, CryptoException {
        for (IChunkPointer chunkPointer : node.getChunkPointers()) {
            chunks.add(chunkPointer.getChunkPointer().getBoxHash());
            if (!ChunkContainerNode.isDataPointer(chunkPointer)) {
                ChunkContainerNode child = ChunkContainerNode.read(accessor, node, chunkPointer);
                getChunkContainerNodeChildChunks(child, accessor, chunks);
            }
        }
    }

    private List<HashValue> collectDiffs(IRepoChunkAccessors.ITransaction transaction,
                                         CommonAncestorsFinder.Chains chains)
            throws IOException, CryptoException {
        final List<HashValue> list = new ArrayList<>();
        for (CommonAncestorsFinder.SingleCommitChain chain : chains.chains)
            collectDiffs(transaction, chain, list);
        return list;
    }

    private void collectDiffs(IRepoChunkAccessors.ITransaction transaction, CommitBox parent, CommitBox child,
                              final List<HashValue> list) throws IOException, CryptoException {
        // add the child commit
        collectChunkContainer(child.getRef(), transaction.getCommitAccessor(child.getRef()), list);

        // diff of the commit trees
        FlatDirectoryBox parentDir = null;
        if (parent != null)
            parentDir = FlatDirectoryBox.read(transaction.getTreeAccessor(parent.getTree()), parent.getTree());
        FlatDirectoryBox nextDir = FlatDirectoryBox.read(transaction.getTreeAccessor(child.getTree()), child.getTree());

        // add root dir
        collectChunkContainer(child.getTree(), transaction.getTreeAccessor(child.getTree()), list);

        DirBoxDiffIterator diffIterator = new DirBoxDiffIterator("", parentDir, nextDir);
        while (diffIterator.hasNext()) {
            DirBoxDiffIterator.Change<FlatDirectoryBox.Entry> change = diffIterator.next();
            // we are only interesting in modified and added changes
            if (change.type == DiffIterator.Type.REMOVED)
                continue;

            if (change.theirs.isFile())
                collectFile(transaction, change.theirs.getDataPointer(), change.path, list);
            else
                collectWholeDir(change.path, change.theirs.getDataPointer(), transaction, list);
        }
    }

    private void add(final List<HashValue> list, HashValue pointer) {
        if (!list.contains(pointer))
            list.add(pointer);
    }

    private void collectFile(IRepoChunkAccessors.ITransaction transaction, ChunkContainerRef pointer, String path,
                             final List<HashValue> list) throws IOException, CryptoException {
        IChunkAccessor changeAccessor = transaction.getFileAccessor(pointer, path);
        collectChunkContainer(pointer, changeAccessor, list);
    }

    private void collectChunkContainer(ChunkContainerRef pointer, IChunkAccessor accessor,
                                       final List<HashValue> list) throws IOException, CryptoException {
        add(list, pointer.getBoxHash());
        // TODO: be more efficient and calculate the container diff
        ChunkContainer chunkContainer = ChunkContainer.read(accessor, pointer);
        getChunkContainerNodeChildChunks(chunkContainer, accessor, list);
    }

    private void collectWholeDir(String path, ChunkContainerRef dirPointer,
                                 IRepoChunkAccessors.ITransaction transaction, final List<HashValue> list)
            throws IOException, CryptoException {
        IChunkAccessor dirAccessor = transaction.getTreeAccessor(dirPointer);
        collectChunkContainer(dirPointer, dirAccessor, list);

        FlatDirectoryBox dir = FlatDirectoryBox.read(dirAccessor, dirPointer);
        for (FlatDirectoryBox.Entry entry : dir.getEntries()) {
            String childPath = StorageLib.appendDir(path, entry.getName());
            if (entry.isFile())
                collectFile(transaction, entry.getDataPointer(), childPath, list);
            else {
                collectWholeDir(childPath, entry.getDataPointer(), transaction, list);
            }
        }
    }

    private void collectDiffs(IRepoChunkAccessors.ITransaction transaction,
                              CommonAncestorsFinder.SingleCommitChain chain,
                              final List<HashValue> list)
            throws IOException, CryptoException {
        for (int i = 0; i < chain.commits.size() - 1; i++) {
            CommitBox parent = chain.commits.get(i + 1);
            CommitBox child = chain.commits.get(i);
            if (list.contains(child.getRef().getBoxHash()))
                continue;

            collectDiffs(transaction, parent, child, list);
        }
    }

    public int push(IRemotePipe remotePipe, IRepoChunkAccessors.ITransaction transaction, String branch)
            throws IOException, CryptoException {
        ChunkStore.Transaction rawTransaction = transaction.getRawAccessor();

        // WARNING race condition: We have to use the commit from the branch log because we send this log entry
        // straight to the server. This means the headCommit and the branch log entry must be in sync!
        ChunkStoreBranchLog.Entry localLogTip = repository.getBranchLog().getLatest();
        ChunkContainerRef headCommitPointer = repository.getCommitCallback().commitPointerFromLog(localLogTip.getMessage());
        CommitBox headCommit = CommitBox.read(repository.getCurrentTransaction().getCommitAccessor(headCommitPointer),
                headCommitPointer);
        assert headCommit != null;

        CommonAncestorsFinder.Chains chainsToPush;
        ChunkStoreBranchLog.Entry remoteLogTip = LogEntryRequest.getRemoteTip(remotePipe, branch);

        if (remoteLogTip.getRev() > 0) { // remote has this branch
            ChunkContainerRef remoteTip = repository.getCommitCallback().commitPointerFromLog(remoteLogTip.getMessage());
            if (!rawTransaction.contains(remoteTip.getBoxHash()))
                return Request.PULL_REQUIRED;

            CommitBox remoteCommit;
            try {
                remoteCommit = CommitBox.read(transaction.getCommitAccessor(remoteTip), remoteTip);
            } catch (Exception e) {
                return Request.PULL_REQUIRED;
            }

            assert headCommit != null;
            chainsToPush = CommonAncestorsFinder.find(transaction, remoteCommit, transaction,
                    headCommit);

            boolean remoteCommitIsCommonAncestor = false;
            for (CommonAncestorsFinder.SingleCommitChain chain : chainsToPush.chains) {
                if (chain.getOldest().getPlainHash().equals(remoteCommit.getPlainHash())) {
                    remoteCommitIsCommonAncestor = true;
                    break;
                }
            }
            if (!remoteCommitIsCommonAncestor)
                return Request.PULL_REQUIRED;
        } else {
            chainsToPush = CommonAncestorsFinder.collectAllChains(transaction, headCommit);
            // also push the first commit so add artificial null parents
            for (CommonAncestorsFinder.SingleCommitChain chain : chainsToPush.chains)
                chain.commits.add(chain.commits.size(), null);
        }

        List<HashValue> chunks = collectDiffs(transaction, chainsToPush);
        List<HashValue> remoteChunks = HasChunksRequest.hasChunks(remotePipe, chunks);
        for (HashValue chunk : remoteChunks)
            chunks.remove(chunk);

        // start request
        DataOutputStream outStream = new DataOutputStream(remotePipe.getOutputStream());
        Request.writeRequestHeader(outStream, Request.PUT_CHUNKS);
        StreamHelper.writeString(outStream, branch);
        // expected remote rev
        outStream.write(remoteLogTip.getEntryId().getBytes());
        // our message
        StreamHelper.writeString(outStream, localLogTip.getEntryId().toHex());
        StreamHelper.writeString(outStream, localLogTip.getMessage());
        outStream.writeInt(chunks.size());

        for (HashValue chunk : chunks) {
            outStream.write(chunk.getBytes());
            byte[] buffer = rawTransaction.getChunk(chunk);
            outStream.writeInt(buffer.length);
            outStream.write(buffer);
        }

        // read response
        DataInputStream inputStream = new DataInputStream(remotePipe.getInputStream());
        int status = Request.receiveHeader(inputStream, Request.PUT_CHUNKS);
        return status;
    }
}
