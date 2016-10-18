/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.remote;

import org.fejoa.chunkstore.HashValue;
import org.fejoa.library.Remote;
import org.fejoa.library.UserData;
import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.database.DatabaseDiff;
import org.fejoa.library.database.JGitInterface;
import org.fejoa.library.database.StorageDir;
import org.fejoa.chunkstore.Repository;
import org.fejoa.library.FejoaContext;
import org.fejoa.library.BranchInfo;
import org.fejoa.library.support.Task;

import java.io.IOException;
import java.util.*;


public class SyncManager {
    final private FejoaContext context;
    final private UserData userData;
    final private ConnectionManager connectionManager;

    final private Remote remote;
    private Task watchFunction;
    private StorageDir.IListener storageDirListener;
    private Collection<BranchInfo.Location> watchedBranches;
    private Task.IObserver<TaskUpdate, Void> watchObserver;
    final private Map<String, Task<Void, ChunkStorePullJob.Result>> ongoingSyncJobs = new HashMap<>();

    public SyncManager(FejoaContext context, UserData userData, ConnectionManager connectionManager, Remote remote) {
        this.context = context;
        this.userData = userData;
        this.connectionManager = connectionManager;
        this.remote = remote;
    }

    private void sync(List<String> storageIdList, final Task.IObserver<TaskUpdate, Void> observer) {
        if (ongoingSyncJobs.size() != 0)
            return;

        // add the ids in case the job finishes before submit returns, e.g. if executed immediately
        for (String id : storageIdList) {
            if (!ongoingSyncJobs.containsKey(id))
                ongoingSyncJobs.put(id, null);
        }

        for (String id : storageIdList) {
            for (BranchInfo.Location branchLocation : watchedBranches) {
                if (branchLocation.getBranchInfo().getBranch().equals(id))
                    sync(branchLocation, storageIdList.size(), observer);
            }
        }
    }

