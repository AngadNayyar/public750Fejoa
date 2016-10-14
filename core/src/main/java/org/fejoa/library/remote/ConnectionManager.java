/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.remote;

import org.fejoa.library.Remote;
import org.fejoa.library.support.Task;

import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;


public class ConnectionManager {
    /**
     * Maintains the access tokens gained for different target users.
     *
     * The target user is identified by a string such as user@server.
     *
     * Must be thread safe to be accessed from a task job.
     */
    static class TokenManager {
        final private HashSet<String> rootAccess = new HashSet<>();
        final private Map<String, HashSet<String>> authMap = new HashMap<>();

        static private String makeKey(String serverUser, String server) {
            return serverUser + "@" + server;
        }

        public boolean hasRootAccess(String serverUser, String server) {
            synchronized (this) {
                return rootAccess.contains(makeKey(serverUser, server));
            }
        }

        public boolean addRootAccess(String serverUser, String server) {
            synchronized (this) {
                return rootAccess.add(makeKey(serverUser, server));
            }
        }

        public boolean removeRootAccess(String serverUser, String server) {
            synchronized (this) {
                return rootAccess.remove(makeKey(serverUser, server));
            }
        }

        public void addToken(String targetUser, String token) {
            synchronized (this) {
                HashSet<String> tokenMap = authMap.get(targetUser);
                if (tokenMap == null) {
                    tokenMap = new HashSet<>();
                    authMap.put(targetUser, tokenMap);
                }
                tokenMap.add(token);
            }
        }

        public boolean removeToken(String targetUser, String token) {
            synchronized (this) {
                HashSet<String> tokenMap = authMap.get(targetUser);
                if (tokenMap == null)
                    return false;
                return tokenMap.remove(token);
            }
        }

        public boolean hasToken(String targetUser, String token) {
            synchronized (this) {
                HashSet<String> tokenMap = authMap.get(targetUser);
                if (tokenMap == null)
                    return false;
                return tokenMap.contains(token);
            }
        }
    }

    //final private CookieStore cookieStore = new BasicCookieStore();
    final private TokenManager tokenManager = new TokenManager();
    private Task.IScheduler startScheduler = new Task.NewThreadScheduler();
    private Task.IScheduler observerScheduler = new Task.CurrentThreadScheduler();

    public ConnectionManager() {
        if (CookieHandler.getDefault() == null)
            CookieHandler.setDefault(new CookieManager(null, CookiePolicy.ACCEPT_ALL));
    }

    public void setStartScheduler(Task.IScheduler startScheduler) {
        this.startScheduler = startScheduler;
    }

    public void setObserverScheduler(Task.IScheduler scheduler) {
        this.observerScheduler = scheduler;
    }

    public <Progress, T extends RemoteJob.Result> Task<Progress, T> submit(final JsonRemoteJob<T> job,
                                                                    Remote remote,
                                                                    final AuthInfo authInfo,
                                                                    final Task.IObserver<Progress, T> observer) {
        JobTask<Progress, T> jobTask = new JobTask<>(tokenManager, job, remote, authInfo);
        jobTask.setStartScheduler(startScheduler).setObserverScheduler(observerScheduler).start(observer);
        return jobTask;
    }

    static private class JobTask<Progress, T extends RemoteJob.Result> extends Task<Progress, T>{
        final private TokenManager tokenManager;
        final private JsonRemoteJob<T> job;
        final private Remote remote;
        final private AuthInfo authInfo;

        private IRemoteRequest remoteRequest;

        public JobTask(TokenManager tokenManager, final JsonRemoteJob<T> job, Remote remote,
                       final AuthInfo authInfo) {
            super();

            this.tokenManager = tokenManager;
            this.job = job;
            this.remote = remote;
            this.authInfo = authInfo;

            setTaskFunction(new ITaskFunction<Progress, T>() {
                @Override
                public void run(Task<Progress, T> task) throws Exception {
                    JobTask.this.run(0);
                }

                @Override
                public void cancel() {
                    cancelJob();
                }
            });
        }

        final static private int MAX_RETRIES = 2;
        private void run(int retryCount) throws Exception {
            if (retryCount > MAX_RETRIES)
                throw new Exception("too many retries");
            IRemoteRequest remoteRequest = getRemoteRequest(remote);
            setCurrentRemoteRequest(remoteRequest);

            boolean hasAccess = hasAccess(remote, authInfo);
            if (!hasAccess) {
                remoteRequest = getAuthRequest(remoteRequest, remote, authInfo);
                setCurrentRemoteRequest(remoteRequest);
            }

            T result = runJob(remoteRequest, job);
            if (result.status == Errors.ACCESS_DENIED) {
                if (authInfo.authType == AuthInfo.PASSWORD)
                    tokenManager.removeRootAccess(remote.getUser(), remote.getServer());
                if (authInfo.authType == AuthInfo.TOKEN) {
                    tokenManager.removeToken(remote.getUser(), ((AuthInfo.Token)authInfo).token.getId());
                } if (hasAccess) {
                    // if we had access try again
                    run(retryCount + 1);
                    return;
                }
            }
            onResult(result);
        }

        private T runJob(final IRemoteRequest remoteRequest, final JsonRemoteJob<T> job) throws Exception {
            try {
                return JsonRemoteJob.run(job, remoteRequest);
            } finally {
                remoteRequest.close();
                setCurrentRemoteRequest(null);
            }
        }

        private void cancelJob() {
            synchronized (this) {
                if (remoteRequest != null)
                    remoteRequest.cancel();
            }
        }

        private void setCurrentRemoteRequest(IRemoteRequest remoteRequest) throws Exception {
            synchronized (this) {
                if (isCanceled()) {
                    this.remoteRequest = null;
                    throw new Exception("canceled");
                }

                this.remoteRequest = remoteRequest;
            }
        }

        private boolean hasAccess(Remote remote, AuthInfo authInfo) {
            if (authInfo.authType == AuthInfo.PLAIN)
                return true;
            if (authInfo.authType == AuthInfo.PASSWORD)
                return tokenManager.hasRootAccess(remote.getUser(), remote.getServer());
            if (authInfo.authType == AuthInfo.TOKEN)
                return tokenManager.hasToken(remote.getUser(), ((AuthInfo.Token)authInfo).token.getId());
            return false;
        }

        private IRemoteRequest getAuthRequest(final IRemoteRequest remoteRequest, final Remote remote,
                                              final AuthInfo authInfo) throws Exception {
            RemoteJob.Result result;
            if (authInfo.authType == AuthInfo.PASSWORD) {
                AuthInfo.Password passwordAuth = (AuthInfo.Password)authInfo;
                result = runJob(remoteRequest, new RootLoginJob(remote.getUser(), passwordAuth.password));
                tokenManager.addRootAccess(remote.getUser(), remote.getServer());
            } else if (authInfo.authType == AuthInfo.TOKEN) {
                AuthInfo.Token tokenAuth = (AuthInfo.Token)authInfo;
                result = runJob(remoteRequest, new AccessRequestJob(remote.getUser(), tokenAuth.token));
                tokenManager.addToken(remote.getUser(), tokenAuth.token.getId());
            } else
                throw new Exception("unknown auth type");

            if (result.status == Errors.DONE)
                return getRemoteRequest(remote);

            throw new Exception(result.message);
        }

        private IRemoteRequest getRemoteRequest(Remote remote) {
            return new HTMLRequest(remote.getServer());
        }
    }
}
