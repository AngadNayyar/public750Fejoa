/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.database;

import java8.util.concurrent.CompletableFuture;
import org.fejoa.chunkstore.HashValue;
import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.support.LooperThread;

import javax.imageio.IIOException;
import java.io.IOException;
import java.util.Collection;


public class AsyncDatabase implements IDatabase {
    final private LooperThread looperThread;
    final protected ISyncDatabase syncDatabase;

    public AsyncDatabase(AsyncDatabase database, ISyncDatabase syncDatabase) {
        this.syncDatabase = syncDatabase;
        this.looperThread = database.looperThread;
    }

    public AsyncDatabase(ISyncDatabase syncDatabase) {
        this.syncDatabase = syncDatabase;

        looperThread = new LooperThread(100);
        looperThread.setDaemon(true);
        looperThread.start();
    }

    public CompletableFuture<Void> close(final boolean waitTillFinished) {
        return CompletableFuture.runAsync(new Runnable() {
            @Override
            public void run() {
                looperThread.quit(waitTillFinished);
            }
        });
    }

    @Override
    public boolean hasFile(String path) throws IOException, CryptoException {
        try {
            return hasFileAsync(path).get();
        } catch (Exception e) {
            if (e.getCause() instanceof IOException)
                throw (IOException)e.getCause();
            else if (e.getCause() instanceof CryptoException)
                throw (CryptoException)e.getCause();
            else
                throw new RuntimeException("Unexpected Exception");
        }
    }

    @Override
    public ISyncRandomDataAccess open(String path, Mode mode) throws IOException, CryptoException {
        try {
            return openAsync(path, mode).get();
        } catch (Exception e) {
            if (e.getCause() instanceof IOException)
                throw (IOException)e.getCause();
            else if (e.getCause() instanceof CryptoException)
                throw (CryptoException)e.getCause();
            else
                throw new RuntimeException("Unexpected Exception");
        }
    }

    @Override
    public byte[] readBytes(String path) throws IOException, CryptoException {
        try {
            return readBytesAsync(path).get();
        } catch (Exception e) {
            if (e.getCause() instanceof IOException)
                throw (IOException)e.getCause();
            else if (e.getCause() instanceof CryptoException)
                throw (CryptoException)e.getCause();
            else
                throw new RuntimeException("Unexpected Exception");
        }
    }

    @Override
    public void putBytes(String path, byte[] data) throws IOException, CryptoException {
        try {
            putBytesAsync(path, data).get();
        } catch (Exception e) {
            if (e.getCause() instanceof IOException)
                throw (IOException)e.getCause();
            else if (e.getCause() instanceof CryptoException)
                throw (CryptoException)e.getCause();
            else
                throw new RuntimeException("Unexpected Exception");
        }
    }

    @Override
    public void remove(String path) throws IOException, CryptoException {
        try {
            removeAsync(path).get();
        } catch (Exception e) {
            if (e.getCause() instanceof IOException)
                throw (IOException)e.getCause();
            else if (e.getCause() instanceof CryptoException)
                throw (CryptoException)e.getCause();
            else
                throw new RuntimeException("Unexpected Exception");
        }
    }

    @Override
    public Collection<String> listFiles(String path) throws IOException, CryptoException {
        try {
            return listFilesAsync(path).get();
        } catch (Exception e) {
            if (e.getCause() instanceof IOException)
                throw (IOException)e.getCause();
            else if (e.getCause() instanceof CryptoException)
                throw (CryptoException)e.getCause();
            else
                throw new RuntimeException("Unexpected Exception");
        }
    }

    @Override
    public Collection<String> listDirectories(String path) throws IOException, CryptoException {
        try {
            return listDirectoriesAsync(path).get();
        } catch (Exception e) {
            if (e.getCause() instanceof IOException)
                throw (IOException)e.getCause();
            else if (e.getCause() instanceof CryptoException)
                throw (CryptoException)e.getCause();
            else
                throw new RuntimeException("Unexpected Exception");
        }
    }

