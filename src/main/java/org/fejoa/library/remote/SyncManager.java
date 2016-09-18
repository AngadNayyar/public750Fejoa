/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.remote;

import org.fejoa.chunkstore.HashValue;
import org.fejoa.chunkstore.Repository;
import org.fejoa.library.UserData;
import org.fejoa.library.database.ICommitSignature;
import org.fejoa.library.database.IDatabaseInterface;
import org.fejoa.library.database.JGitInterface;
import org.fejoa.library.FejoaContext;
import org.fejoa.library.Remote;
import org.fejoa.library.BranchInfo;
import org.fejoa.library.database.StorageDir;
import org.fejoa.library.support.Task;

import java.io.IOException;
import java.util.*;


public class SyncManager {
    final private FejoaContext context;
    final private UserData userData;
    final private ConnectionManager connectionManager;

    final private Remote remote;
    private Task.ICancelFunction watchFunction;
    private Collection<BranchInfo> watchedBranches;
    final private Map<String, Task.ICancelFunction> ongoingSyncJobs = new HashMap<>();

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
            for (BranchInfo branchInfo : watchedBranches) {
                if (branchInfo.getBranch().equals(id))
                    sync(branchInfo, storageIdList.size(), observer);
            }
        }
    }

    private void watch(Collection<BranchInfo> branchInfoList, Task.IObserver<Void, WatchJob.Result> observer) {
        watchedBranches = branchInfoList;
        watchFunction = connectionManager.submit(new WatchJob(context, remote.getUser(), branchInfoList),
                new ConnectionManager.ConnectionInfo(remote.getUser(), remote.getServer()),
                context.getRootAuthInfo(remote.getUser(), remote.getServer()),
                observer);
    }

    public void startWatching(final Collection<BranchInfo> branchInfoList, final Task.IObserver<TaskUpdate, Void> observer) {
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
                    watch(branchInfoList, this);
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
                        if (watchFunction != null)
                            watch(branchInfoList, that);
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
                if (watchFunction != null)
                    observer.onException(exception);
                else
                    observer.onResult(null);
            }
        };

        watch(branchInfoList, watchObserver);
    }

    public void stopWatching() {
        for (Map.Entry<String, Task.ICancelFunction> entry : ongoingSyncJobs.entrySet()) {
            if (entry.getValue() == null)
                continue;
            entry.getValue().cancel();
        }
        ongoingSyncJobs.clear();
        if (watchFunction != null) {
            watchFunction.cancel();
            watchFunction = null;
        }
    }

    private Task.ICancelFunction gitSync(final JGitInterface gitInterface, final StorageDir dir, final int nJobs,
                                         final Task.IObserver<TaskUpdate, Void> observer,
                                         final ConnectionManager.ConnectionInfo connectionInfo,
                                         final ConnectionManager.AuthInfo authInfo) {
        final String id = dir.getBranch();
        return connectionManager.submit(new GitPullJob(gitInterface.getRepository(),
                        remote.getUser(), dir.getBranch()),
                connectionInfo, authInfo,
                new Task.IObserver<Void, GitPullJob.Result>() {
                    @Override
                    public void onProgress(Void aVoid) {
                        //observer.onProgress(aVoid);
                    }

                    @Override
                    public void onResult(GitPullJob.Result result) {
                        try {
                            HashValue pullRevHash = HashValue.fromHex(result.pulledRev);
                            dir.commit();
                            HashValue base = gitInterface.getTip();
                            gitInterface.merge(pullRevHash);
                            dir.onTipUpdated(base, pullRevHash);
                            HashValue tip = dir.getTip();
                            if (tip.equals(pullRevHash)) {
                                jobFinished(id, observer, nJobs, "sync after pull: " + id);
                                return;
                            }
                        } catch (IOException e) {
                            observer.onException(e);
                        }

                        // push
                        connectionManager.submit(new GitPushJob(gitInterface.getRepository(), remote.getUser(),
                                        gitInterface.getBranch()), connectionInfo, authInfo,
                                new Task.IObserver<Void, RemoteJob.Result>() {
                                    @Override
                                    public void onProgress(Void aVoid) {
                                        //observer.onProgress(aVoid);
                                    }

                                    @Override
                                    public void onResult(RemoteJob.Result result) {
                                        jobFinished(id, observer, nJobs, "sync after push: " + id);
                                    }

                                    @Override
                                    public void onException(Exception exception) {
                                        observer.onException(exception);
                                        jobFinished(id, observer, nJobs, "exception");
                                    }
                                });
                    }

                    @Override
                    public void onException(Exception exception) {
                        observer.onException(exception);
                        jobFinished(id, observer, nJobs, "exception");
                    }
                });
    }

    static public Task.ICancelFunction sync(ConnectionManager connectionManager, StorageDir storageDir,
                                            ConnectionManager.ConnectionInfo connectionInfo,
                                            ConnectionManager.AuthInfo authInfo,
                                            final Task.IObserver<TaskUpdate, String> observer) {
        if (storageDir.getDatabase() instanceof Repository) {
            return csSync(connectionManager, storageDir, connectionInfo, authInfo, observer);
        } else {
            throw new RuntimeException("Unsupported database");
        }
    }

    static public Task.ICancelFunction pull(ConnectionManager connectionManager, final StorageDir storageDir,
                                            ConnectionManager.ConnectionInfo connectionInfo,
                                            ConnectionManager.AuthInfo authInfo,
                                            final Task.IObserver<Void, ChunkStorePullJob.Result> observer) {
        if (!(storageDir.getDatabase() instanceof Repository))
            throw new RuntimeException("Unsupported database");

        final Repository repository = (Repository)storageDir.getDatabase();
        return connectionManager.submit(new ChunkStorePullJob(repository, storageDir.getCommitSignature(),
                        connectionInfo.user, storageDir.getBranch()), connectionInfo, authInfo,
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

    static private Task.ICancelFunction csSync(final ConnectionManager connectionManager, final StorageDir storageDir,
                                               final ConnectionManager.ConnectionInfo connectionInfo,
                                               final ConnectionManager.AuthInfo authInfo,
                                               final Task.IObserver<TaskUpdate, String> observer) {
        final Repository repository = (Repository)storageDir.getDatabase();
        final String id = repository.getBranch();
        return connectionManager.submit(new ChunkStorePullJob(repository, storageDir.getCommitSignature(),
                        connectionInfo.user, repository.getBranch()), connectionInfo, authInfo,
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
                        connectionManager.submit(new ChunkStorePushJob(repository, connectionInfo.user,
                                        repository.getBranch()), connectionInfo, authInfo,
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

    private void sync(final BranchInfo branchInfo, final int nJobs, final Task.IObserver<TaskUpdate, Void> observer) {
        final String branch = branchInfo.getBranch();
        final StorageDir dir;
        try {
            dir = userData.getStorageDir(branchInfo);
        } catch (Exception e) {
            e.printStackTrace();
            observer.onException(e);
            ongoingSyncJobs.remove(branch);
            return;
        }

        final ConnectionManager.ConnectionInfo connectionInfo = new ConnectionManager.ConnectionInfo(remote.getUser(),
                remote.getServer());
        final ConnectionManager.AuthInfo authInfo = context.getRootAuthInfo(remote.getUser(), remote.getServer());
        Task.ICancelFunction job = sync(connectionManager, dir, connectionInfo, authInfo,
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
