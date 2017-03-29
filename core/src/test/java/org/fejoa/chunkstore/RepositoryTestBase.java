/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.chunkstore;

import junit.framework.TestCase;
import org.apache.commons.codec.binary.Base64;
import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.crypto.CryptoHelper;
import org.fejoa.library.database.CSRepositoryBuilder;
import org.fejoa.library.support.StorageLib;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;


public class RepositoryTestBase extends TestCase {
    final protected List<String> cleanUpFiles = new ArrayList<String>();

    @Override
    public void tearDown() throws Exception {
        super.tearDown();

        for (String dir : cleanUpFiles)
            StorageLib.recursiveDeleteFile(new File(dir));
    }

    static class TestFile {
        TestFile(String content) {
            this.content = content;
        }

        String content;
        ChunkContainerRef boxPointer;
    }

    static class TestDirectory {
        Map<String, TestFile> files = new HashMap<>();
        Map<String, TestDirectory> dirs = new HashMap<>();
        ChunkContainerRef boxPointer;
    }

    static class TestCommit {
        String message;
        TestDirectory directory;
        ChunkContainerRef boxPointer;
    }

    protected class DatabaseStingEntry {
        public String path;
        public String content;

        public DatabaseStingEntry(String path, String content) {
            this.path = path;
            this.content = content;
        }
    }

    protected ICommitCallback simpleCommitCallback = CSRepositoryBuilder.getSimpleCommitCallback();

    protected void add(Repository database, Map<String, DatabaseStingEntry> content, DatabaseStingEntry entry)
            throws Exception {
        content.put(entry.path, entry);
        database.putBytes(entry.path, entry.content.getBytes());
    }

    protected void remove(Repository database, Map<String, DatabaseStingEntry> content, String path)
            throws Exception {
        if (content.containsKey(path)) {
            content.remove(path);
            database.remove(path);
        }
    }

    private int countFiles(Repository database, String dirPath) throws IOException, CryptoException {
        int fileCount = database.listFiles(dirPath).size();
        for (String dir : database.listDirectories(dirPath))
            fileCount += countFiles(database, StorageLib.appendDir(dirPath, dir));
        return fileCount;
    }

    protected void containsContent(Repository database, Map<String, DatabaseStingEntry> content) throws IOException,
            CryptoException {
        for (DatabaseStingEntry entry : content.values()) {
            byte bytes[] = database.readBytes(entry.path);
            assertNotNull(bytes);
            assertTrue(entry.content.equals(new String(bytes)));
        }
        assertEquals(content.size(), countFiles(database, ""));
    }
}
