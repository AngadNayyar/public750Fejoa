/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.chunkstore;

import org.fejoa.chunkstore.sync.PullRepoRequest;
import org.fejoa.chunkstore.sync.PullRequest;
import org.fejoa.chunkstore.sync.PushRequest;
import org.fejoa.chunkstore.sync.RequestHandler;
import org.fejoa.library.BranchAccessRight;
import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.remote.IRemotePipe;
import org.fejoa.library.support.StorageLib;

import java.io.*;
import java.util.HashMap;
import java.util.Map;


public class PullPushTest extends RepositoryTestBase {

    private ChunkStore createChunkStore(File directory, String name) throws IOException {
        assertTrue(!directory.getName().equals("") && !directory.getName().equals("."));
        cleanUpFiles.add(directory.getName());

        return ChunkStore.create(directory, name);
    }

    private IChunkAccessor getAccessor(final ChunkStore.Transaction transaction) {
        return new IChunkAccessor() {
            @Override
            public DataInputStream getChunk(ChunkPointer hash) throws IOException {
                return new DataInputStream(new ByteArrayInputStream(transaction.getChunk(hash.getBoxHash())));
            }

            @Override
            public PutResult<HashValue> putChunk(byte[] data, HashValue ivHash) throws IOException {
                return transaction.put(data);
            }

            @Override
            public void releaseChunk(HashValue data) {

            }
        };
    }

    private IRepoChunkAccessors getRepoChunkAccessors(final ChunkStore chunkStore) {
        return new IRepoChunkAccessors() {
            @Override
            public ITransaction startTransaction() throws IOException {
                return new RepoAccessorsTransactionBase(chunkStore) {
                    final IChunkAccessor accessor = getAccessor(transaction);
                    @Override
                    public ChunkStore.Transaction getRawAccessor() {
                        return transaction;
                    }

                    @Override
                    public IChunkAccessor getCommitAccessor(ChunkContainerRef ref) {
                        return accessor;
                    }

                    @Override
                    public IChunkAccessor getTreeAccessor(ChunkContainerRef ref) {
                        return accessor;
                    }

                    @Override
                    public IChunkAccessor getFileAccessor(ChunkContainerRef ref, String filePath) {
                        return accessor;
                    }
                };
            }
        };
    }

    private IRemotePipe connect(final RequestHandler handler) {
        return new IRemotePipe() {
            ByteArrayOutputStream outputStream;

            @Override
            public InputStream getInputStream() throws IOException {
                final ByteArrayOutputStream reply = new ByteArrayOutputStream();
                final ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
                handler.handle(new IRemotePipe() {
                    @Override
                    public InputStream getInputStream() throws IOException {
                        return inputStream;
                    }

                    @Override
                    public OutputStream getOutputStream() {
                        return reply;
                    }
                }, BranchAccessRight.ALL);
                return new ByteArrayInputStream(reply.toByteArray());
            }

            @Override
            public OutputStream getOutputStream() {
                outputStream = new ByteArrayOutputStream();
                return outputStream;
            }
        };
    }

    public void testPull() throws Exception {
        String branch = "pullBranch";
        File directory = new File("PullTest");
        cleanUpFiles.add(directory.getName());
        for (String dir : cleanUpFiles)
            StorageLib.recursiveDeleteFile(new File(dir));
        directory.mkdirs();

        ChunkStore requestChunkStore = createChunkStore(directory, "requestStore");
        Repository requestRepo = new Repository(directory, branch, getRepoChunkAccessors(requestChunkStore),
                simpleCommitCallback);
        ChunkStore remoteChunkStore = createChunkStore(directory, "remoteStore");
        IRepoChunkAccessors remoteAccessor = getRepoChunkAccessors(remoteChunkStore);
        final Repository remoteRepo = new Repository(directory, branch, remoteAccessor, simpleCommitCallback);

        final RequestHandler handler = new RequestHandler(remoteAccessor.startTransaction().getRawAccessor(),
                new RequestHandler.IBranchLogGetter() {
            @Override
            public ChunkStoreBranchLog get(String branch) throws IOException {
                return remoteRepo.getBranchLog();
            }
        });
        final IRemotePipe senderPipe = connect(handler);

        PullRequest pullRequest = new PullRequest(requestRepo, null);
        ChunkContainerRef pulledTip = pullRequest.pull(senderPipe, branch);

        assertTrue(pulledTip.getBoxHash().isZero());

        // change the remote repo
        Map<String, DatabaseStingEntry> remoteContent = new HashMap();
        add(remoteRepo, remoteContent, new DatabaseStingEntry("testFile", "Hello World"));
        ChunkContainerRef boxPointer = remoteRepo.commitInternal("", null);

        pulledTip = pullRequest.pull(senderPipe, branch);
        containsContent(requestRepo, remoteContent);
        assertTrue(pulledTip.getBoxHash().equals(boxPointer.getBoxHash()));
        assertEquals(pulledTip,
                simpleCommitCallback.commitPointerFromLog(requestRepo.getBranchLog().getLatest().getMessage()));

        // make another remote change
        add(remoteRepo, remoteContent, new DatabaseStingEntry("testFile2", "Hello World 2"));
        boxPointer = remoteRepo.commitInternal("", null);

        pulledTip = pullRequest.pull(senderPipe, branch);
        containsContent(requestRepo, remoteContent);
        assertTrue(pulledTip.getBoxHash().equals(boxPointer.getBoxHash()));
        assertEquals(pulledTip,
                simpleCommitCallback.commitPointerFromLog(requestRepo.getBranchLog().getLatest().getMessage()));
    }

