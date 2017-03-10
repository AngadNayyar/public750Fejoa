/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.chunkstore;

import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.database.AsyncDatabase;
import org.fejoa.library.database.ICommitSignature;

import java.io.File;
import java.io.IOException;
import java.util.Collection;


public class Repository extends AsyncDatabase {
    public Repository(File dir, String branch, HashValue commit, IRepoChunkAccessors chunkAccessors,
                      ICommitCallback commitCallback) throws IOException, CryptoException {
        super(new SyncRepository(dir, branch, commit, chunkAccessors, commitCallback));
    }

    public Repository(File dir, String branch, IRepoChunkAccessors chunkAccessors,
                      ICommitCallback commitCallback) throws IOException, CryptoException {
        super(new SyncRepository(dir, branch, chunkAccessors, commitCallback));
    }

    public Repository(Repository parent, CommitBox headCommit) throws IOException, CryptoException {
        super(parent, new SyncRepository(parent.getSyncRepo(), headCommit));
    }

    private SyncRepository getSyncRepo() {
        return (SyncRepository) syncDatabase;
    }

    public ICommitCallback getCommitCallback() {
        return getSyncRepo().getCommitCallback();
    }

    public IRepoChunkAccessors.ITransaction getCurrentTransaction() {
        return getSyncRepo().getCurrentTransaction();
    }

    public MergeResult merge(IRepoChunkAccessors.ITransaction transaction, CommitBox commitBox)
            throws IOException, CryptoException {
        return getSyncRepo().merge(transaction, commitBox);
    }

    public ChunkStoreBranchLog getBranchLog() throws IOException {
        return getSyncRepo().getBranchLog();
    }

    public ChunkContainerRef commitInternal(String message, ICommitSignature commitSignature,
                               Collection<ChunkContainerRef> mergeParents) throws IOException, CryptoException {
        return getSyncRepo().commitInternal(message, commitSignature, mergeParents);
    }

    public ChunkContainerRef commitInternal(String message, ICommitSignature commitSignature)
            throws IOException, CryptoException {
        return getSyncRepo().commitInternal(message, commitSignature);
    }

    public HashValue commit(ICommitSignature commitSignature) throws IOException, CryptoException {
        return getSyncRepo().commit(commitSignature);
    }

    public CommitBox getHeadCommit() {
        return getSyncRepo().getHeadCommit();
    }

    public CommitCache getCommitCache() {
        return getSyncRepo().getCommitCache();
    }

    public File getDir() {
        return getSyncRepo().getDir();
    }

    public static ChunkContainerRef put(TypedBlob blob, IChunkAccessor accessor, ChunkContainerRef ref)
            throws IOException, CryptoException {
        return SyncRepository.put(blob, accessor, ref);
    }

    public IRepoChunkAccessors getAccessors() {
        return getSyncRepo().getAccessors();
    }
}
