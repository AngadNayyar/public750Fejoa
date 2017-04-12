/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.remote;

import org.fejoa.chunkstore.CommitBox;
import org.fejoa.chunkstore.HashValue;
import org.fejoa.chunkstore.Repository;
import org.fejoa.chunkstore.sync.RequestHandler;
import org.fejoa.library.Remote;
import org.fejoa.library.UserData;
import org.fejoa.library.database.DatabaseDiff;
import org.fejoa.library.database.StorageDir;
import org.fejoa.library.FejoaContext;
import org.fejoa.library.BranchInfo;
import org.fejoa.library.support.Task;

import java.io.IOException;
import java.util.*;


public class SyncManager {
    private static class Entry {
        final public List<BranchInfo.Location> branches = new ArrayList<>();
        final public SyncManagerServerWorker syncManager;

        public Entry(SyncManagerServerWorker syncManager) {
            this.syncManager = syncManager;
        }
    }

    final private UserData userData;
    final private ConnectionManager connectionManager;
    final private Task.IObserver<TaskUpdate, Void> observer;
    final private Map<String, Entry> serverWorkerMap = new HashMap<>();

    public SyncManager(UserData userData, ConnectionManager connectionManager,
                       Task.IObserver<TaskUpdate, Void> observer) {
        this.userData = userData;
        this.connectionManager = connectionManager;
        this.observer = observer;
    }

    private Entry getServerEntry(String server) {
        Entry entry = serverWorkerMap.get(server);
        if (entry != null)
            return entry;
        entry = new Entry(new SyncManagerServerWorker(userData, connectionManager, server));
        serverWorkerMap.put(server, entry);
        return entry;
    }

    private void putLocation(List<BranchInfo.Location> entries, BranchInfo.Location location) throws IOException {
        // remove existing entry
        Iterator<BranchInfo.Location> it = entries.iterator();

        String remoteId = location.getRemoteId();
        while (it.hasNext()) {
            BranchInfo.Location entry = it.next();
            try {
                if (entry.getBranchInfo().getBranch().equals(location.getBranchInfo().getBranch())
                        && entry.getRemoteId().equals(remoteId)) {
                    it.remove();
                    break;
                }
            } catch (IOException e) {
                continue;
            }
        }

        entries.add(location);
    }

    public void addWatching(Collection<BranchInfo.Location> branches) throws IOException {
        HashSet<Entry> dirtyEntries = new HashSet<>();
        for (BranchInfo.Location location : branches) {
            // sort entries by server
            String server = location.getRemote().getServer();
            Entry entry = getServerEntry(server);
            putLocation(entry.branches, location);
            dirtyEntries.add(entry);
        }

        // update sync workers
        for (Entry entry : dirtyEntries)
            entry.syncManager.startWatching(entry.branches, observer);
    }

    public void stop() {
        for (Map.Entry<String, Entry> entry : serverWorkerMap.entrySet())
            entry.getValue().syncManager.stopWatching();
        serverWorkerMap.clear();
    }

    static public Task<Void, ChunkStorePullJob.Result> sync(ConnectionManager connectionManager, StorageDir storageDir,
                                                            Remote remote, AuthInfo authInfo,
                                                            final Task.IObserver<TaskUpdate, String> observer) {
        return Syncer.sync(connectionManager, storageDir, remote, authInfo, observer);
    }
/*
    static public Task<Void, WatchJob.Result> getRemoteTip(FejoaContext context, ConnectionManager connectionManager,
                                                final StorageDir storageDir,
                                                Remote remote, AuthInfo authInfo,
                                                final Task.IObserver<Void, WatchJob.Result> observer) {
        return connectionManager.submit(new WatchJob(context, watchedBranches), remote,
                authInfo, observer);
    }*/

    static public Task<Void, ChunkStorePullJob.Result> pull(ConnectionManager connectionManager,
                                                            final StorageDir storageDir,
                                                            Remote remote, AuthInfo authInfo,
                                                            final Task.IObserver<Void, ChunkStorePullJob.Result> observer) {
        if (!(storageDir.getDatabase() instanceof Repository))
            throw new RuntimeException("Unsupported database");

        final Repository repository = (Repository) storageDir.getDatabase();
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
}

class Watcher {
    private Task watchFunction;
    private Collection<BranchInfo.Location> watchedBranches;

