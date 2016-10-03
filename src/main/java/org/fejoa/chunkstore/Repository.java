/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.chunkstore;

import org.fejoa.chunkstore.sync.*;
import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.database.DatabaseDiff;
import org.fejoa.library.database.ICommitSignature;
import org.fejoa.library.database.IDatabaseInterface;

import java.io.*;
import java.util.*;


public class Repository implements IDatabaseInterface {
    final private File dir;
    final private String branch;
    final private ChunkStoreBranchLog log;
    private CommitBox headCommit;
    final private ICommitCallback commitCallback;
    final private IRepoChunkAccessors accessors;
    private LogRepoTransaction transaction;
    private TreeAccessor treeAccessor;
    final private CommitCache commitCache;
    final private ChunkSplitter chunkSplitter = new RabinSplitter();

    public interface ICommitCallback {
        String commitPointerToLog(BoxPointer commitPointer) throws CryptoException;
        BoxPointer commitPointerFromLog(String logEntry) throws CryptoException;
     }

    public Repository(File dir, String branch, IRepoChunkAccessors chunkAccessors, ICommitCallback commitCallback)
            throws IOException, CryptoException {
        this.dir = dir;
        this.branch = branch;
        this.accessors = chunkAccessors;
        this.transaction = new LogRepoTransaction(accessors.startTransaction());
        this.log = getLog(dir, branch);
        this.commitCallback = commitCallback;

        BoxPointer headCommitPointer = null;
        if (log.getLatest() != null)
            headCommitPointer = commitCallback.commitPointerFromLog(log.getLatest().getMessage());
        DirectoryBox root;
        if (headCommitPointer == null) {
            root = DirectoryBox.create();
        } else {
            headCommit = CommitBox.read(transaction.getCommitAccessor(), headCommitPointer);
            root = DirectoryBox.read(transaction.getTreeAccessor(), headCommit.getTree());
        }
        this.treeAccessor = new TreeAccessor(root, transaction);
        commitCache = new CommitCache(this);
    }

    static public ChunkSplitter defaultNodeSplitter(int targetChunkSize) {
        float kFactor = (32f) / (32 * 3 + 8);
        return new RabinSplitter((int)(kFactor * targetChunkSize),
                (int)(kFactor * RabinSplitter.CHUNK_1KB), (int)(kFactor * RabinSplitter.CHUNK_128KB * 5));
    }

    public CommitBox getHeadCommit() {
        return headCommit;
    }

    public String getBranch() {
        return branch;
    }

    static public ChunkStoreBranchLog getLog(File baseDir, String branch) throws IOException {
        return new ChunkStoreBranchLog(new File(getBranchDir(baseDir), branch));
    }

    public IRepoChunkAccessors.ITransaction getCurrentTransaction() {
        return transaction;
    }

    public IRepoChunkAccessors getAccessors() {
        return accessors;
    }

    @Override
    public HashValue getTip() throws IOException {
        if (getHeadCommit() == null)
            return Config.newDataHash();
        return getHeadCommit().dataHash();
    }

    private Collection<HashValue> getParents() {
        if (headCommit == null)
            return Collections.emptyList();
        List<HashValue> parents = new ArrayList<>();
        for (BoxPointer parent : headCommit.getParents())
            parents.add(parent.getDataHash());
        return parents;
    }

    public CommitCache getCommitCache() {
        return commitCache;
    }

    @Override
    public HashValue getHash(String path) throws IOException, CryptoException {
        DirectoryBox.Entry entry = treeAccessor.get(path);
        if (!entry.isFile())
            throw new IOException("Not a file path.");
        FileBox fileBox = FileBox.read(transaction.getFileAccessor(path), entry.getDataPointer());
        return fileBox.getDataContainer().hash();
    }

    static private File getBranchDir(File dir) {
        return new File(dir, "branches");
    }

    public ICommitCallback getCommitCallback() {
        return commitCallback;
    }

    private DirectoryBox getDirBox(String path) throws IOException {
        try {
            DirectoryBox.Entry entry = treeAccessor.get(path);
            if (entry == null)
                return null;
            assert !entry.isFile();
            if (entry.getObject() == null)
                entry.setObject(DirectoryBox.read(transaction.getTreeAccessor(), entry.getDataPointer()));

            return (DirectoryBox)entry.getObject();
        } catch (CryptoException e) {
            throw new IOException(e.getMessage());
        }
    }