    @Override
    public DatabaseDiff getDiff(HashValue baseCommit, HashValue endCommit) throws IOException, CryptoException {
        try {
            return getDiffAsync(baseCommit, endCommit).get();
        } catch (Exception e) {
            if (e.getCause() instanceof IOException)
                throw (IOException)e.getCause();
            else if (e.getCause() instanceof CryptoException)
                throw (CryptoException)e.getCause();
            else
                throw new RuntimeException("Unexpected Exception");
        }
    }

    @Override
    public HashValue getHash(String path) throws CryptoException {
        try {
            return getHashAsync(path).get();
        } catch (Exception e) {
            if (e.getCause() instanceof CryptoException)
                throw (CryptoException)e.getCause();
            else
                throw new RuntimeException("Unexpected Exception");
        }
    }

    @Override
    public HashValue commit(String message, ICommitSignature signature) throws IOException {
        try {
            return commitAsync(message, signature).get();
        } catch (Exception e) {
            if (e.getCause() instanceof IOException)
                throw (IOException)e.getCause();
            else
                throw new RuntimeException("Unexpected Exception");
        }
    }

    public class RandomDataAccess implements IRandomDataAccess {
        final private ISyncRandomDataAccess syncRandomDataAccess;

        private RandomDataAccess(ISyncRandomDataAccess access) {
            this.syncRandomDataAccess = access;
        }

        @Override
        public long length() {
            return syncRandomDataAccess.length();
        }

        @Override
        public long position() {
            return syncRandomDataAccess.position();
        }

        @Override
        public void seek(long position) throws IOException, CryptoException {
            syncRandomDataAccess.seek(position);
        }

        @Override
        public void write(byte[] data) throws IOException {
            try {
                writeAsync(data, true).get();
            } catch (Exception e) {
                throw new IOException(e);
            }
        }

        @Override
        public int read(byte[] buffer) throws IOException {
            try {
                return readAsync(buffer, true).get();
            } catch (Exception e) {
                throw new IOException(e);
            }
        }

        @Override
        public void flush() throws IOException {
            try {
                flush(true).get();
            } catch (Exception e) {
                throw new IOException(e);
            }
        }

        @Override
        public void close() throws IOException {
            try {
                close(true).get();
            } catch (Exception e) {
                throw new IOException(e);
            }
        }

