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
import org.fejoa.chunkstore.BoxPointer;
import org.fejoa.chunkstore.HashValue;
import org.fejoa.chunkstore.Repository;
import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.crypto.CryptoHelper;
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
        BoxPointer boxPointer;
    }

    static class TestDirectory {
        Map<String, TestFile> files = new HashMap<>();
        Map<String, TestDirectory> dirs = new HashMap<>();
        BoxPointer boxPointer;
    }

    static class TestCommit {
        String message;
        TestDirectory directory;
        BoxPointer boxPointer;
    }

    protected class DatabaseStingEntry {
        public String path;
        public String content;

        public DatabaseStingEntry(String path, String content) {
            this.path = path;
            this.content = content;
        }
    }

    protected Repository.ICommitCallback simpleCommitCallback = new Repository.ICommitCallback() {
        static final String DATA_HASH_KEY = "dataHash";
        static final String BOX_HASH_KEY = "boxHash";
        static final String IV_KEY = "iv";

        @Override
        public HashValue logHash(BoxPointer commitPointer) {
            try {
                MessageDigest digest = CryptoHelper.sha256Hash();
                digest.update(commitPointer.getBoxHash().getBytes());
                digest.update(commitPointer.getIV());
                return new HashValue(digest.digest());
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException("Missing sha256");
            }
        }

        @Override
        public String commitPointerToLog(BoxPointer commitPointer) {
            JSONObject jsonObject = new JSONObject();
            try {
                jsonObject.put(DATA_HASH_KEY, commitPointer.getDataHash().toHex());
                jsonObject.put(BOX_HASH_KEY, commitPointer.getBoxHash().toHex());
                jsonObject.put(IV_KEY, Base64.encodeBase64String(commitPointer.getIV()));
            } catch (JSONException e) {
                e.printStackTrace();
            }
            String jsonString = jsonObject.toString();
            return Base64.encodeBase64String(jsonString.getBytes());
        }

        @Override
        public BoxPointer commitPointerFromLog(String logEntry) {
            String jsonString = new String(Base64.decodeBase64(logEntry));
            try {
                JSONObject jsonObject = new JSONObject(jsonString);
                return new BoxPointer(HashValue.fromHex(jsonObject.getString(DATA_HASH_KEY)),
                        HashValue.fromHex(jsonObject.getString(BOX_HASH_KEY)),
                        Base64.decodeBase64(jsonObject.getString(IV_KEY)));
            } catch (JSONException e) {
                e.printStackTrace();
            }
            return null;
        }
    };

    protected void add(Repository database, Map<String, DatabaseStingEntry> content, DatabaseStingEntry entry)
            throws Exception {
        content.put(entry.path, entry);
        database.writeBytes(entry.path, entry.content.getBytes());
    }

    protected void remove(Repository database, Map<String, DatabaseStingEntry> content, String path)
            throws Exception {
        if (content.containsKey(path)) {
            content.remove(path);
            database.remove(path);
        }
    }

    private int countFiles(Repository database, String dirPath) throws IOException {
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