    @Override
    public List<String> listFiles(String path) throws IOException {
        DirectoryBox directoryBox = getDirBox(path);
        if (directoryBox == null)
            return Collections.emptyList();
        List<String> entries = new ArrayList<>();
        for (DirectoryBox.Entry fileEntry : directoryBox.getFiles())
            entries.add(fileEntry.getName());
        return entries;
    }

    @Override
    public List<String> listDirectories(String path) throws IOException {
        DirectoryBox directoryBox = getDirBox(path);
        if (directoryBox == null)
            return Collections.emptyList();
        List<String> entries = new ArrayList<>();
        for (DirectoryBox.Entry dirEntry : directoryBox.getDirs())
            entries.add(dirEntry.getName());
        return entries;
    }

    @Override
    public byte[] readBytes(String path) throws IOException, CryptoException {
        return treeAccessor.read(path);
    }

    @Override
    public void writeBytes(String path, byte[] bytes) throws IOException, CryptoException {
        treeAccessor.put(path, writeToFileBox(path, bytes));
    }

    @Override
    public void remove(String path) throws IOException, CryptoException {
        treeAccessor.remove(path);
    }

    private FileBox writeToFileBox(String path, byte[] data) throws IOException {
        FileBox file = FileBox.create(transaction.getFileAccessor(path), defaultNodeSplitter(RabinSplitter.CHUNK_8KB));
        ChunkContainer chunkContainer = file.getDataContainer();
        ChunkContainerOutputStream containerOutputStream = new ChunkContainerOutputStream(chunkContainer,
                chunkSplitter);
        containerOutputStream.write(data);
        containerOutputStream.flush();
        return file;
    }

    static public BoxPointer put(TypedBlob blob, IChunkAccessor accessor) throws IOException, CryptoException {
        ChunkSplitter nodeSplitter = Repository.defaultNodeSplitter(RabinSplitter.CHUNK_8KB);
        ChunkContainer chunkContainer = new ChunkContainer(accessor, nodeSplitter);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        blob.write(new DataOutputStream(outputStream));
        chunkContainer.append(new DataChunk(outputStream.toByteArray()));
        chunkContainer.flush(false);

        return chunkContainer.getBoxPointer();
    }

    private void copyMissingCommits(CommitBox commitBox,
                                    IRepoChunkAccessors.ITransaction source,
                                    IRepoChunkAccessors.ITransaction target)
            throws IOException, CryptoException {
        // TODO: test
        // copy to their transaction
        ChunkStore.Transaction targetTransaction = target.getRawAccessor();
        if (targetTransaction.contains(commitBox.getBoxPointer().getBoxHash()))
            return;
        for (BoxPointer parent : commitBox.getParents()) {
            CommitBox parentCommit = CommitBox.read(source.getCommitAccessor(), parent);
            copyMissingCommits(parentCommit, source, target);
        }

        ChunkFetcher chunkFetcher = ChunkFetcher.createLocalFetcher(targetTransaction, source.getRawAccessor());
        chunkFetcher.enqueueGetCommitJob(target, commitBox.getBoxPointer());
        chunkFetcher.fetch();
    }

