/*
 * Copyright 2014-2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.database;

import org.fejoa.chunkstore.HashValue;
import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.crypto.CryptoHelper;
import org.fejoa.library.support.WeakListenable;

import java.io.IOException;
import java.util.*;


public class StorageDir extends IOStorageDir {
    private IIOFilter filter;

    public interface IIOFilter {
        byte[] writeFilter(byte[] bytes) throws IOException;
        byte[] readFilter(byte[] bytes) throws IOException;
    }

    public interface IListener {
        void onTipChanged(DatabaseDiff diff, String base, String tip);
    }

    public void addListener(IListener listener) {
        getStorageDirCache().addListener(listener);
    }

    public void removeListener(IListener listener) {
        getStorageDirCache().removeListener(listener);
    }

    /**
     * The StorageDirCache idatabases shared between all StorageDir that are build from the same parent.
     */
    static class StorageDirCache extends WeakListenable<StorageDir.IListener> implements IIOSyncDatabase {
        private ICommitSignature commitSignature;
        final private IDatabase database;
        final private Map<String, byte[]> toAdd = new HashMap<>();
        final private List<String> toDelete = new ArrayList<>();
        private boolean needsCommit = false;

        private void notifyTipChanged(DatabaseDiff diff, HashValue base, HashValue tip) {
            for (IListener listener : getListeners())
                listener.onTipChanged(diff, base.toHex(), tip.toHex());
        }

        public StorageDirCache(IDatabase database) {
            this.database = database;
        }

        public IDatabase getDatabase() {
            return database;
        }

        public void setCommitSignature(ICommitSignature commitSignature) {
            this.commitSignature = commitSignature;
        }

        public void putBytes(String path, byte[] data) throws IOException {
            // process deleted items before adding new items
            if (toDelete.size() > 0)
                flush();
            this.toAdd.put(path, data);
        }

        @Override
        public ISyncRandomDataAccess open(String path, Mode mode) throws IOException, CryptoException {
            if (mode.has(Mode.WRITE))
                needsCommit = true;
            return database.open(path, mode);
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

        public byte[] readBytes(String path) throws IOException {
            if (toAdd.containsKey(path))
                return toAdd.get(path);
            try {
                return IOStorageDir.readBytes(database, path);
            } catch (CryptoException e) {
                throw new IOException(e);
            }
        }

        public void flush() throws IOException {
            try {
                for (Map.Entry<String, byte[]> entry : toAdd.entrySet())
                    StorageDir.putBytes(database, entry.getKey(), entry.getValue());

                for (String path : toDelete)
                    database.remove(path);
            } catch (CryptoException e) {
                throw new IOException(e);
            }
            toAdd.clear();
            toDelete.clear();
            needsCommit = true;
        }

        private boolean needsCommit() {
            if ((toAdd.size() == 0 && toDelete.size() == 0) && !needsCommit)
                return false;
            return true;
        }

        public void commit(String message) throws IOException {
            if (!needsCommit())
                return;
            flush();
            needsCommit = false;
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

        this.filter = storageDir.filter;
    }

    public StorageDir(IDatabase database, String baseDir) {
        super(AsyncInterfaceUtil.fakeAsync(new StorageDirCache(database)), baseDir);
    }

    private StorageDirCache getStorageDirCache() {
        return (StorageDirCache)((AsyncInterfaceUtil.FakeIODatabase)this.database).getSyncDatabase();
    }

    public void setCommitSignature(ICommitSignature commitSignature) {
        this.getStorageDirCache().setCommitSignature(commitSignature);
    }

    public ICommitSignature getCommitSignature() {
        return this.getStorageDirCache().getCommitSignature();
    }

    public void setFilter(IIOFilter filter) {
        this.filter = filter;
    }

    public IDatabase getDatabase() {
        return getStorageDirCache().getDatabase();
    }

    public HashValue getHash(String path) throws IOException {
        return getStorageDirCache().getHash(path, filter);
    }

    @Override
    public byte[] readBytes(String path) throws IOException, CryptoException {
        byte[] bytes = getStorageDirCache().readBytes(getRealPath(path));
        if (filter != null)
            return filter.readFilter(bytes);
        return bytes;
    }

    @Override
    public void putBytes(String path, byte[] data) throws IOException, CryptoException {
        if (filter != null)
            data = filter.writeFilter(data);
        getStorageDirCache().putBytes(getRealPath(path), data);
    }

    public void commit(String message) throws IOException {
        getStorageDirCache().commit(message);
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
        getStorageDirCache().onTipUpdated(old, newTip);
    }
}