    public void testPullRepo() throws Exception {
        String branch = "pullRepoBranch";
        File directory = new File("PullRepoTestSource");
        File directory2 = new File("PullRepoTestTarget");
        cleanUpFiles.add(directory.getName());
        cleanUpFiles.add(directory2.getName());
        for (String dir : cleanUpFiles)
            StorageLib.recursiveDeleteFile(new File(dir));
        directory.mkdirs();
        directory2.mkdirs();

        // test pull full repo
        IRepoChunkAccessors sourceAccessor = getRepoChunkAccessors(createChunkStore(directory, branch));
        final Repository sourceRepo = new Repository(directory, branch, sourceAccessor, simpleCommitCallback);
        IRepoChunkAccessors targetAccessor = getRepoChunkAccessors(createChunkStore(directory2, branch));
        Repository targetRepo = new Repository(directory2, branch, targetAccessor, simpleCommitCallback);

        // fill source repo
        Map<String, DatabaseStingEntry> sourceContent = new HashMap<>();
        add(sourceRepo, sourceContent, new DatabaseStingEntry("testFile", "Hello World"));
        add(sourceRepo, sourceContent, new DatabaseStingEntry("sub/testFile", "Hello World2"));
        add(sourceRepo, sourceContent, new DatabaseStingEntry("sub/testFile2", "Hello World3"));
        sourceRepo.commitInternal("", null);

        // pull repo to target
        final RequestHandler handler = new RequestHandler(sourceAccessor.startTransaction().getRawAccessor(),
                new RequestHandler.IBranchLogGetter() {
                    @Override
                    public ChunkStoreBranchLog get(String branch) throws IOException {
                        return sourceRepo.getBranchLog();
                    }
                });
        final IRemotePipe senderPipe = connect(handler);
        PullRepoRequest pullRepoRequest = new PullRepoRequest(targetRepo);
        pullRepoRequest.pull(senderPipe, branch);

        // verify
        sourceRepo.getBranchLog().getLatest().getMessage().equals(targetRepo.getBranchLog().getLatest().getMessage());
        targetRepo = new Repository(directory2, branch, targetAccessor, simpleCommitCallback);
        containsContent(targetRepo, sourceContent);
    }

    public void testPush() throws Exception {
        String branch = "pushBranch";
        File directory = new File("PushTest");
        final File remoteDirectory = new File("RemotePushTest");
        cleanUpFiles.add(directory.getName());
        cleanUpFiles.add(remoteDirectory.getName());
        for (String dir : cleanUpFiles)
            StorageLib.recursiveDeleteFile(new File(dir));
        directory.mkdirs();
        remoteDirectory.mkdirs();

        ChunkStore localChunkStore = createChunkStore(directory, "localStore");
        Repository localRepo = new Repository(directory, branch, getRepoChunkAccessors(localChunkStore),
                simpleCommitCallback);
        final ChunkStore remoteChunkStore = createChunkStore(remoteDirectory, "remoteStore");
        final IRepoChunkAccessors remoteAccessor = getRepoChunkAccessors(remoteChunkStore);

        final RequestHandler handler = new RequestHandler(remoteAccessor.startTransaction().getRawAccessor(),
                new RequestHandler.IBranchLogGetter() {
            @Override
            public ChunkStoreBranchLog get(String branch) throws IOException {
                Repository remoteRepo = null;
                try {
                    remoteRepo = new Repository(remoteDirectory, branch, remoteAccessor, simpleCommitCallback);
                } catch (CryptoException e) {
                    e.printStackTrace();
                }
                return remoteRepo.getBranchLog();
            }
        });
        final IRemotePipe senderPipe = connect(handler);

        // change the local repo
        Map<String, DatabaseStingEntry> localContent = new HashMap<>();
        add(localRepo, localContent, new DatabaseStingEntry("testFile", "Hello World!"));
        localRepo.commit(null);

        IRepoChunkAccessors.ITransaction localTransaction = localRepo.getCurrentTransaction();
        PushRequest pushRequest = new PushRequest(localRepo);
        pushRequest.push(senderPipe, localTransaction, branch);

        Repository remoteRepo = new Repository(remoteDirectory, branch, getRepoChunkAccessors(remoteChunkStore),
                simpleCommitCallback);
        containsContent(remoteRepo, localContent);

        // add more
        add(localRepo, localContent, new DatabaseStingEntry("testFile2", "Hello World 2"));
        add(localRepo, localContent, new DatabaseStingEntry("sub/testFile3", "Hello World 3"));
        add(localRepo, localContent, new DatabaseStingEntry("sub/sub2/testFile4", "Hello World 4"));
        localRepo.commit(null);
        // push changes
        pushRequest.push(senderPipe, localTransaction, branch);
        remoteRepo = new Repository(remoteDirectory, branch, getRepoChunkAccessors(remoteChunkStore),
                simpleCommitCallback);
        containsContent(remoteRepo, localContent);
    }
}

