/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.chunkstore;

import java8.util.concurrent.CompletableFuture;
import java8.util.function.BiConsumer;
import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.database.StorageDir;
import org.fejoa.library.support.StreamHelper;

import java.io.*;
import java.nio.file.NoSuchFileException;
import java.util.*;
import java.util.concurrent.ExecutionException;


public class RepositoryTest extends RepositoryTestBase {
    private IChunkAccessor getAccessor(final ChunkStore.Transaction chunkStoreTransaction) {
        return new IChunkAccessor() {
            @Override
            public DataInputStream getChunk(ChunkPointer hash) throws IOException {
                return new DataInputStream(new ByteArrayInputStream(chunkStoreTransaction.getChunk(hash.getBoxHash())));
            }

            @Override
            public PutResult<HashValue> putChunk(byte[] data, HashValue ivHash) throws IOException {
                return chunkStoreTransaction.put(data);
            }

            @Override
            public void releaseChunk(HashValue data) {

            }
        };
    }

    protected IRepoChunkAccessors getRepoChunkAccessors(final ChunkStore chunkStore) {
        return new IRepoChunkAccessors() {
            @Override
            public ITransaction startTransaction() throws IOException {
                return new RepoAccessorsTransactionBase(chunkStore) {
                    @Override
                    public ChunkStore.Transaction getRawAccessor() {
                        return transaction;
                    }

                    @Override
                    public IChunkAccessor getCommitAccessor(ChunkContainerRef ref) {
                        return getAccessor(transaction);
                    }

                    @Override
                    public IChunkAccessor getTreeAccessor(ChunkContainerRef ref) {
                        return getAccessor(transaction);
                    }

                    @Override
                    public IChunkAccessor getFileAccessor(ChunkContainerRef ref, String filePath) {
                        return getAccessor(transaction);
                    }
                };
            }
        };
    }

    protected ChunkStore createChunkStore(File directory, String name) throws IOException {
        assertTrue(!directory.getName().equals("") && !directory.getName().equals("."));
        cleanUpFiles.add(directory.getName());

        return ChunkStore.create(directory, name);
    }

    private ChunkContainerRef write(IChunkAccessor accessor, TypedBlob blob) throws IOException, CryptoException {
        ChunkContainerRef ref = new ChunkContainerRef();
        ChunkContainer chunkContainer = new ChunkContainer(accessor, ref);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        HashValue dataHash = blob.write(new DataOutputStream(outputStream), ref);
        chunkContainer.append(new DataChunk(outputStream.toByteArray()));
        chunkContainer.flush(false);
        ref.setDataHash(dataHash);
        return ref;
    }

    private TestCommit writeToRepository(IRepoChunkAccessors.ITransaction accessors, TestDirectory root,
                                         String commitMessage)
            throws IOException, CryptoException {
        ChunkContainerRef tree = writeDir(accessors, root, "");
        CommitBox commitBox = CommitBox.create();
        commitBox.setTree(tree);
        commitBox.setCommitMessage(commitMessage.getBytes());

        TestCommit testCommit = new TestCommit();
        testCommit.message = commitMessage;
        testCommit.directory = root;
        testCommit.boxPointer = write(accessors.getCommitAccessor(commitBox.getRef()), commitBox);

        return testCommit;
    }

    private FileBox writeToFileBox(IChunkAccessor accessor, ChunkContainerRef ref, String content)
            throws IOException, CryptoException {
        ChunkContainer chunkContainer = new ChunkContainer(accessor, ref);
        FileBox file = FileBox.create(chunkContainer);
        ChunkContainerOutputStream containerOutputStream = new ChunkContainerOutputStream(chunkContainer);
        containerOutputStream.write(content.getBytes());
        containerOutputStream.flush();
        chunkContainer.flush(false);
        return file;
    }

    private ChunkContainerRef writeDir(IRepoChunkAccessors.ITransaction accessors, TestDirectory dir, String path)
            throws IOException,
            CryptoException {
        FlatDirectoryBox directoryBox = FlatDirectoryBox.create();
        // first write child dirs recursively
        for (Map.Entry<String, TestDirectory> entry : dir.dirs.entrySet()) {
            ChunkContainerRef childPointer = writeDir(accessors, entry.getValue(), path + "/" + entry.getKey());
            directoryBox.addDir(entry.getKey(), childPointer);
        }

        for (Map.Entry<String, TestFile> entry : dir.files.entrySet()) {
            TestFile testFile = entry.getValue();
            ChunkContainerRef ref = testFile.boxPointer;
            if (ref == null)
                ref = new ChunkContainerRef();
            IChunkAccessor fileAccessor = accessors.getFileAccessor(ref,path + "/" + entry.getKey());
            FileBox file = writeToFileBox(fileAccessor, ref, testFile.content);
            testFile.boxPointer = file.getRef();
            directoryBox.addFile(entry.getKey(), testFile.boxPointer);
        }

        dir.boxPointer = write(accessors.getTreeAccessor(directoryBox.getRef()), directoryBox);
        return dir.boxPointer;
    }

