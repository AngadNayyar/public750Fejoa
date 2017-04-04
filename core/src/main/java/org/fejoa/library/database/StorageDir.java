/*
 * Copyright 2014-2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.database;

import java8.util.concurrent.CompletableFuture;
import java8.util.concurrent.CompletionStage;
import java8.util.function.BiConsumer;
import java8.util.function.Function;
import org.fejoa.chunkstore.HashValue;
import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.support.WeakListenable;

import java.io.IOException;
import java.util.concurrent.Executor;


public class StorageDir extends IOStorageDir {
    public interface IListener {
        void onTipChanged(DatabaseDiff diff);
    }

    public void addListener(IListener listener) {
        getStorageDirCache().addListener(listener);
    }

    public void removeListener(IListener listener) {
        getStorageDirCache().removeListener(listener);
    }

    /**
     * The StorageDirCache is shared between all StorageDir that are build from the same parent.
     */
    static class StorageDirCache extends DatabaseDecorator {
        private ICommitSignature commitSignature;
        final WeakListenable<StorageDir.IListener> listeners = new WeakListenable<>();
        final Executor listenerExecutor;

        public StorageDirCache(IDatabase database, Executor listenerExecutor) {
            super(database);

            this.listenerExecutor = listenerExecutor;
        }

        private void notifyTipChanged(final DatabaseDiff diff) {
            for (final IListener listener : listeners.getListeners()) {
                if (listenerExecutor != null) {
                    listenerExecutor.execute(new Runnable() {
                        @Override
                        public void run() {
                            listener.onTipChanged(diff);
                        }
                    });
                } else {
                    listener.onTipChanged(diff);
                }
            }
        }

        public void addListener(IListener listener) {
            listeners.addListener(listener);
        }

        public void removeListener(IListener listener) {
            listeners.removeListener(listener);
        }

        public IDatabase getDatabase() {
            return database;
        }

        public void setCommitSignature(ICommitSignature commitSignature) {
            this.commitSignature = commitSignature;
        }

        public CompletableFuture<HashValue> commitAsync(String message) {
            assert listenerExecutor != null;

            final HashValue base = getDatabase().getTip();
            CompletableFuture<HashValue> result = database.commitAsync(message, commitSignature);
            result.thenCompose(new Function<HashValue, CompletionStage<DatabaseDiff>>() {
                @Override
                public CompletionStage<DatabaseDiff> apply(HashValue hashValue) {
                    if (listeners.getListeners().size() > 0) {
                        HashValue tip = getDatabase().getTip();
                        return getDatabase().getDiffAsync(base, tip);
                    }
                    return CompletableFuture.completedFuture(null);
                }
            }).whenComplete(new BiConsumer<DatabaseDiff, Throwable>() {
                @Override
                public void accept(DatabaseDiff diff, Throwable throwable) {
                    if (throwable != null || diff == null)
                        return;
                    notifyTipChanged(diff);
                }
            });
            return result;
        }

        public void commit(String message) throws IOException {
            HashValue base = getDatabase().getTip();
            try {
                database.commit(message, commitSignature);

                if (listeners.getListeners().size() > 0) {
                    HashValue tip = getDatabase().getTip();
                    DatabaseDiff diff = getDatabase().getDiff(base, tip);
                    notifyTipChanged(diff);
                }
            } catch (CryptoException e) {
                throw new IOException(e);
            }
        }

        public void onTipUpdated(HashValue old, HashValue newTip) throws IOException {
            if (listeners.getListeners().size() > 0) {
                try {
                    DatabaseDiff diff = getDatabase().getDiff(old, newTip);
                    notifyTipChanged(diff);
                } catch (CryptoException e) {
                    throw new IOException(e);
                }
            }
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
    }

    public StorageDir(IDatabase database, String baseDir, Executor listenerExecutor) {
        super(new StorageDirCache(database, listenerExecutor), baseDir);
    }

    private StorageDirCache getStorageDirCache() {
        return (StorageDirCache)this.database;
    }

    public void setCommitSignature(ICommitSignature commitSignature) {
        this.getStorageDirCache().setCommitSignature(commitSignature);
    }

    public ICommitSignature getCommitSignature() {
        return this.getStorageDirCache().getCommitSignature();
    }

    public IDatabase getDatabase() {
        return getStorageDirCache().getDatabase();
    }

    public HashValue getHash(String path) throws IOException, CryptoException {
        return getStorageDirCache().getHash(path);
    }

    @Override
    public byte[] readBytes(String path) throws IOException, CryptoException {
        return getStorageDirCache().readBytes(getRealPath(path));
    }

    @Override
    public void putBytes(String path, byte[] data) throws IOException, CryptoException {
        getStorageDirCache().putBytes(getRealPath(path), data);
    }

    public void commit(String message) throws IOException {
        getStorageDirCache().commit(message);
    }

    public void commit() throws IOException {
        commit("Client commit");
    }

    public CompletableFuture<HashValue> commitAsync() {
        return getStorageDirCache().commitAsync("Client commit");
    }

    public CompletableFuture<HashValue> commitAsync(String message) {
        return getStorageDirCache().commitAsync(message);
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
