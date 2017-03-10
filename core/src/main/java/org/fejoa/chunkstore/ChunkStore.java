/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.chunkstore;

import org.fejoa.library.crypto.CryptoHelper;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;


public class ChunkStore {
    /**
     * TODO: make the transaction actually do something, i.e. make a transaction atomic
     */
    public class Transaction {
        public long size() {
            return ChunkStore.this.size();
        }

        public ChunkStoreIterator iterator() throws IOException {
            return ChunkStore.this.iterator();
        }

        public byte[] getChunk(HashValue hash) throws IOException {
            return ChunkStore.this.getChunk(hash);
        }

        public PutResult<HashValue> put(byte[] data) throws IOException {
            return ChunkStore.this.put(data);
        }

        public boolean contains(HashValue hash) throws IOException {
            return ChunkStore.this.hasChunk(hash);
        }

        public void commit() throws IOException {
            currentTransaction = null;
        }

        public void cancel() {
            // TODO implement
        }
    }

    static class LockBucket {
        private Map<String, WeakReference<Lock>> lockMap = new HashMap<>();

        public Lock getLock(String id) {
            WeakReference<Lock> weakObject = lockMap.get(id);
            if (weakObject != null) {
                Lock lock = weakObject.get();
                if (lock != null)
                    return lock;
            }

            // create new lock
            Lock lock = new ReentrantLock();
            lockMap.put(id, new WeakReference<>(lock));
            return lock;
        }
    }

    final static protected LockBucket lockBucket = new LockBucket();
    final private BPlusTree tree;
    final private PackFile packFile;
    final private Lock fileLock;
    private Transaction currentTransaction;

    protected ChunkStore(File dir, String name) throws FileNotFoundException {
        this.tree = new BPlusTree(new RandomAccessFile(new File(dir, name + ".idx"), "rw"));
        this.packFile = new PackFile(new RandomAccessFile(new File(dir, name + ".pack"), "rw"));
        this.fileLock = lockBucket.getLock(dir + "/" + name);
    }

    static public ChunkStore create(File dir, String name) throws IOException {
        ChunkStore chunkStore = new ChunkStore(dir, name);
        chunkStore.tree.create(hashSize(), 1024);
        chunkStore.packFile.create(hashSize());
        return chunkStore;
    }

    static public ChunkStore open(File dir, String name) throws IOException {
        ChunkStore chunkStore = new ChunkStore(dir, name);
        chunkStore.tree.open();
        chunkStore.packFile.open();
        return chunkStore;
    }

    static public boolean exists(File dir, String name) {
        return new File(dir, name + ".idx").exists();
    }

    public byte[] getChunk(HashValue hash) throws IOException {
        return getChunk(hash.getBytes());
    }

    public byte[] getChunk(byte[] hash) throws IOException {
        try {
            lock();
            Long position = tree.get(hash);
            if (position == null)
                return null;
            return packFile.get(position.intValue(), hash);
        } finally {
            unlock();
        }
    }

    public long size() {
        try {
            lock();
            return tree.size();
        } finally {
            unlock();
        }
    }

    static public class Entry {
        final public HashValue key;
        final public byte[] data;

        public Entry(HashValue key, byte[] data) {
            this.key = key;
            this.data = data;
        }
    }

    public class ChunkStoreIterator implements Iterator<Entry> {
        final private Iterator<BPlusTree.Entry<Long>> iterator;

        ChunkStoreIterator(Iterator<BPlusTree.Entry<Long>> iterator) {
            this.iterator = iterator;

            lock();
        }

        public void unlock() {
            ChunkStore.this.unlock();
        }

        @Override
        protected void finalize() throws Throwable {
            unlock();
        }

        @Override
        public boolean hasNext() {
            return iterator.hasNext();
        }

        @Override
        public void remove() {

        }

        @Override
        public Entry next() {
            BPlusTree.Entry<Long> next = iterator.next();
            Long position = next.data;
            byte[] chunk;
            try {
                chunk = packFile.get(position.intValue(), next.key);
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
            return new Entry(new HashValue(next.key), chunk);
        }
    }

    public ChunkStoreIterator iterator() throws IOException {
        return new ChunkStoreIterator(tree.iterator());
    }

    public boolean hasChunk(HashValue hashValue) throws IOException {
        try {
            lock();
            return tree.get(hashValue.getBytes()) != null;
        } finally {
            unlock();
        }
    }

    public Transaction openTransaction() throws IOException {
        synchronized (this) {
            if (currentTransaction != null)
                return currentTransaction;
            currentTransaction = new Transaction();
            return currentTransaction;
        }
    }

    private PutResult<HashValue> put(byte[] data) throws IOException {
        try {
            lock();
            // make this configurable
            HashValue hash = new HashValue(CryptoHelper.sha3_256Hash(data));
            // TODO make it more efficient by only using one lookup
            if (tree.get(hash.getBytes()) != null)
                return new PutResult<>(hash, true);
            long position = packFile.put(hash, data);
            boolean wasInDatabase = !tree.put(hash, position);
            PutResult<HashValue> putResult = new PutResult<>(hash, wasInDatabase);
            return putResult;
        } finally {
            unlock();
        }
    }

    private void lock() {
        fileLock.lock();
    }

    private void unlock() {
        fileLock.unlock();
    }

    static private int hashSize() {
        return 32;
    }

}
