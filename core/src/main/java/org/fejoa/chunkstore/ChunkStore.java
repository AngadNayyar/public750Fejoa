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


interface IChunkStoreEngine {
    void create(File dir, String name) throws IOException;
    void open(File dir, String name) throws IOException;

    long size();
    ChunkStore.IChunkStoreIterator iterator() throws IOException;
    byte[] getChunk(byte[] hash) throws IOException;
    PutResult<HashValue> put(byte[] data) throws IOException;
    boolean contains(byte[] hash) throws IOException;
    void commit() throws IOException;
    void cancel();
}

class SimpleChunkStoreEngine implements IChunkStoreEngine {
    public class ChunkStoreIterator implements ChunkStore.IChunkStoreIterator {
        final private Iterator<BPlusTree.Entry<Long>> iterator;

        ChunkStoreIterator(Iterator<BPlusTree.Entry<Long>> iterator) {
            this.iterator = iterator;

            lock();
        }

        public void unlock() {
            SimpleChunkStoreEngine.this.unlock();
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
        public ChunkStore.Entry next() {
            BPlusTree.Entry<Long> next = iterator.next();
            Long position = next.data;
            byte[] chunk;
            try {
                chunk = packFile.get(position.intValue(), next.key);
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
            return new ChunkStore.Entry(new HashValue(next.key), chunk);
        }
    }

    final private BPlusTree tree;
    final private PackFile packFile;
    final private Lock lock;

    public SimpleChunkStoreEngine(BPlusTree tree, PackFile packFile, Lock lock) {
        this.tree = tree;
        this.packFile = packFile;
        this.lock = lock;
    }

    private void lock() {
        lock.lock();
    }

    private void unlock() {
        lock.unlock();
    }

    @Override
    public void create(File dir, String name) throws IOException {
        try {
            lock();
            tree.create(ChunkStore.hashSize(), 1024);
            packFile.create(ChunkStore.hashSize());
        } finally {
            unlock();
        }
    }

    @Override
    public void open(File dir, String name) throws IOException {
        try {
            lock();
            tree.open();
            packFile.open();
        } finally {
            unlock();
        }
    }

    @Override
    public long size() {
        try {
            lock();
            return tree.size();
        } finally {
            unlock();
        }
    }

    @Override
    public ChunkStore.IChunkStoreIterator iterator() throws IOException {
        return new ChunkStoreIterator(tree.iterator());
    }

    @Override
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

    @Override
    public PutResult<HashValue> put(byte[] data) throws IOException {
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

    @Override
    public boolean contains(byte[] hash) throws IOException {
        try {
            lock();
            return tree.get(hash) != null;
        } finally {
            unlock();
        }
    }

    @Override
    public void commit() throws IOException {

    }

    @Override
    public void cancel() {

    }
}

public class ChunkStore {
    /**
     * TODO: make the transaction actually do something, i.e. make a transaction atomic
     */
    public class Transaction {
        public long size() {
            return ChunkStore.this.size();
        }

        public IChunkStoreIterator iterator() throws IOException {
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

        synchronized public Lock getLock(String id) {
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

    static class DatabaseBucket {
        private Map<String, WeakReference<IChunkStoreEngine>> map = new HashMap<>();

        synchronized public IChunkStoreEngine getDB(String id, Lock lock) throws FileNotFoundException {
            WeakReference<IChunkStoreEngine> weakObject = map.get(id);
            if (weakObject != null) {
                IChunkStoreEngine db = weakObject.get();
                if (db != null)
                    return db;
            }

            // create new db
            BPlusTree tree = new BPlusTree(new RandomAccessFile(new File(id +".idx"), "rw"));
            PackFile packFile = new PackFile(new RandomAccessFile(new File(id + ".pack"), "rw"));
            IChunkStoreEngine engine = new SimpleChunkStoreEngine(tree, packFile, lock);
            map.put(id, new WeakReference<>(engine));
            return engine;
        }
    }

    final static protected LockBucket lockBucket = new LockBucket();
    final static protected DatabaseBucket databaseBucket = new DatabaseBucket();
    final private IChunkStoreEngine db;
    private Transaction currentTransaction;

    protected ChunkStore(File dir, String name) throws FileNotFoundException {
        String id = dir + "/" + name;
        this.db = databaseBucket.getDB(id, lockBucket.getLock(id));
    }

    static public ChunkStore create(File dir, String name) throws IOException {
        ChunkStore chunkStore = new ChunkStore(dir, name);
        chunkStore.db.create(dir, name);
        return chunkStore;
    }

    static public ChunkStore open(File dir, String name) throws IOException {
        ChunkStore chunkStore = new ChunkStore(dir, name);
        chunkStore.db.open(dir, name);
        return chunkStore;
    }

    static public boolean exists(File dir, String name) {
        File[] children = dir.listFiles();
        if (children == null)
            return false;
        for (File child : children) {
            if (!child.isFile())
                continue;
            if (child.getName().startsWith(name))
                return true;
        }
        return false;
    }

    public byte[] getChunk(HashValue hash) throws IOException {
        return getChunk(hash.getBytes());
    }

    public byte[] getChunk(byte[] hash) throws IOException {
        return db.getChunk(hash);
    }

    public long size() {
        return db.size();
    }

    static public class Entry {
        final public HashValue key;
        final public byte[] data;

        public Entry(HashValue key, byte[] data) {
            this.key = key;
            this.data = data;
        }
    }

    public interface IChunkStoreIterator extends Iterator<Entry> {
        void unlock();
    }

    public IChunkStoreIterator iterator() throws IOException {
        return db.iterator();
    }

    public boolean hasChunk(HashValue hashValue) throws IOException {
        return db.contains(hashValue.getBytes());
    }

    // TODO rename to getCurrentTransaction?
    synchronized public Transaction openTransaction() throws IOException {
        if (currentTransaction != null)
            return currentTransaction;
        currentTransaction = new Transaction();
        return currentTransaction;
    }

    private PutResult<HashValue> put(byte[] data) throws IOException {
        return db.put(data);
    }

    static public int hashSize() {
        return 32;
    }

}
