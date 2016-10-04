/*
 * Copyright 2014-2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.database;

import org.fejoa.chunkstore.HashValue;
import org.fejoa.library.crypto.*;
import org.fejoa.library.support.WeakListenable;

import java.io.IOException;
import java.util.*;


public class StorageDir extends IOStorageDir {
    final private StorageDirCache cache;
    private IIOFilter filter;

    public interface IIOFilter {
        byte[] writeFilter(byte[] bytes) throws IOException;
        byte[] readFilter(byte[] bytes) throws IOException;
    }

    public interface IListener {
        void onTipChanged(DatabaseDiff diff, String base, String tip);
    }

    public void addListener(IListener listener) {
        cache.addListener(listener);
    }

    public void removeListener(IListener listener) {
        cache.removeListener(listener);
    }

    /**
     * The StorageDirCache is shared between all StorageDir that are build from the same parent.
     */
    static class StorageDirCache extends WeakListenable<StorageDir.IListener> implements IIODatabaseInterface {
        private ICommitSignature commitSignature;
        final private IDatabaseInterface database;
        final private Map<String, byte[]> toAdd = new HashMap<>();
        final private List<String> toDelete = new ArrayList<>();

        private void notifyTipChanged(DatabaseDiff diff, HashValue base, HashValue tip) {
            for (IListener listener : getListeners())
                listener.onTipChanged(diff, base.toHex(), tip.toHex());
        }

        public StorageDirCache(IDatabaseInterface database) {
            this.database = database;
        }

        public IDatabaseInterface getDatabase() {
            return database;
        }

        public void setCommitSignature(ICommitSignature commitSignature) {
            this.commitSignature = commitSignature;
        }

        @Override
        public void writeBytes(String path, byte[] data) throws IOException {
            // process deleted items before adding new items
            if (toDelete.size() > 0)
                flush();
            this.toAdd.put(path, data);
        }

        public HashValue getHash(String path, IIOFilter filter) throws IOException {
            byte[] bytes = toAdd.get(path);
            if (bytes != null) {
                if (filter != null)
                    bytes = filter.readFilter(bytes);
                return new HashValue(CryptoHelper.sha1Hash(bytes));
            }
            try {
                return database.getHash(path);
            } catch (CryptoException e) {
                throw new IOException(e);
            }
        }

        @Override
        public boolean hasFile(String path) throws IOException, CryptoException {
            if (toDelete.contains(path))
                return false;
            if (toAdd.containsKey(path))
                return true;
            return database.hasFile(path);
        }

        @Override
        public byte[] readBytes(String path) throws IOException {
            if (toAdd.containsKey(path))
                return toAdd.get(path);
            try {
                return database.readBytes(path);
            } catch (CryptoException e) {
                throw new IOException(e);
            }
        }

        public void flush() throws IOException {
            try {
                for (Map.Entry<String, byte[]> entry : toAdd.entrySet())
                    database.writeBytes(entry.getKey(), entry.getValue());

                for (String path : toDelete)
                    database.remove(path);
            } catch (CryptoException e) {
                throw new IOException(e);
            }
            toAdd.clear();
            toDelete.clear();
        }

        private boolean needsCommit() {
            if (toAdd.size() == 0 && toDelete.size() == 0)
                return false;
            return true;
        }

        public void commit(String message) throws IOException {
            if (!needsCommit())
                return;
            flush();
            HashValue base = getDatabase().getTip();
            try {
                database.commit(message, commitSignature);

                if (getListeners().size() > 0) {
                    HashValue tip = getDatabase().getTip();
                    DatabaseDiff diff = getDatabase().getDiff(base, tip);
                    notifyTipChanged(diff, base, tip);
                }
            } catch (CryptoException e) {
                throw new IOException(e);
            }
        }

        public void onTipUpdated(HashValue old, HashValue newTip) throws IOException {
            if (getListeners().size() > 0) {
                try {
                    DatabaseDiff diff = getDatabase().getDiff(old, newTip);
                    notifyTipChanged(diff, old, newTip);
                } catch (CryptoException e) {
                    throw new IOException(e);
                }
            }
        }

        @Override
        public Collection<String> listFiles(String path) throws IOException {
            flush();
            try {
                return database.listFiles(path);
            } catch (CryptoException e) {
                throw new IOException(e);
            }
        }

        @Override
        public Collection<String> listDirectories(String path) throws IOException {
            flush();
            try {
                return database.listDirectories(path);
            } catch (CryptoException e) {
                throw new IOException(e);
            }
        }

        public void remove(String path) {
            toDelete.add(path);
        }

        public ICommitSignature getCommitSignature() {
            return commitSignature;
        }
    }

    public StorageDir(StorageDir storageDir) {
        this(storageDir, storageDir.getBaseDir(), true);
    }

    public StorageDir(StorageDir storageDir, String baseDir) {
        this(storageDir, baseDir, false);
    }

    public StorageDir(StorageDir storageDir, String baseDir, boolean absoluteBaseDir) {
        super(storageDir, baseDir, absoluteBaseDir);

        this.cache = storageDir.cache;
        this.filter = storageDir.filter;
    }

    public StorageDir(IDatabaseInterface database, String baseDir) {
        super(new StorageDirCache(database), baseDir);

        this.cache = (StorageDirCache)this.database;
    }

    public void setCommitSignature(ICommitSignature commitSignature) {
        this.cache.setCommitSignature(commitSignature);
    }

    public ICommitSignature getCommitSignature() {
        return this.cache.getCommitSignature();
    }

    public void setFilter(IIOFilter filter) {
        this.filter = filter;
    }

    public IDatabaseInterface getDatabase() {
        return cache.getDatabase();
    }

    static public String appendDir(String baseDir, String dir) {
        String newDir = baseDir;
        if (dir.equals(""))
            return baseDir;
        if (!newDir.equals(""))
            newDir += "/";
        newDir += dir;
        return newDir;
    }

    public HashValue getHash(String path) throws IOException {
        return cache.getHash(path, filter);
    }

    @Override
    public byte[] readBytes(String path) throws IOException, CryptoException {
        byte[] bytes = super.readBytes(path);
        if (filter != null)
            return filter.readFilter(bytes);
        return bytes;
    }

    @Override
    public void writeBytes(String path, byte[] data) throws IOException, CryptoException {
        if (filter != null)
            data = filter.writeFilter(data);
        super.writeBytes(path, data);
    }

    public void commit(String message) throws IOException {
        cache.commit(message);
    }

    public void commit() throws IOException {
        commit("Client commit");
    }

    public HashValue getTip() throws IOException {
        return getDatabase().getTip();
    }

    public String getBranch() {
        return getDatabase().getBranch();
    }

    public DatabaseDiff getDiff(HashValue baseCommit, HashValue endCommit) throws IOException {
        try {
            return getDatabase().getDiff(baseCommit, endCommit);
        } catch (CryptoException e) {
            throw new IOException(e);
        }
    }

    public void onTipUpdated(HashValue old, HashValue newTip) throws IOException {
        cache.onTipUpdated(old, newTip);
    }
}
