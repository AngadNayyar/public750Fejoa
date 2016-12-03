/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.filestorage;

import org.fejoa.chunkstore.Config;
import org.fejoa.chunkstore.HashValue;
import org.fejoa.chunkstore.Repository;
import org.fejoa.library.FejoaContext;
import org.fejoa.library.database.StorageDir;
import org.fejoa.library.crypto.CryptoException;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.util.Collection;


public class Index {
    static public class Entry {
        private HashValue hash;
        private long lastModified;
        private long length;

        final static String HASH_KEY = "hash";
        final static String MODIFICATION_TIME_KEY = "mTime";
        final static String LENGTH_KEY = "length";

        public Entry(HashValue hash, long lastModified, long length) {
            this.hash = hash;
            this.lastModified = lastModified;
            this.length = length;
        }

        public Entry(HashValue hash, File file) {
            this.hash = hash;
            this.lastModified = file.lastModified();
            this.length = file.length();
        }

        private Entry() {
            this.hash = Config.newBoxHash();
        }

        static public Entry open(String bundle) throws JSONException {
            Entry entry = new Entry();
            entry.fromJson(bundle);
            return entry;
        }

        public String toJson() throws JSONException {
            JSONObject bundle = new JSONObject();
            bundle.put(HASH_KEY, hash.toHex());
            bundle.put(MODIFICATION_TIME_KEY, lastModified);
            bundle.put(LENGTH_KEY, length);
            return bundle.toString();
        }

        public void fromJson(String data) throws JSONException {
            JSONObject bundle = new JSONObject(data);
            this.hash = HashValue.fromHex(bundle.getString(HASH_KEY));
            this.lastModified = bundle.getLong(MODIFICATION_TIME_KEY);
            this.length = bundle.getLong(LENGTH_KEY);
        }

        public HashValue getHash() {
            return hash;
        }

        public long getLastModified() {
            return lastModified;
        }

        public long getLength() {
            return length;
        }
    }

    final private File indexDir;
    final private StorageDir storageDir;

    public Index(FejoaContext context, File indexDir, String branch) throws IOException, CryptoException {
        this.indexDir = indexDir;
        indexDir.mkdirs();
        this.storageDir = context.getPlainStorage(indexDir, branch);
    }

    public void update(String filePath, Entry entry) throws IOException, JSONException {
        String bundle = entry.toJson();
        storageDir.writeString(filePath, bundle);
    }

    public Entry get(String filePath) throws JSONException {
        String bundle;
        try {
            bundle = storageDir.readString(filePath);
        } catch (IOException e) {
            return null;
        }
        return Entry.open(bundle);
    }

    public void remove(String filePath) throws IOException, CryptoException {
        storageDir.remove(filePath);
    }

    public Collection<String> listFiles(String dir) throws IOException, CryptoException {
        return storageDir.listFiles(dir);
    }

    public Collection<String> listDirectories(String dir) throws IOException, CryptoException {
        return storageDir.listDirectories(dir);
    }

    private void setRev(HashValue rev) throws IOException {
        RandomAccessFile randomAccessFile = new RandomAccessFile(new File(indexDir, "rev"), "rw");
        randomAccessFile.setLength(0);
        randomAccessFile.write(rev.toHex().getBytes());
        randomAccessFile.close();
    }

    public HashValue getRev() throws IOException {
        RandomAccessFile randomAccessFile;
        try {
            randomAccessFile = new RandomAccessFile(new File(indexDir, "rev"), "r");
            String revHex = randomAccessFile.readLine();
            randomAccessFile.close();
            return HashValue.fromHex(revHex);
        } catch (FileNotFoundException e) {
            return Config.newDataHash();
        }
    }

    public void commit(HashValue currentRev) throws IOException {
        this.storageDir.commit();
        setRev(currentRev);
    }
}