    public boolean merge(IRepoChunkAccessors.ITransaction otherTransaction, CommitBox otherBranch)
            throws IOException, CryptoException {
        // TODO: check if the transaction is valid, i.e. contains object compatible with otherBranch?
        // TODO: verify commits
        assert otherBranch != null;
        assert !treeAccessor.isModified();

        // 1) Find common ancestor
        // 2) Pull missing objects into the other transaction
        // 3) Merge head with otherBranch and commit the other transaction
        synchronized (Repository.this) {
            if (headCommit == null) {
                // we are empty just use the other branch
                otherTransaction.finishTransaction();
                headCommit = otherBranch;

                transaction.finishTransaction();
                transaction = new LogRepoTransaction(accessors.startTransaction());
                log.add(commitCallback.commitPointerToLog(headCommit.getBoxPointer()), transaction.getObjectsWritten());
                treeAccessor = new TreeAccessor(DirectoryBox.read(transaction.getTreeAccessor(), otherBranch.getTree()),
                        transaction);
                return false;
            }
            if (headCommit.dataHash().equals(otherBranch.dataHash()))
                return false;
            if (commitCache.isParent(headCommit.dataHash(), otherBranch.dataHash()))
                return false;

            CommonAncestorsFinder.Chains chains = CommonAncestorsFinder.find(transaction.getCommitAccessor(),
                    headCommit, otherTransaction.getCommitAccessor(), otherBranch);
            copyMissingCommits(headCommit, transaction, otherTransaction);

            CommonAncestorsFinder.SingleCommitChain shortestChain = chains.getShortestChain();
            if (shortestChain == null)
                throw new IOException("Branches don't have common ancestor.");
            if (shortestChain.getOldest().dataHash().equals(headCommit.dataHash())) {
                // no local commits: just use the remote head
                otherTransaction.finishTransaction();
                headCommit = otherBranch;

                transaction.finishTransaction();
                transaction = new LogRepoTransaction(accessors.startTransaction());
                log.add(commitCallback.commitPointerToLog(headCommit.getBoxPointer()), transaction.getObjectsWritten());
                treeAccessor = new TreeAccessor(DirectoryBox.read(transaction.getTreeAccessor(), otherBranch.getTree()),
                        transaction);
                return false;
            }

            // merge branches
            treeAccessor = ThreeWayMerge.merge(transaction, transaction, headCommit, otherTransaction,
                    otherBranch, shortestChain.getOldest(), ThreeWayMerge.ourSolver());
            return true;
        }
    }

    public HashValue commit(ICommitSignature commitSignature) throws IOException, CryptoException {
        return commit("Repo commit", commitSignature);
    }

    private boolean needCommit() {
        return treeAccessor.isModified();
    }

    @Override
    public HashValue commit(String message, ICommitSignature commitSignature) throws IOException, CryptoException {
        commitInternal(message, commitSignature);
        if (headCommit == null)
            return null;
        return headCommit.dataHash();
    }

    public BoxPointer commitInternal(String message, ICommitSignature commitSignature) throws IOException,
            CryptoException {
        return commitInternal(message, commitSignature, Collections.<BoxPointer>emptyList());
    }

    public BoxPointer commitInternal(String message, ICommitSignature commitSignature,
                                     Collection<BoxPointer> mergeParents) throws IOException,
            CryptoException {
        if (mergeParents.size() == 0 && !needCommit())
            return null;

        synchronized (Repository.this) {
            BoxPointer rootTree = treeAccessor.build();
            CommitBox commitBox = CommitBox.create();
            commitBox.setTree(rootTree);
            if (headCommit != null)
                commitBox.addParent(headCommit.getBoxPointer());
            for (BoxPointer mergeParent : mergeParents)
                commitBox.addParent(mergeParent);
            if (commitSignature != null)
                message = commitSignature.signMessage(message, rootTree.getDataHash(), getParents());
            commitBox.setCommitMessage(message.getBytes());
            BoxPointer commitPointer = put(commitBox, transaction.getCommitAccessor());
            commitBox.setBoxPointer(commitPointer);
            headCommit = commitBox;

            transaction.finishTransaction();
            log.add(commitCallback.commitPointerToLog(commitPointer), transaction.getObjectsWritten());

            transaction = new LogRepoTransaction(accessors.startTransaction());
            this.treeAccessor.setTransaction(transaction);

            return commitPointer;
        }
    }

    public ChunkStoreBranchLog getBranchLog() throws IOException {
        return log;
    }

    @Override
    public DatabaseDiff getDiff(HashValue baseCommitHash, HashValue endCommitHash) throws IOException, CryptoException {
        CommitBox baseCommit = commitCache.getCommit(baseCommitHash);
        CommitBox endCommit = commitCache.getCommit(endCommitHash);

        DatabaseDiff databaseDiff = new DatabaseDiff();

        IChunkAccessor treeAccessor = transaction.getTreeAccessor();
        TreeIterator diffIterator = new TreeIterator(treeAccessor, baseCommit, treeAccessor, endCommit);
        while (diffIterator.hasNext()) {
            DiffIterator.Change<DirectoryBox.Entry> change = diffIterator.next();
            switch (change.type) {
                case MODIFIED:
                    databaseDiff.modified.addPath(change.path);
                    break;

                case ADDED:
                    databaseDiff.added.addPath(change.path);
                    break;

                case REMOVED:
                    databaseDiff.removed.addPath(change.path);
                    break;
            }
        }

        return databaseDiff;
    }
}