    public Watcher(Collection<BranchInfo.Location> branchInfoList) {
        this.watchedBranches = branchInfoList;
    }

    public boolean isWatching() {
        return watchFunction != null;
    }

    public void startWatching(FejoaContext context, ConnectionManager connectionManager, String server,
                              final Task.IObserver<Void, WatchJob.Result> observer) {
        assert watchFunction == null;
        Map<String, ConnectionManager.UserAuthInfo> authInfos = new HashMap<>();
        for (BranchInfo.Location location : watchedBranches) {
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

        watchFunction = connectionManager.submit(new WatchJob(context, watchedBranches), server,
                authInfos.values(), observer);
    }

    public void stopWatching() {
        if (!isWatching())
            return;

        watchFunction.cancel();
        watchFunction = null;
    }

    public Collection<BranchInfo.Location> getWatchedBranches() {
        return watchedBranches;
    }

    public Collection<BranchInfo.Location> getBranchLocations(List<WatchJob.BranchLogTip> storageIdList) {
        List<BranchInfo.Location> list = new ArrayList<>();
        for (WatchJob.BranchLogTip logTip : storageIdList) {
            for (BranchInfo.Location branchLocation : watchedBranches) {
                if (branchLocation.getBranchInfo().getBranch().equals(logTip.branch))
                    list.add(branchLocation);
            }
        }
        return list;
    }
}

class Syncer {
    final UserData userData;
    final ConnectionManager connectionManager;
    final private Map<String, Task<Void, ChunkStorePullJob.Result>> ongoingSyncJobs = new HashMap<>();

    public Syncer(UserData userData, ConnectionManager connectionManager) {
        this.userData = userData;
        this.connectionManager = connectionManager;
    }

    public boolean isSyncing() {
        return ongoingSyncJobs.size() != 0;
    }

    public void sync(Collection<BranchInfo.Location> syncBranches, final Task.IObserver<TaskUpdate, Void> observer) {
        if (isSyncing())
            return;

        // add the ids in case the job finishes before submit returns, e.g. if executed immediately
        for (BranchInfo.Location location : syncBranches) {
            String branchId = location.getBranchInfo().getBranch();
            if (!ongoingSyncJobs.containsKey(branchId)) {
                ongoingSyncJobs.put(branchId, null);
                sync(location, syncBranches.size(), observer);
            }
        }
    }

    public void stop() {
        for (Map.Entry<String, Task<Void, ChunkStorePullJob.Result>> entry : ongoingSyncJobs.entrySet()) {
            if (entry.getValue() == null)
                continue;
            entry.getValue().cancel();
        }
    }

    private void sync(final BranchInfo.Location branchLocation, final int nJobs, final Task.IObserver<TaskUpdate, Void> observer) {
        final BranchInfo branchInfo = branchLocation.getBranchInfo();
        final String branch = branchInfo.getBranch();
        final StorageDir dir;
        final AuthInfo authInfo;
        final Remote remote;
        try {
            dir = userData.getStorageDir(branchInfo);
            authInfo = branchLocation.getAuthInfo(userData.getContext());
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
                        jobFinished(branch, observer, nJobs, "exception: " + exception.getMessage());
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

    static public Task<Void, ChunkStorePullJob.Result> sync(ConnectionManager connectionManager, StorageDir storageDir,
                                                             Remote remote, AuthInfo authInfo,
                                                             final Task.IObserver<TaskUpdate, String> observer) {
        if (storageDir.getDatabase() instanceof Repository) {
            return csSync(connectionManager, storageDir, remote, authInfo, observer);
        } else {
            throw new RuntimeException("Unsupported database");
        }
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
                        if (result.status == RequestHandler.Result.ERROR.getValue()) {
                            observer.onResult("Failed to pull: " + result.message);
                            return;
                        }
                        try {
                            CommitBox headCommit = repository.getHeadCommit();
                            if (headCommit == null) {
                                assert result.pulledRev.getDataHash().isZero();
                                observer.onResult("Nothing to sync, tips are ZERO: " + id);
                                return;
                            }

                            HashValue tip = storageDir.getTip();
                            if (!result.pulledRev.getDataHash().isZero() && !result.oldTip.equals(tip))
                                storageDir.onTipUpdated(result.oldTip, tip);

                            if (headCommit.getRef().equals(result.pulledRev)) {
                                observer.onResult("Sync after pull: " + id);
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
                        exception.printStackTrace();
                        observer.onException(exception);
                    }
                });
    }
}

class LocalBranchMonitor {
    final private UserData userData;
    private Map<BranchInfo.Location, StorageDir> branchInfoMap = null;
    private StorageDir.IListener currentListener;

