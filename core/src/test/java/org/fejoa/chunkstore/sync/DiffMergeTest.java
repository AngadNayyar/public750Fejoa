/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.chunkstore.sync;

import org.fejoa.chunkstore.*;
import org.fejoa.library.crypto.Crypto;
import org.fejoa.library.crypto.CryptoHelper;
import org.fejoa.library.support.StorageLib;

import java.io.File;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;


public class DiffMergeTest extends RepositoryTest {
    MessageDigest messageDigest;

    public DiffMergeTest() throws NoSuchAlgorithmException {
         messageDigest = CryptoHelper.sha256Hash();
    }

    private ChunkContainerRef addFile(FlatDirectoryBox box, String name) {
        HashValue dataHash = new HashValue(CryptoHelper.sha256Hash(Crypto.get().generateSalt()));
        ChunkPointer fakeBox = new ChunkPointer(dataHash,
                new HashValue(CryptoHelper.sha256Hash(Crypto.get().generateSalt())), dataHash);

        ChunkContainerRef fakeFilePointer = new ChunkContainerRef();
        fakeFilePointer.setDataHash(fakeBox.getDataHash());
        fakeFilePointer.setIV(fakeBox.getIV());
        fakeFilePointer.setBoxHash(fakeBox.getBoxHash());

        box.addFile(name, fakeFilePointer);
        return fakeFilePointer;
    }

    public void testDiff() {
        FlatDirectoryBox ours = FlatDirectoryBox.create();
        FlatDirectoryBox theirs = FlatDirectoryBox.create();

        ChunkContainerRef file1 = addFile(ours, "test1");
        DirBoxDiffIterator iterator = new DirBoxDiffIterator("", ours, theirs);
        assertTrue(iterator.hasNext());
        DiffIterator.Change change = iterator.next();
        assertEquals(DiffIterator.Type.REMOVED, change.type);
        assertEquals("test1", change.path);
        assertFalse(iterator.hasNext());
        theirs.addFile("test1", file1);

        iterator = new DirBoxDiffIterator("", ours, theirs);
        assertFalse(iterator.hasNext());

        ChunkContainerRef file2 = addFile(theirs, "test2");
        iterator = new DirBoxDiffIterator("", ours, theirs);
        assertTrue(iterator.hasNext());
        change = iterator.next();
        assertEquals(DiffIterator.Type.ADDED, change.type);
        assertEquals("test2", change.path);
        assertFalse(iterator.hasNext());
        ours.addFile("test2", file2);

        ChunkContainerRef file3 = addFile(ours, "test3");
        theirs.addFile("test3", file3);
        ChunkContainerRef file4 = addFile(ours, "test4");
        theirs.addFile("test4", file4);
        ChunkContainerRef file5 = addFile(ours, "test5");
        theirs.addFile("test5", file5);

        ChunkContainerRef file31 = addFile(ours, "test31");
        iterator = new DirBoxDiffIterator("", ours, theirs);
        assertTrue(iterator.hasNext());
        change = iterator.next();
        assertEquals(DiffIterator.Type.REMOVED, change.type);
        assertEquals("test31", change.path);
        assertFalse(iterator.hasNext());

        theirs.addFile("test31", file31);
        ChunkContainerRef file41 = addFile(theirs, "test41");
        iterator = new DirBoxDiffIterator("", ours, theirs);
        assertTrue(iterator.hasNext());
        change = iterator.next();
        assertEquals(DiffIterator.Type.ADDED, change.type);
        assertEquals("test41", change.path);
        assertFalse(iterator.hasNext());

        addFile(ours, "test41");
        iterator = new DirBoxDiffIterator("", ours, theirs);
        assertTrue(iterator.hasNext());
        change = iterator.next();
        assertEquals(DiffIterator.Type.MODIFIED, change.type);
        assertEquals("test41", change.path);
        assertFalse(iterator.hasNext());
    }

    public void testMerge() throws Exception {
        String branch = "repoBranch";
        String name = "repoTreeBuilder";
        File directory = new File("RepoTest");
        File directory2 = new File("RepoTest2");
        cleanUpFiles.add(directory.getName());
        cleanUpFiles.add(directory2.getName());
        for (String dir : cleanUpFiles)
            StorageLib.recursiveDeleteFile(new File(dir));
        directory.mkdirs();
        directory2.mkdirs();

        ChunkStore chunkStore = createChunkStore(directory, name);
        IRepoChunkAccessors accessors = getRepoChunkAccessors(chunkStore);
        Repository repository = new Repository(directory, branch, accessors, simpleCommitCallback);
        Repository repository2 = new Repository(directory2, branch, accessors, simpleCommitCallback);

        repository.putBytes("file1", "file1".getBytes());
        repository.commit(null);

        repository2.putBytes("file1", "file1".getBytes());
        repository2.commit(null);

        repository2.putBytes("file2", "file2".getBytes());
        repository2.commit(null);

        Map<String, DatabaseStingEntry> mergedContent = new HashMap<>();
        mergedContent.put("file1", new DatabaseStingEntry("file1", "file1"));
        mergedContent.put("file2", new DatabaseStingEntry("file2", "file2"));

        IRepoChunkAccessors.ITransaction transaction = accessors.startTransaction();

        // test common ancestor finder
        CommitBox ours = repository.getHeadCommit();
        CommitBox theirs = repository2.getHeadCommit();
        CommonAncestorsFinder.Chains chains = CommonAncestorsFinder.find(transaction, ours, transaction, theirs);
        assertTrue(chains.chains.size() == 1);
        CommonAncestorsFinder.SingleCommitChain chain = chains.chains.get(0);
        assertTrue(chain.commits.size() == 2);
        CommitBox parent = chain.commits.get(chain.commits.size() - 1);
        assertTrue(parent.getPlainHash().equals(repository.getHeadCommit().getPlainHash()));

        repository.merge(transaction, theirs);
        repository.commit("merge1", null);
        containsContent(repository, mergedContent);

        repository.putBytes("file2", "our file 2".getBytes());
        repository.commit(null);
        repository2.putBytes("file2", "their file 2".getBytes());
        repository2.commit(null);

        theirs = repository2.getHeadCommit();
        repository.merge(transaction, theirs);
        repository.commit("merge2", null);

        mergedContent.clear();
        mergedContent.put("file1", new DatabaseStingEntry("file1", "file1"));
        mergedContent.put("file2", new DatabaseStingEntry("file2", "our file 2"));
        containsContent(repository, mergedContent);
    }
}
