/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.database;

import java8.util.concurrent.CompletableFuture;
import java8.util.function.Supplier;
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
                throw new RuntimeException("Unexpected Exception: " + path, e);
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
                throw new RuntimeException("Unexpected Exception: " + path, e);
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
                throw new RuntimeException("Unexpected Exception: " + path, e);
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
                throw new RuntimeException("Unexpected Exception: " + path, e);
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
                throw new RuntimeException("Unexpected Exception: " + path, e);
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
                throw new RuntimeException("Unexpected Exception: " + path, e);
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
                throw new RuntimeException("Unexpected Exception: " + path, e);
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
                throw new RuntimeException("Unexpected Exception: ", e);
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
            write(data, 0, data.length);
        }

        @Override
        public int read(byte[] buffer) throws IOException, CryptoException {
            return read(buffer, 0, buffer.length);
        }

        @Override
        public void write(byte[] data, int offset, int length) throws IOException {
            try {
                writeAsync(data, offset, length, true).get();
            } catch (Exception e) {
                throw new IOException(e);
            }
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException, CryptoException {
            try {
                return readAsync(buffer, offset, length, true).get();
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

        private CompletableFuture<Void> writeAsync(final byte[] data, final int offset, final int length,
                                                   boolean runNext) {
            return post(new IValueGetter<Void>() {
                @Override
                public Void get() throws Exception {
                    syncRandomDataAccess.write(data, offset, length);
                    return null;
                }
            });
        }

        private CompletableFuture<Integer> readAsync(final byte[] buffer, final int offset, final int length,
                                                     boolean runNext) {
            return post(new IValueGetter<Integer>() {
                @Override
                public Integer get() throws Exception {
                    return syncRandomDataAccess.read(buffer, offset, length);
                }
            });
        }

        private CompletableFuture<Void> flush(boolean runNext) {
            return post(new IValueGetter<Void>() {
                @Override
                public Void get() throws Exception {
                    syncRandomDataAccess.flush();
                    return null;
                }
            });
        }

        private CompletableFuture<Void> close(boolean runNext) {
            return post(new IValueGetter<Void>() {
                @Override
                public Void get() throws Exception {
                    syncRandomDataAccess.close();
                    return null;
                }
            });
        }

        @Override
        public CompletableFuture<Void> writeAsync(byte[] data) {
            return writeAsync(data, 0, data.length);
        }

        @Override
        public CompletableFuture<Integer> readAsync(byte[] buffer) {
            return readAsync(buffer, 0, buffer.length);
        }

        @Override
        public CompletableFuture<Void> writeAsync(byte[] data, int offset, int length) {
            return writeAsync(data, offset, length, false);
        }

        @Override
        public CompletableFuture<Integer> readAsync(byte[] buffer, int offset, int length) {
            return readAsync(buffer, offset, length, false);
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
        return post(new IValueGetter<HashValue>() {
            @Override
            public HashValue get() throws Exception {
                return syncDatabase.getHash(path);
            }
        });
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
        return post(new IValueGetter<Boolean>() {
            @Override
            public Boolean get() throws Exception {
                return syncDatabase.hasFile(path);
            }
        });
    }

    @Override
    public CompletableFuture<IRandomDataAccess> openAsync(final String path, final IIOSyncDatabase.Mode mode) {
        return post(new IValueGetter<IRandomDataAccess>() {
            @Override
            public IRandomDataAccess get() throws Exception {
                return new RandomDataAccess(syncDatabase.open(path, mode));
            }
        });
    }

    @Override
    public CompletableFuture<Void> removeAsync(final String path) {
        return post(new IValueGetter<Void>() {
            @Override
            public Void get() throws Exception {
                syncDatabase.remove(path);
                return null;
            }
        });
    }

    @Override
    public CompletableFuture<byte[]> readBytesAsync(final String path) {
        return post(new IValueGetter<byte[]>() {
            @Override
            public byte[] get() throws Exception {
                return syncDatabase.readBytes(path);
            }
        });
    }

    @Override
    public CompletableFuture<Void> putBytesAsync(final String path, final byte[] data) {
        return post(new IValueGetter<Void>() {
            @Override
            public Void get() throws Exception {
                syncDatabase.putBytes(path, data);
                return null;
            }
        });
    }

    @Override
    public CompletableFuture<HashValue> commitAsync(final String message, final ICommitSignature signature) {
        return post(new IValueGetter<HashValue>() {
            @Override
            public HashValue get() throws Exception {
                return syncDatabase.commit(message, signature);
            }
        });
    }

    @Override
    public CompletableFuture<DatabaseDiff> getDiffAsync(final HashValue baseCommit, final HashValue endCommit) {
        return post(new IValueGetter<DatabaseDiff>() {
            @Override
            public DatabaseDiff get() throws Exception {
                return syncDatabase.getDiff(baseCommit, endCommit);
            }
        });
    }

    @Override
    public CompletableFuture<Collection<String>> listFilesAsync(final String path) {
        return post(new IValueGetter<Collection<String>>() {
            @Override
            public Collection<String> get() throws Exception {
                return syncDatabase.listFiles(path);
            }
        });
    }

    @Override
    public CompletableFuture<Collection<String>> listDirectoriesAsync(final String path) {
        return post(new IValueGetter<Collection<String>>() {
            @Override
            public Collection<String> get() throws Exception {
                return syncDatabase.listDirectories(path);
            }
        });
    }

    interface IValueGetter<T> {
        T get() throws Exception;
    }

    private <T> CompletableFuture<T> post(final IValueGetter<T> getter) {
        final CompletableFuture<T> future = new CompletableFuture<>();
        looperThread.post(new Runnable() {
            @Override
            public void run() {
                try {
                    final T value = getter.get();
                    // leave the looper thread and give way for other database requests
                    future.completeAsync(new Supplier<T>() {
                        @Override
                        public T get() {
                            return value;
                        }
                    });
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            }
        });
        return future;
    }
}