    public LocalBranchMonitor(UserData userData) {
        this.userData = userData;
    }

    public void startWatching(Collection<BranchInfo.Location> branchInfoList, StorageDir.IListener listener) {
        stopWatching();
        branchInfoMap = new HashMap<>();
        currentListener = listener;
        for (BranchInfo.Location location : branchInfoList) {
            try {
                StorageDir storageDir = userData.getStorageDir(location.getBranchInfo());
                storageDir.addListener(currentListener);
                branchInfoMap.put(location, storageDir);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void stopWatching() {
        if (branchInfoMap == null)
            return;

        for (Map.Entry<BranchInfo.Location, StorageDir> entry : branchInfoMap.entrySet())
            entry.getValue().removeListener(currentListener);

        branchInfoMap = null;
    }
}

class SyncManagerServerWorker {
    final private FejoaContext context;
    final private ConnectionManager connectionManager;

    final private String server;

    private boolean isWatching = false;
    final private List<BranchInfo.Location> branchInfoList = new ArrayList<>();
    private Watcher currentWatcher;
    final private LocalBranchMonitor localBranchMonitor;
    final private Syncer syncer;

    public SyncManagerServerWorker(UserData userData, ConnectionManager connectionManager, String server) {
        this.context = userData.getContext();
        this.connectionManager = connectionManager;
        this.server = server;

        localBranchMonitor = new LocalBranchMonitor(userData);
        syncer = new Syncer(userData, connectionManager);
    }

    private void listenToBranches(Collection<BranchInfo.Location> branchInfoList,
                                  Task.IObserver<TaskUpdate, Void> observer) {
        localBranchMonitor.startWatching(branchInfoList, createStorageWatchListener(observer));
    }

    private StorageDir.IListener createStorageWatchListener(final Task.IObserver<TaskUpdate, Void> observer) {
        return new StorageDir.IListener() {
            @Override
            public void onTipChanged(DatabaseDiff diff) {
                reNewWatching(observer);
            }
        };
    }

    private void reNewWatching(final Task.IObserver<TaskUpdate, Void> observer) {
        if (syncer.isSyncing() || !isWatching())
            return;
        if (currentWatcher != null)
            currentWatcher.stopWatching();
        currentWatcher = new Watcher(branchInfoList);
        final Watcher activeWatcher = currentWatcher;

        currentWatcher.startWatching(context, connectionManager, server, new Task.IObserver<Void, WatchJob.Result>() {
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
                    reNewWatching(observer);
                    observer.onProgress(makeUpdate("timeout"));
                    return;
                }

                observer.onProgress(makeUpdate("start syncing"));
                syncer.sync(activeWatcher.getBranchLocations(result.updated),
                        new Task.IObserver<TaskUpdate, Void>() {
                            @Override
                            public void onProgress(TaskUpdate update) {
                                observer.onProgress(update);
                            }

                            @Override
                            public void onResult(Void result) {
                                // still watching?
                                if (isWatching())
                                    reNewWatching(observer);
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
                if (activeWatcher.isWatching())
                    observer.onException(exception);
                else
                    observer.onResult(null);
            }
        });
    }

    public void startWatching(Collection<BranchInfo.Location> branchInfoList,
                              final Task.IObserver<TaskUpdate, Void> observer) {
        this.branchInfoList.clear();
        this.branchInfoList.addAll(branchInfoList);
        isWatching = true;
        if (currentWatcher != null && currentWatcher.isWatching())
            currentWatcher.stopWatching();
        listenToBranches(branchInfoList, observer);
        reNewWatching(observer);
    }

    public void stopWatching() {
        isWatching = false;
        if (currentWatcher != null) {
            currentWatcher.stopWatching();
            currentWatcher = null;
        }
        localBranchMonitor.stopWatching();
    }

    public boolean isWatching() {
        return isWatching;
    }

    public void stop() {
        stopWatching();
        localBranchMonitor.stopWatching();
        syncer.stop();
    }
}