    private void watch(Collection<BranchInfo.Location> branchInfoList, Task.IObserver<Void, WatchJob.Result> observer) {
        watchedBranches = branchInfoList;

        storageDirListener = createStorageWatchListener(watchObserver);
        for (BranchInfo.Location location : branchInfoList) {
            try {
                StorageDir storageDir = userData.getStorageDir(location.getBranchInfo());
                storageDir.addListener(storageDirListener);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        Map<String, ConnectionManager.UserAuthInfo> authInfos = new HashMap<>();
        for (BranchInfo.Location location : branchInfoList) {
            try {
                AuthInfo authInfo = location.getAuthInfo(context);
                Remote remote = location.getRemote();
                ConnectionManager.UserAuthInfo userAuthInfo = new ConnectionManager.UserAuthInfo(remote.getUser(),
                        authInfo);
                authInfos.put(authInfo.getId(), userAuthInfo);
            } catch (Exception e) {
                observer.onException(e);
            }
        }

        watchFunction = connectionManager.submit(new WatchJob(context, branchInfoList), remote.getServer(),
                authInfos.values(), observer);
    }

    private boolean isWatching() {
        return watchFunction != null;
    }

    private StorageDir.IListener createStorageWatchListener(final Task.IObserver<TaskUpdate, Void> observer) {
        return new StorageDir.IListener() {
            @Override
            public void onTipChanged(DatabaseDiff diff, String base, String tip) {
                if (isWatching())
                    startWatching(watchedBranches, observer);
            }
        };
    }

    public void startWatching(Collection<BranchInfo.Location> branchInfoList,
                              final Task.IObserver<TaskUpdate, Void> observer) {
        if (isWatching())
            stopWatching();

        this.watchObserver = observer;
        final Task.IObserver<Void, WatchJob.Result> watchObserver = new Task.IObserver<Void, WatchJob.Result>() {
            private TaskUpdate makeUpdate(String message) {
                return new TaskUpdate("Watching", -1, -1, message);
            }

            @Override
            public void onProgress(Void aVoid) {

            }

            @Override
            public void onResult(WatchJob.Result result) {
                // timeout?
                if (result.updated == null || result.updated.size() == 0) {
                    watch(watchedBranches, this);
                    observer.onProgress(makeUpdate("timeout"));
                    return;
                }

                observer.onProgress(makeUpdate("start syncing"));
                final Task.IObserver<Void, WatchJob.Result> that = this;
                sync(result.updated, new Task.IObserver<TaskUpdate, Void>() {
                    @Override
                    public void onProgress(TaskUpdate update) {
                        observer.onProgress(update);
                    }

                    @Override
                    public void onResult(Void result) {
                        // still watching?
                        if (isWatching())
                            watch(watchedBranches, that);
                        else
                            observer.onResult(null);
                    }

                    @Override
                    public void onException(Exception exception) {
                        observer.onException(exception);
                    }
                });
            }

            @Override
            public void onException(Exception exception) {
                // if we haven't stopped watching this is an real exception
                if (isWatching())
                    observer.onException(exception);
                else
                    observer.onResult(null);
            }
        };

        watch(branchInfoList, watchObserver);
    }

    public void stopWatching() {
        ongoingSyncJobs.clear();
        if (watchFunction != null) {
            watchFunction.cancel();
            watchFunction = null;
            storageDirListener = null;
        }
    }

    public void stop() {
        stopWatching();
        for (Map.Entry<String, Task<Void, ChunkStorePullJob.Result>> entry : ongoingSyncJobs.entrySet()) {
            if (entry.getValue() == null)
                continue;
            entry.getValue().cancel();
        }
    }

    static public Task<Void, ChunkStorePullJob.Result> sync(ConnectionManager connectionManager, StorageDir storageDir,
                                            Remote remote, AuthInfo authInfo,
                                            final Task.IObserver<TaskUpdate, String> observer) {
        if (storageDir.getDatabase() instanceof Repository) {
            return csSync(connectionManager, storageDir, remote, authInfo, observer);
        } else {
            throw new RuntimeException("Unsupported database");
        }
    }

    static public Task<Void, ChunkStorePullJob.Result> pull(ConnectionManager connectionManager,
                                                            final StorageDir storageDir,
                                            Remote remote, AuthInfo authInfo,
                                            final Task.IObserver<Void, ChunkStorePullJob.Result> observer) {
        if (!(storageDir.getDatabase() instanceof Repository))
            throw new RuntimeException("Unsupported database");

        final Repository repository = (Repository)storageDir.getDatabase();
        return connectionManager.submit(new ChunkStorePullJob(repository, storageDir.getCommitSignature(),
                        remote.getUser(), storageDir.getBranch()), remote, authInfo,
                new Task.IObserver<Void, ChunkStorePullJob.Result>() {
            @Override
            public void onProgress(Void aVoid) {
                observer.onProgress(aVoid);
            }

            @Override
            public void onResult(ChunkStorePullJob.Result result) {
                try {
                    HashValue tip = storageDir.getTip();
                    if (!result.pulledRev.getDataHash().isZero() && !result.oldTip.equals(tip))
                        storageDir.onTipUpdated(result.oldTip, tip);

                    observer.onResult(result);
                } catch (IOException e) {
                    observer.onException(e);
                }
            }

            @Override
            public void onException(Exception exception) {
                observer.onException(exception);
            }
        });
    }

    static private Task<Void, ChunkStorePullJob.Result> csSync(final ConnectionManager connectionManager, final StorageDir storageDir,
                                               final Remote remote,
                                               final AuthInfo authInfo,
                                               final Task.IObserver<TaskUpdate, String> observer) {
        final Repository repository = (Repository)storageDir.getDatabase();
        final String id = repository.getBranch();
        return connectionManager.submit(new ChunkStorePullJob(repository, storageDir.getCommitSignature(),
                        remote.getUser(), repository.getBranch()), remote, authInfo,
                new Task.IObserver<Void, ChunkStorePullJob.Result>() {
                    @Override
                    public void onProgress(Void aVoid) {
                        //observer.onProgress(aVoid);
                    }

                    @Override
                    public void onResult(ChunkStorePullJob.Result result) {
                        try {
                            HashValue tip = storageDir.getTip();
                            if (!result.pulledRev.getDataHash().isZero() && !result.oldTip.equals(tip))
                                storageDir.onTipUpdated(result.oldTip, tip);

                            if (repository.getHeadCommit().getBoxPointer().equals(result.pulledRev)) {
                                observer.onResult("sync after pull: " + id);
                                return;
                            }
                        } catch (IOException e) {
                            observer.onException(e);
                        }

                        // push
                        connectionManager.submit(new ChunkStorePushJob(repository, remote.getUser(),
                                        repository.getBranch()), remote, authInfo,
                                new Task.IObserver<Void, ChunkStorePushJob.Result>() {
                                    @Override
                                    public void onProgress(Void aVoid) {
                                        //observer.onProgress(aVoid);
                                    }

                                    @Override
                                    public void onResult(ChunkStorePushJob.Result result) {
                                        observer.onResult("sync after push: " + id);
                                    }

                                    @Override
                                    public void onException(Exception exception) {
                                        observer.onException(exception);
                                    }
                                });
                    }

                    @Override
                    public void onException(Exception exception) {
                        observer.onException(exception);
                    }
                });
    }

    private void sync(final BranchInfo.Location branchLocation, final int nJobs, final Task.IObserver<TaskUpdate, Void> observer) {
        final BranchInfo branchInfo = branchLocation.getBranchInfo();
        final String branch = branchInfo.getBranch();
        final StorageDir dir;
        final AuthInfo authInfo;
        final Remote remote;
        try {
            dir = userData.getStorageDir(branchInfo);
            authInfo = branchLocation.getAuthInfo(context);
            remote = branchLocation.getRemote();
        } catch (Exception e) {
            e.printStackTrace();
            observer.onException(e);
            ongoingSyncJobs.remove(branch);
            return;
        }

        Task<Void, ChunkStorePullJob.Result> job = sync(connectionManager, dir, remote, authInfo,
                    new Task.IObserver<TaskUpdate, String>() {
                @Override
                public void onProgress(TaskUpdate taskUpdate) {

                }

                @Override
                public void onResult(String message) {
                    jobFinished(branch, observer, nJobs, message);
                }

                @Override
                public void onException(Exception exception) {
                    jobFinished(branch, observer, nJobs, "exception");
                }
            });

        // only add the job if it is still in the list, e.g. when the request is sync the job is already gone
        if (ongoingSyncJobs.containsKey(branch))
            ongoingSyncJobs.put(branch, job);
    }

    private void jobFinished(String id, Task.IObserver<TaskUpdate, Void> observer, int totalNumberOfJobs,
                             String message) {
        ongoingSyncJobs.remove(id);

        int remainingJobs = ongoingSyncJobs.size();
        observer.onProgress(new TaskUpdate("Sync", totalNumberOfJobs, totalNumberOfJobs - remainingJobs, message));
        if (remainingJobs == 0)
            observer.onResult(null);
    }
}
