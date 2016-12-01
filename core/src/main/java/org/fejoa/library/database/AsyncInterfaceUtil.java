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

import java.io.IOException;
import java.util.Collection;


public class AsyncInterfaceUtil {
    static public IRandomDataAccess fakeAsync(final ISyncRandomDataAccess syncRandomDataAccess) {
        return new IRandomDataAccess() {
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
                syncRandomDataAccess.write(data, offset, length);
            }

            @Override
            public int read(byte[] buffer, int offset, int length) throws IOException, CryptoException {
                return syncRandomDataAccess.read(buffer, offset, length);
            }

            @Override
            public void flush() throws IOException {
                syncRandomDataAccess.flush();
            }

            @Override
            public void close() throws IOException, CryptoException {
                syncRandomDataAccess.close();
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
                CompletableFuture<Void> future = new CompletableFuture();
                try {
                    write(data, offset, length);
                    future.complete(null);
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
                return future;
            }

            @Override
            public CompletableFuture<Integer> readAsync(byte[] buffer, int offset, int length) {
                CompletableFuture<Integer> future = new CompletableFuture();
                try {
                    future.complete(read(buffer, offset, length));
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
                return future;
            }

            @Override
            public CompletableFuture<Void> flushAsync() {
                CompletableFuture<Void> future = new CompletableFuture();
                try {
                    flush();
                    future.complete(null);
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
                return future;
            }

            @Override
            public CompletableFuture<Void> closeAsync() {
                CompletableFuture<Void> future = new CompletableFuture();
                try {
                    close();
                    future.complete(null);
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
                return future;
            }
        };
    }

    static public class FakeIODatabase<T extends IIOSyncDatabase> implements IIODatabase {
        final protected T syncDatabase;

        public FakeIODatabase(T syncDatabase) {
            this.syncDatabase = syncDatabase;
        }

        public T getSyncDatabase() {
            return syncDatabase;
        }

        @Override
        public boolean hasFile(String path) throws IOException, CryptoException {
            return syncDatabase.hasFile(path);
        }

        @Override
        public ISyncRandomDataAccess open(String path, Mode mode) throws IOException, CryptoException {
            return syncDatabase.open(path, mode);
        }

        @Override
        public byte[] readBytes(String path) throws IOException, CryptoException {
            return syncDatabase.readBytes(path);
        }

        @Override
        public void putBytes(String path, byte[] data) throws IOException, CryptoException {
            syncDatabase.putBytes(path, data);
        }

        @Override
        public void remove(String path) throws IOException, CryptoException {
            syncDatabase.remove(path);
        }

        @Override
        public Collection<String> listFiles(String path) throws IOException, CryptoException {
            return syncDatabase.listFiles(path);
        }

        @Override
        public Collection<String> listDirectories(String path) throws IOException, CryptoException {
            return syncDatabase.listDirectories(path);
        }

        @Override
        public CompletableFuture<Boolean> hasFileAsync(String path) {
            CompletableFuture<Boolean> future = new CompletableFuture();
            try {
                future.complete(hasFile(path));
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
            return future;
        }

        @Override
        public CompletableFuture<IRandomDataAccess> openAsync(String path, Mode mode) {
            CompletableFuture<IRandomDataAccess> future = new CompletableFuture();
            try {
                future.complete(fakeAsync(open(path, mode)));
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
            return future;
        }

        @Override
        public CompletableFuture<Void> removeAsync(String path) {
            CompletableFuture<Void> future = new CompletableFuture();
            try {
                remove(path);
                future.complete(null);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
            return future;
        }

        @Override
        public CompletableFuture<byte[]> readBytesAsync(String path) {
            CompletableFuture<byte[]> future = new CompletableFuture();
            try {
                future.complete(readBytes(path));
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
            return future;
        }

        @Override
        public CompletableFuture<Void> putBytesAsync(String path, byte[] data) {
            CompletableFuture<Void> future = new CompletableFuture();
            try {
                putBytes(path, data);
                future.complete(null);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
            return future;
        }

        @Override
        public CompletableFuture<Collection<String>> listFilesAsync(String path) {
            CompletableFuture<Collection<String>> future = new CompletableFuture();
            try {
                future.complete(listFiles(path));
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
            return future;
        }

        @Override
        public CompletableFuture<Collection<String>> listDirectoriesAsync(String path) {
            CompletableFuture<Collection<String>> future = new CompletableFuture();
            try {
                future.complete(listDirectories(path));
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
            return future;
        }
    }

    static public FakeIODatabase fakeAsync(IIOSyncDatabase database) {
        return new FakeIODatabase(database);
    }

    static class FakeDatabase extends FakeIODatabase<ISyncDatabase> implements IDatabase {
        public FakeDatabase(ISyncDatabase syncDatabase) {
            super(syncDatabase);
        }

        @Override
        public HashValue getHash(String path) throws CryptoException, IOException {
            return syncDatabase.getHash(path);
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
        public HashValue commit(String message, ICommitSignature signature) throws IOException, CryptoException {
            return syncDatabase.commit(message, signature);
        }

        @Override
        public DatabaseDiff getDiff(HashValue baseCommit, HashValue endCommit) throws IOException, CryptoException {
            return syncDatabase.getDiff(baseCommit, endCommit);
        }

        @Override
        public CompletableFuture<HashValue> getHashAsync(String path) {
            CompletableFuture<HashValue> future = new CompletableFuture();
            try {
                future.complete(getHash(path));
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
            return future;
        }

        @Override
        public CompletableFuture<HashValue> commitAsync(String message, ICommitSignature signature) {
            CompletableFuture<HashValue> future = new CompletableFuture();
            try {
                future.complete(commit(message, signature));
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
            return future;
        }

        @Override
        public CompletableFuture<DatabaseDiff> getDiffAsync(HashValue baseCommit, HashValue endCommit) {
            CompletableFuture<DatabaseDiff> future = new CompletableFuture();
            try {
                future.complete(getDiff(baseCommit, endCommit));
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
            return future;
        }
    }

    static public IDatabase fakeAsync(final ISyncDatabase database) {
        return new FakeDatabase(database);
    }
}