    private void verifyCommitInRepository(IRepoChunkAccessors.ITransaction accessors, TestCommit testCommit)
            throws IOException,
            CryptoException {
        CommitBox commitBox = CommitBox.read(accessors.getCommitAccessor(testCommit.boxPointer), testCommit.boxPointer);
        assertEquals(testCommit.message, new String(commitBox.getCommitMessage()));
        assertEquals(testCommit.directory.boxPointer.getBox().getBoxHash(), commitBox.getTree().getBox().getBoxHash());

        verifyDirInRepository(accessors, testCommit.directory, "");
    }

    private void verifyDirInRepository(IRepoChunkAccessors.ITransaction accessors, TestDirectory testDir, String path)
            throws IOException, CryptoException {
        FlatDirectoryBox directoryBox = FlatDirectoryBox.read(accessors.getTreeAccessor(testDir.boxPointer),
                testDir.boxPointer);
        assertEquals(testDir.dirs.size() + testDir.files.size(), directoryBox.getEntries().size());

        for (Map.Entry<String, TestDirectory> entry : testDir.dirs.entrySet()) {
            FlatDirectoryBox.Entry dirEntry = directoryBox.getEntry(entry.getKey());
            assertNotNull(dirEntry);
            assertEquals(entry.getValue().boxPointer.getBox().getBoxHash(),
                    dirEntry.getDataPointer().getBox().getBoxHash());

            verifyDirInRepository(accessors, entry.getValue(), path + "/" + entry.getKey());
        }
        for (Map.Entry<String, TestFile> entry : testDir.files.entrySet()) {
            TestFile testFile = entry.getValue();
            FlatDirectoryBox.Entry dirEntry = directoryBox.getEntry(entry.getKey());
            assertNotNull(dirEntry);
            assertEquals(testFile.boxPointer.getBox().getBoxHash(), dirEntry.getDataPointer().getBox().getBoxHash());

            verifyFileInRepository(accessors.getFileAccessor(testFile.boxPointer, path + "/" + entry.getKey()),
                    testFile);
        }
    }

    private void verifyFileInRepository(IChunkAccessor accessor, TestFile testFile) throws IOException, CryptoException {
        FileBox fileBox = FileBox.read(accessor, testFile.boxPointer);
        ChunkContainerInputStream inputStream = new ChunkContainerInputStream(fileBox.getDataContainer());
        assertEquals(testFile.content, new String(StreamHelper.readAll(inputStream)));
    }

    public void testBasics() throws IOException, CryptoException {
        String name = "repoTest";
        File directory = new File("RepoTest");
        cleanUpFiles.add(name);
        directory.mkdirs();

        ChunkStore chunkStore = createChunkStore(directory, name);
        IRepoChunkAccessors accessors = getRepoChunkAccessors(chunkStore);
        IRepoChunkAccessors.ITransaction transaction = accessors.startTransaction();

        TestFile testFile1 = new TestFile("file1Content");
        TestFile testFile2 = new TestFile("file2Content");
        TestFile testFile3 = new TestFile("file3Content");
        TestFile testFile4 = new TestFile("file4Content");

        TestDirectory sub1 = new TestDirectory();
        sub1.files.put("file3", testFile3);

        TestDirectory sub2 = new TestDirectory();
        sub2.files.put("file4", testFile4);

        TestDirectory root = new TestDirectory();
        root.files.put("file1", testFile1);
        root.files.put("file2", testFile2);

        root.dirs.put("sub1", sub1);
        root.dirs.put("sub2", sub2);

        ChunkStoreBranchLog branchLog = new ChunkStoreBranchLog(new File(name, "branch.log"));
        TestCommit testCommit = writeToRepository(transaction, root, "Commit Message");
        branchLog.add(testCommit.boxPointer.getBoxHash(), testCommit.boxPointer.getBoxHash().toHex(),
                Collections.<HashValue>emptyList());
        transaction.finishTransaction();

        branchLog = new ChunkStoreBranchLog(new File(name, "branch.log"));
        ChunkStoreBranchLog.Entry tip = branchLog.getLatest();
        assertNotNull(tip);
        assertEquals(testCommit.boxPointer.getBoxHash(), HashValue.fromHex(tip.getMessage()));

        transaction = accessors.startTransaction();
        verifyCommitInRepository(transaction, testCommit);
    }