        private CompletableFuture<Void> writeAsync(final byte[] data, boolean runNext) {
            final CompletableFuture<Void> future = new CompletableFuture<>();
            looperThread.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        syncRandomDataAccess.write(data);
                        future.complete(null);
                    } catch (IOException e) {
                        future.completeExceptionally(e);
                    }
                }
            }, runNext);
            return future;
        }

        private CompletableFuture<Integer> readAsync(final byte[] buffer, boolean runNext) {
            final CompletableFuture<Integer> future = new CompletableFuture<>();
            looperThread.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        future.complete(syncRandomDataAccess.read(buffer));
                    } catch (Exception e) {
                        future.completeExceptionally(e);
                    }
                }
            }, runNext);
            return future;
        }

        private CompletableFuture<Void> flush(boolean runNext) {
            final CompletableFuture<Void> future = new CompletableFuture<>();
            looperThread.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        syncRandomDataAccess.flush();
                        future.complete(null);
                    } catch (IOException e) {
                        future.completeExceptionally(e);
                    }
                }
            }, runNext);
            return future;
        }

        private CompletableFuture<Void> close(boolean runNext) {
            final CompletableFuture<Void> future = new CompletableFuture<>();
            looperThread.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        syncRandomDataAccess.close();
                        future.complete(null);
                    } catch (Exception e) {
                        future.completeExceptionally(e);
                    }
                }
            }, runNext);
            return future;
        }

        @Override
        public CompletableFuture<Void> writeAsync(byte[] data) {
            return writeAsync(data, false);
        }

        @Override
        public CompletableFuture<Integer> readAsync(byte[] buffer) {
            return readAsync(buffer, false);
        }

        @Override
        public CompletableFuture<Void> flushAsync() {
            return flush(false);
        }

        @Override
        public CompletableFuture<Void> closeAsync() {
            return close(false);
        }
    }

    @Override
    public CompletableFuture<HashValue> getHashAsync(final String path) {
        final CompletableFuture<HashValue> future = new CompletableFuture<>();
        looperThread.post(new Runnable() {
            @Override
            public void run() {
                try {
                    future.complete(syncDatabase.getHash(path));
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            }
        });
        return future;
    }

    @Override
    public String getBranch() {
        return syncDatabase.getBranch();
    }

    @Override
    public HashValue getTip() {
        return syncDatabase.getTip();
    }

    @Override
    public CompletableFuture<Boolean> hasFileAsync(final String path) {
        final CompletableFuture<Boolean> future = new CompletableFuture<>();
        looperThread.post(new Runnable() {
            @Override
            public void run() {
                try {
                    future.complete(syncDatabase.hasFile(path));
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            }
        });
        return future;
    }

    @Override
    public CompletableFuture<IRandomDataAccess> openAsync(final String path, final IIOSyncDatabase.Mode mode) {
        final CompletableFuture<IRandomDataAccess> future = new CompletableFuture<>();
        looperThread.post(new Runnable() {
            @Override
            public void run() {
                try {
                    ISyncRandomDataAccess syncRandomDataAccess = syncDatabase.open(path, mode);
                    future.complete(new RandomDataAccess(syncRandomDataAccess));
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            }
        });
        return future;
    }

    @Override
    public CompletableFuture<Void> removeAsync(final String path) {
        final CompletableFuture<Void> future = new CompletableFuture<>();
        looperThread.post(new Runnable() {
            @Override
            public void run() {
                try {
                    syncDatabase.remove(path);
                    future.complete(null);
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            }
        });
        return future;
    }

    @Override
    public CompletableFuture<byte[]> readBytesAsync(final String path) {
        final CompletableFuture<byte[]> future = new CompletableFuture<>();
        looperThread.post(new Runnable() {
            @Override
            public void run() {
                try {
                    future.complete(syncDatabase.readBytes(path));
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            }
        });
        return future;
    }

    @Override
    public CompletableFuture<Void> putBytesAsync(final String path, final byte[] data) {
        final CompletableFuture<Void> future = new CompletableFuture<>();
        looperThread.post(new Runnable() {
            @Override
            public void run() {
                try {
                    syncDatabase.putBytes(path, data);
                    future.complete(null);
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            }
        });
        return future;
    }

    @Override
    public CompletableFuture<HashValue> commitAsync(final String message, final ICommitSignature signature) {
        final CompletableFuture<HashValue> future = new CompletableFuture<>();
        looperThread.post(new Runnable() {
            @Override
            public void run() {
                try {
                    future.complete(syncDatabase.commit(message, signature));
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            }
        });
        return future;
    }

    @Override
    public CompletableFuture<DatabaseDiff> getDiffAsync(final HashValue baseCommit, final HashValue endCommit) {
        final CompletableFuture<DatabaseDiff> future = new CompletableFuture<>();
        looperThread.post(new Runnable() {
            @Override
            public void run() {
                try {
                    future.complete(syncDatabase.getDiff(baseCommit, endCommit));
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            }
        });
        return future;
    }

    @Override
    public CompletableFuture<Collection<String>> listFilesAsync(final String path) {
        final CompletableFuture<Collection<String>> future = new CompletableFuture<>();
        looperThread.post(new Runnable() {
            @Override
            public void run() {
                try {
                    future.complete(syncDatabase.listFiles(path));
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            }
        });
        return future;
    }

    @Override
    public CompletableFuture<Collection<String>> listDirectoriesAsync(final String path) {
        final CompletableFuture<Collection<String>> future = new CompletableFuture<>();
        looperThread.post(new Runnable() {
            @Override
            public void run() {
                try {
                    future.complete(syncDatabase.listDirectories(path));
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            }
        });
        return future;
    }
}
