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

import java.io.*;
import java.util.Collection;


interface IRandomDataAccess extends ISyncRandomDataAccess {
    CompletableFuture<Void> writeAsync(byte[] data);
    CompletableFuture<Integer> readAsync(byte[] buffer);

    CompletableFuture<Void> flushAsync();
    CompletableFuture<Void> closeAsync();
}


class AsyncDatabase implements IDatabase2 {
    final private LooperThread looperThread = new LooperThread(100);
    final private IDatabaseInterface syncDatabase;

    public AsyncDatabase(IDatabaseInterface syncDatabase) {
        this.syncDatabase = syncDatabase;

        looperThread.start();
    }

    public CompletableFuture<Void> close() {
        return CompletableFuture.runAsync(new Runnable() {
            @Override
            public void run() {
                looperThread.quit(true);
            }
        });
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
    public CompletableFuture<HashValue> getHash(final String path) {
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
    public CompletableFuture<String> getBranch() {
        final CompletableFuture<String> future = new CompletableFuture<>();
        looperThread.post(new Runnable() {
            @Override
            public void run() {
                try {
                    future.complete(syncDatabase.getBranch());
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            }
        });
        return future;
    }

    @Override
    public CompletableFuture<HashValue> getTip() throws IOException {
        final CompletableFuture<HashValue> future = new CompletableFuture<>();
        looperThread.post(new Runnable() {
            @Override
            public void run() {
                try {
                    future.complete(syncDatabase.getTip());
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            }
        });
        return future;
    }

    @Override
    public CompletableFuture<Boolean> hasFile(final String path) {
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
    public CompletableFuture<IRandomDataAccess> open(String path, String mode) {
        return null;
    }

    @Override
    public CompletableFuture<Void> remove(final String path) {
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
    public CompletableFuture<HashValue> commit(final String message, final ICommitSignature signature) {
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
    public CompletableFuture<Collection<String>> listFiles(final String path) {
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
    public CompletableFuture<Collection<String>> listDirectories(final String path) {
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


interface IIODatabase2 {
    CompletableFuture<Boolean> hasFile(String path);
    CompletableFuture<IRandomDataAccess> open(String path, String mode);
    CompletableFuture<Void> remove(String path);

    CompletableFuture<Collection<String>> listFiles(String path);
    CompletableFuture<Collection<String>> listDirectories(String path);
}

public interface IDatabase2 extends IIODatabase2 {
    CompletableFuture<HashValue> getHash(String path);
    CompletableFuture<String> getBranch();
    CompletableFuture<HashValue> getTip() throws IOException;

    CompletableFuture<HashValue> commit(String message, ICommitSignature signature);

}