    public void testRepositoryBasics() throws Exception {
        String branch = "repoBranch";
        String name = "repoTreeBuilder";
        File directory = new File("RepoTest");
        cleanUpFiles.add(directory.getName());
        directory.mkdirs();

        ChunkStore chunkStore = createChunkStore(directory, name);
        IRepoChunkAccessors accessors = getRepoChunkAccessors(chunkStore);
        Repository repository = new Repository(directory, branch, accessors, simpleCommitCallback);

        Map<String, DatabaseStingEntry> content = new HashMap<>();
        add(repository, content, new DatabaseStingEntry("file1", "file1"));
        add(repository, content, new DatabaseStingEntry("dir1/file2", "file2"));
        add(repository, content, new DatabaseStingEntry("dir1/file3", "file3"));
        add(repository, content, new DatabaseStingEntry("dir2/file4", "file4"));
        add(repository, content, new DatabaseStingEntry("dir1/sub1/file5", "file5"));
        add(repository, content, new DatabaseStingEntry("dir1/sub1/sub2/file6", "file6"));

        repository.commit(null);
        HashValue tip = repository.getTip();
        assertEquals(tip, repository.getHeadCommit().getPlainHash());

        repository = new Repository(directory, branch, accessors, simpleCommitCallback);
        containsContent(repository, content);

        // test add to existing dir
        add(repository, content, new DatabaseStingEntry("dir1/file6", "file6"));
        repository.commit(null);
        repository = new Repository(directory, branch, accessors, simpleCommitCallback);
        containsContent(repository, content);

        // test update
        add(repository, content, new DatabaseStingEntry("dir1/file3", "file3Update"));
        add(repository, content, new DatabaseStingEntry("dir1/sub1/file5", "file5Update"));
        add(repository, content, new DatabaseStingEntry("dir1/sub1/sub2/file6", "file6Update"));
        repository.commit(null);
        repository = new Repository(directory, branch, accessors, simpleCommitCallback);
        containsContent(repository, content);

        // test remove
        remove(repository, content, "dir1/sub1/file5");
        repository.commit(null);
        repository = new Repository(directory, branch, accessors, simpleCommitCallback);
        containsContent(repository, content);

        assertEquals(0, repository.listFiles("notThere").size());
        assertEquals(0, repository.listDirectories("notThere").size());
        assertEquals(0, repository.listFiles("file1").size());
        assertEquals(0, repository.listDirectories("file1").size());
        assertEquals(0, repository.listDirectories("dir1/file2").size());
    }

    public void testRepositoryAsync() throws IOException, CryptoException, InterruptedException, ExecutionException {
        String branch = "repoBranch";
        String name = "repoTreeBuilder";
        File directory = new File("RepoTestAsync");
        cleanUpFiles.add(directory.getName());
        directory.mkdirs();

        ChunkStore chunkStore = createChunkStore(directory, name);
        IRepoChunkAccessors accessors = getRepoChunkAccessors(chunkStore);
        Repository repository = new Repository(directory, branch, accessors, simpleCommitCallback);

        final StorageDir storageDir = new StorageDir(repository, "", null);
        System.out.println("1) Before put call");
        CompletableFuture<Void> putReadJob = storageDir.putStringAsync("test", "content").whenCompleteAsync(
                new BiConsumer<Void, Throwable>() {
            @Override
            public void accept(Void aVoid, Throwable throwable) {
                System.out.println("3) put done");
                storageDir.readStringAsync("test").whenComplete(new BiConsumer<String, Throwable>() {
                    @Override
                    public void accept(String s, Throwable throwable) {
                        assertEquals("content", s);
                        System.out.println("Read: " + s);
                    }
                });
            }
        });
        System.out.println("2) After put call");

        try {
            storageDir.readBytesAsync("invalidPath").get();
            assertTrue(false);
        } catch (ExecutionException e) {
            assertTrue(e.getCause() instanceof NoSuchFileException);
        }

        putReadJob.get();
    }
}
