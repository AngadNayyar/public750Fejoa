/*
 * Copyright 2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.remote;

import org.fejoa.library.ContactPrivate;
import rx.Observable;
import rx.Observer;
import rx.Subscription;
import rx.subscriptions.Subscriptions;
import rx.util.functions.Func1;

import java.util.*;


class SharedConnection {
    private String server;
    private Map<ContactPrivate, RoleManager> contactRoles = new HashMap<>();

    public SharedConnection(String server) {
        this.server = server;
    }

    public RoleManager getRoleManager(ContactPrivate myself) {
        if (!contactRoles.containsKey(myself)) {
            RoleManager roleManager = new RoleManager(this);
            contactRoles.put(myself, roleManager);
            return roleManager;
        }
        return contactRoles.get(myself);
    }

    Observable<IRemoteRequest> getRemoteRequest() {
        return Observable.create(new Observable.OnSubscribeFunc<IRemoteRequest>() {
            @Override
            public Subscription onSubscribe(Observer<? super IRemoteRequest> observer) {
                String fullAddress = "http://" + server + "/php_server/portal.php";
                HTMLRequest remoteRequest = new HTMLRequest(fullAddress);

                observer.onNext(remoteRequest);
                observer.onCompleted();
                return Subscriptions.empty();
            }
        });
    }
}

class RoleManager {
    final private SharedConnection sharedConnection;
    final private RequestQueue requestQueue;
    final private ServerWatcher serverWatcher = new ServerWatcher();
    final private List<String> roles = Collections.synchronizedList(new ArrayList<String>());

    public RoleManager(SharedConnection sharedConnection) {
        this.sharedConnection = sharedConnection;
        requestQueue = new RequestQueue(null);
    }

    public void setListener(ServerWatcher.IListener listener) {
        serverWatcher.setListener(listener);
    }

    public void startWatching(RemoteStorageLink link) {
        serverWatcher.addLink(link);
        requestQueue.setIdleJob(serverWatcher);
    }

    private boolean hasRole(String role) {
        synchronized (roles) {
            return roles.contains(role);
        }
    }

    private void addRole(String role) {
        synchronized (roles) {
            roles.add(role);
        }
    }

    public <T> Observable<T> sendQueued(final ConnectionInfo info, final Func1<IRemoteRequest, Observable<T>> job) {
        return Observable.create(new Observable.OnSubscribeFunc<T>() {
            @Override
            public Subscription onSubscribe(Observer<? super T> observer) {
                Observable<T> observable = sharedConnection.getRemoteRequest().mapMany(
                        new Func1<IRemoteRequest, Observable<IRemoteRequest>>() {
                            @Override
                            public Observable<IRemoteRequest> call(IRemoteRequest remoteRequest) {
                                return login(remoteRequest, info);
                            }
                        }).mapMany(job);
                return requestQueue.queue(observable).subscribe(observer);
            }
        });
    }

    public Observable<IRemoteRequest> getPreparedRemoteRequest(final ConnectionInfo info) {
        return Observable.create(new Observable.OnSubscribeFunc<IRemoteRequest>() {
            @Override
            public Subscription onSubscribe(Observer<? super IRemoteRequest> observer) {
                Observable<IRemoteRequest> observable = sharedConnection.getRemoteRequest().mapMany(
                        new Func1<IRemoteRequest, Observable<IRemoteRequest>>() {
                            @Override
                            public Observable<IRemoteRequest> call(IRemoteRequest remoteRequest) {
                                return login(remoteRequest, info);
                            }
                        });
                return observable.subscribe(observer);
            }
        });
    }

    public <T> Observable<T> queueJob(Observable<T> observable) {
        return requestQueue.queue(observable);
    }

    private Observable<IRemoteRequest> login(final IRemoteRequest remoteRequest, final ConnectionInfo info) {
        final String role = info.myself.getUid() + ":" + info.serverUser;
        if (hasRole(role))
            return Observable.just(remoteRequest);

        return Observable.create(new Observable.OnSubscribeFunc<IRemoteRequest>() {
            @Override
            public Subscription onSubscribe(final Observer<? super IRemoteRequest> observer) {
                if (hasRole(role)) {
                    observer.onNext(remoteRequest);
                    observer.onCompleted();
                    return Subscriptions.empty();
                }
                SignatureAuthentication authentication = new SignatureAuthentication(info);
                authentication.auth(remoteRequest).subscribe(new Observer<Boolean>() {
                    @Override
                    public void onCompleted() {
                        observer.onCompleted();
                    }

                    @Override
                    public void onError(Throwable e) {
                        observer.onError(e);
                    }

                    @Override
                    public void onNext(Boolean signedIn) {
                        if (signedIn) {
                            addRole(role);
                            observer.onNext(remoteRequest);
                        } else
                            observer.onError(new Exception("failed to log in"));
                    }
                });
                return Subscriptions.empty();
            }
        });
    }
}

public class ConnectionManager {
    static private ConnectionManager connectionManager = null;
    static public ConnectionManager get() {
        if (connectionManager != null)
            return connectionManager;
        connectionManager = new ConnectionManager();
        return connectionManager;
    }

    final private Map<String, SharedConnection> servers = new HashMap<>();

    private ConnectionManager() {
    }

    private SharedConnection getContactRoles(String server) {
        SharedConnection sharedConnection;
        if (!servers.containsKey(server)) {
            sharedConnection = new SharedConnection(server);
            servers.put(server, sharedConnection);
            return sharedConnection;
        }
        return servers.get(server);
    }

    private RoleManager getRoleManager(ConnectionInfo info) {
        SharedConnection sharedConnection = getContactRoles(info.server);
        return sharedConnection.getRoleManager(info.myself);
    }

    public <T> Observable<T> sendQueued(ConnectionInfo info, Func1<IRemoteRequest, Observable<T>> job) {
        return getRoleManager(info).sendQueued(info, job);
    }

    public Observable<IRemoteRequest> getPreparedRemoteRequest(ConnectionInfo info) {
        return getRoleManager(info).getPreparedRemoteRequest(info);
    }

    public <T> Observable<T> queueJob(ConnectionInfo info, Observable<T> observable) {
        return getRoleManager(info).queueJob(observable);
    }

    public RemoteConnection getConnection(ConnectionInfo info) {
        return new RemoteConnection(this, info);
    }

    public void setWatchListener(ConnectionInfo info, ServerWatcher.IListener listener) {
        getRoleManager(info).setListener(listener);
    }

    public void startWatching(RemoteStorageLink link) {
        ConnectionInfo info = link.getConnectionInfo();
        getRoleManager(info).startWatching(link);
    }
}

