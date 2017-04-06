/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.remote;

import java8.util.concurrent.CompletableFuture;
import java8.util.function.BiFunction;
import java8.util.function.Consumer;
import java8.util.function.Function;
import java8.util.function.Supplier;
import org.fejoa.library.Remote;
import org.fejoa.library.support.Task;

import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executor;


public class ConnectionManager {
    static public class UserAuthInfo {
        final public String userName;
        final public AuthInfo authInfo;

        public UserAuthInfo(String userName, AuthInfo authInfo) {
            this.userName = userName;
            this.authInfo = authInfo;
        }
    }

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

        public void addToken(String targetUser, String server, String token) {
            String key = makeKey(targetUser, server);
            synchronized (this) {
                HashSet<String> tokenMap = authMap.get(key);
                if (tokenMap == null) {
                    tokenMap = new HashSet<>();
                    authMap.put(key, tokenMap);
                }
                tokenMap.add(token);
            }
        }

        public boolean removeToken(String targetUser, String server, String token) {
            String key = makeKey(targetUser, server);
            synchronized (this) {
                HashSet<String> tokenMap = authMap.get(key);
                if (tokenMap == null)
                    return false;
                return tokenMap.remove(key);
            }
        }

        public boolean hasToken(String targetUser, String server, String token) {
            String key = makeKey(targetUser, server);
            synchronized (this) {
                HashSet<String> tokenMap = authMap.get(key);
                if (tokenMap == null)
                    return false;
                return tokenMap.contains(token);
            }
        }
    }

    //final private CookieStore cookieStore = new BasicCookieStore();
    final private TokenManager tokenManager = new TokenManager();
    private Executor startScheduler = new Task.NewThreadScheduler();
    private Executor observerScheduler = new Task.CurrentThreadScheduler();

    public ConnectionManager() {
        if (CookieHandler.getDefault() == null)
            CookieHandler.setDefault(new CookieManager(null, CookiePolicy.ACCEPT_ALL));
    }

    public void setStartScheduler(Executor startScheduler) {
        this.startScheduler = startScheduler;
    }

    public void setObserverScheduler(Executor scheduler) {
        this.observerScheduler = scheduler;
    }

    public <Progress, T extends RemoteJob.Result> Task<Progress, T> submit(final JsonRemoteJob<T> job,
                                                                    Remote remote,
                                                                    final AuthInfo authInfo,
                                                                    final Task.IObserver<Progress, T> observer) {
        return submit(job, remote.getServer(), Collections.singletonList(new UserAuthInfo(remote.getUser(), authInfo)), observer);
    }

    public <Progress, T extends RemoteJob.Result> Task<Progress, T> submit(final JsonRemoteJob<T> job,
                                                                           String url,
                                                                           final Collection<UserAuthInfo> authInfos,
                                                                           final Task.IObserver<Progress, T> observer) {
        JobTask<Progress, T> jobTask = new JobTask<>(tokenManager, job, url, authInfos);
        jobTask.setStartScheduler(startScheduler).setObserverScheduler(observerScheduler).start(observer);
        return jobTask;
    }

    public <T extends RemoteJob.Result> CompletableFuture<T> submit(final JsonRemoteJob<T> job, Remote remote,
                                                                    final AuthInfo authInfo) {
        return submit(job, remote.getServer(), Collections.singletonList(new UserAuthInfo(remote.getUser(), authInfo)));
    }

    /**
     * The returned CompletableFuture ignores the observerScheduler. The future must be observed manually on the
     * observerScheduler.
     */
    public <T extends RemoteJob.Result> CompletableFuture<T> submit(final JsonRemoteJob<T> job, String url,
                                                                    final Collection<UserAuthInfo> authInfos) {
        final JobRunner<T> jobRunner = new JobRunner<>(tokenManager, job, url, authInfos);
        final CompletableFuture<T> result = new CompletableFuture<>();
        startScheduler.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final T value = jobRunner.run(JobRunner.MAX_RETRIES);
                    result.complete(value);
                } catch (Exception e) {
                    result.completeExceptionally(e);
                }
            }
        });

        result.exceptionally(new Function<Throwable, T>() {
            @Override
            public T apply(Throwable throwable) {
                if (throwable instanceof CancellationException)
                    jobRunner.cancelJob();
                return null;
            }
        });
        return result;
    }

    static class JobRunner<T extends RemoteJob.Result> {
        final private TokenManager tokenManager;
        final private JsonRemoteJob<T> job;
        final private String url;
        final private Collection<UserAuthInfo> authInfos;

        private IRemoteRequest remoteRequest;
        private boolean isCanceled = false;

        final static private int MAX_RETRIES = 2;

        public JobRunner(TokenManager tokenManager, final JsonRemoteJob<T> job, String url,
                       final Collection<UserAuthInfo> authInfos) {
            super();

            this.tokenManager = tokenManager;
            this.job = job;
            this.url = url;
            this.authInfos = authInfos;
        }

        public T run(int retryCount) throws Exception {
            if (retryCount > MAX_RETRIES)
                throw new Exception("too many retries");
            IRemoteRequest remoteRequest = getRemoteRequest(url);
            setCurrentRemoteRequest(remoteRequest);

            Collection<UserAuthInfo> missingAccess;
            // synchronized: Don't send multiple auth requests at the same time; they could interfere, i.e. when they
            // are stateful.
            synchronized (tokenManager) {
                missingAccess = getMissingAccess(url, authInfos);
                if (missingAccess.size() > 0) {
                    remoteRequest = getAuthRequest(remoteRequest, url, missingAccess);
                    setCurrentRemoteRequest(remoteRequest);
                }
            }

            T result = runJob(remoteRequest, job);
            if (result.status == Errors.ACCESS_DENIED) {
                for (UserAuthInfo userAuthInfo : authInfos) {
                    AuthInfo authInfo = userAuthInfo.authInfo;
                    // TODO: be more selective and only remove failed auth infos
                    if (authInfo.authType == AuthInfo.PASSWORD)
                        tokenManager.removeRootAccess(userAuthInfo.userName, url);
                    if (authInfo.authType == AuthInfo.TOKEN) {
                        tokenManager.removeToken(userAuthInfo.userName, url,
                                ((AuthInfo.Token) authInfo).token.getId());
                    }
                }
                if (missingAccess.size() < authInfos.size()) {
                    // if we had access try again
                    return run(retryCount + 1);
                }
            }
            return result;
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
                isCanceled = true;
                if (remoteRequest != null)
                    remoteRequest.cancel();
            }
        }

        private void setCurrentRemoteRequest(IRemoteRequest remoteRequest) throws Exception {
            synchronized (this) {
                if (remoteRequest != null && isCanceled) {
                    this.remoteRequest = null;
                    throw new Exception("JobTask canceled");
                }

                this.remoteRequest = remoteRequest;
            }
        }

        private boolean hasAccess(String url, UserAuthInfo userAuthInfo) {
            AuthInfo authInfo = userAuthInfo.authInfo;
            if (authInfo.authType == AuthInfo.PLAIN)
                return true;
            if (authInfo.authType == AuthInfo.PASSWORD)
                return tokenManager.hasRootAccess(userAuthInfo.userName, url);
            if (authInfo.authType == AuthInfo.TOKEN)
                return tokenManager.hasToken(userAuthInfo.userName, url, ((AuthInfo.Token)authInfo).token.getId());
            return false;
        }

        private Collection<UserAuthInfo> getMissingAccess(String url, Collection<UserAuthInfo> authInfos) {
            List<UserAuthInfo> missingAccess = new ArrayList<>();
            for (UserAuthInfo authInfo : authInfos) {
                if (!hasAccess(url, authInfo))
                    missingAccess.add(authInfo);
            }
            return missingAccess;
        }

        private IRemoteRequest getAuthRequest(final IRemoteRequest remoteRequest, final String url,
                                              final Collection<UserAuthInfo> authInfos) throws Exception {
            for (UserAuthInfo userAuthInfo : authInfos) {
                AuthInfo authInfo = userAuthInfo.authInfo;
                RemoteJob.Result result;
                if (authInfo.authType == AuthInfo.PASSWORD) {
                    AuthInfo.Password passwordAuth = (AuthInfo.Password) authInfo;
                    result = runJob(remoteRequest, new LoginJob(passwordAuth.context, userAuthInfo.userName,
                            passwordAuth.password));
                    tokenManager.addRootAccess(userAuthInfo.userName, url);
                } else if (authInfo.authType == AuthInfo.TOKEN) {
                    AuthInfo.Token tokenAuth = (AuthInfo.Token) authInfo;
                    result = runJob(remoteRequest, new AccessRequestJob(userAuthInfo.userName, tokenAuth.token));
                    if (result.status == Errors.OK)
                        tokenManager.addToken(userAuthInfo.userName, url, tokenAuth.token.getId());
                } else
                    throw new Exception("unknown auth type");

                if (result.status != Errors.DONE)
                    throw new Exception(result.message);
            }

            return getRemoteRequest(url);
        }

        private IRemoteRequest getRemoteRequest(String url) {
            return new HTMLRequest(url);
        }
    }

    static private class JobTask<Progress, T extends RemoteJob.Result> extends Task<Progress, T>{
        final private JobRunner<T> jobRunner;

        public JobTask(TokenManager tokenManager, final JsonRemoteJob<T> job, String url,
                       final Collection<UserAuthInfo> authInfos) {
            super();

            this.jobRunner = new JobRunner<>(tokenManager, job, url, authInfos);

            setTaskFunction(new ITaskFunction<Progress, T>() {
                @Override
                public void run(Task<Progress, T> task) throws Exception {
                    final T value = jobRunner.run(JobRunner.MAX_RETRIES);
                    task.onResult(value);
                }

                @Override
                public void cancel() {
                    jobRunner.cancelJob();
                }
            });
        }


    }
}
