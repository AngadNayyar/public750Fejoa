package org.fejoa;

import junit.framework.TestCase;
import org.fejoa.chunkstore.Repository;
import org.fejoa.library.FejoaContext;
import org.fejoa.library.Remote;
import org.fejoa.library.UserData;
import org.fejoa.library.database.ICommitSignature;
import org.fejoa.library.database.StorageDir;
import org.fejoa.library.remote.*;
import org.fejoa.library.support.StorageLib;
import org.fejoa.library.support.Task;
import org.fejoa.server.JettyServer;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.fejoa.server.JettyServer.DEFAULT_PORT;


public class JettyTest extends TestCase {
    final static String TEST_DIR = "jettyTest";
    final static String SERVER_TEST_DIR = TEST_DIR + "/Server";

    final List<String> cleanUpDirs = new ArrayList<String>();
    JettyServer server;
    Remote remote;
    AuthInfo authInfo;
    Task.IObserver<Void, RemoteJob.Result> observer;
    ConnectionManager connectionManager;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        cleanUpDirs.add(TEST_DIR);

        server = new JettyServer(SERVER_TEST_DIR);
        server.setDebugNoAccessControl(true);
        server.start();

        remote = new Remote("", "http://localhost:" + DEFAULT_PORT + "/");
        authInfo = new AuthInfo.Plain();
        observer = new Task.IObserver<Void, RemoteJob.Result>() {
            @Override
            public void onProgress(Void aVoid) {
                System.out.println("onProgress: ");
            }

            @Override
            public void onResult(RemoteJob.Result result) {
                System.out.println("onNext: " + result.message);
            }

            @Override
            public void onException(Exception exception) {
                exception.printStackTrace();
                fail(exception.getMessage());
            }
        };

        connectionManager = new ConnectionManager();
        connectionManager.setStartScheduler(new Task.CurrentThreadScheduler());
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();

        Thread.sleep(1000);
        server.stop();

        for (String dir : cleanUpDirs)
            StorageLib.recursiveDeleteFile(new File(dir));
    }

    private void syncChunkStore(final ConnectionManager connectionManager, final Repository repository,
                                final ICommitSignature commitSignature, final String serverUser) {
        connectionManager.submit(new ChunkStorePushJob(repository, serverUser, repository.getBranch()),
                remote, authInfo, new Task.IObserver<Void, ChunkStorePushJob.Result>() {
                    @Override
                    public void onProgress(Void aVoid) {
                        observer.onProgress(aVoid);
                    }

                    @Override
                    public void onResult(ChunkStorePushJob.Result result) {
                        observer.onResult(result);
                        if (result.pullRequired) {
                            connectionManager.submit(new ChunkStorePullJob(repository, commitSignature, serverUser,
                                    repository.getBranch()), remote, authInfo,
                                    new Task.IObserver<Void, ChunkStorePullJob.Result>() {
                                @Override
                                public void onProgress(Void aVoid) {
                                    observer.onProgress(aVoid);
                                }

                                @Override
                                public void onResult(ChunkStorePullJob.Result result) {
                                    observer.onResult(result);
                                }

                                @Override
                                public void onException(Exception exception) {
                                    observer.onException(exception);
                                }
                            });
                        }
                    }

                    @Override
                    public void onException(Exception exception) {
                        observer.onException(exception);
                    }
                });
    }

    public void testSyncChunkStore() throws Exception {
        String serverUser = "user1";
        String localCSDir = TEST_DIR + "/.chunkstore";
        String BRANCH = "testBranch";

        // push
        FejoaContext localContext = new FejoaContext(TEST_DIR, null);
        StorageDir local =  localContext.getStorage(BRANCH, null, null);
        local.writeString("testFile", "testData");
        local.commit();
        syncChunkStore(connectionManager, (Repository)local.getDatabase(), null, serverUser);

        // do changes on the server
        FejoaContext serverContext = new FejoaContext(SERVER_TEST_DIR, null);
        StorageDir server =  serverContext.getStorage(BRANCH, null, null);
        server.putBytes("testFileServer", "testDataServer".getBytes());
        server.commit();

        // merge
        local.putBytes("testFile2", "testDataClient2".getBytes());
        local.remove("testFile");
        local.commit();

        // sync
        syncChunkStore(connectionManager, (Repository)local.getDatabase(), null, serverUser);

        // pull into empty git
        StorageLib.recursiveDeleteFile(new File(localCSDir));
        localContext = new FejoaContext(TEST_DIR, null);
        local =  localContext.getStorage(BRANCH, null, null);
        syncChunkStore(connectionManager, (Repository)local.getDatabase(), null, serverUser);
    }

    public void testSimple() throws Exception {
        connectionManager.submit(new JsonPingJob(), remote, authInfo, observer);

        UserData userData = UserData.create(new FejoaContext(TEST_DIR, null), "password");
        connectionManager.submit(new CreateAccountJob("userName", "password", userData.getSettings()),
                remote, authInfo, observer);
        Thread.sleep(1000);

        connectionManager.submit(new LoginJob(userData.getContext(), "userName", "password"), remote, authInfo, observer);

        Thread.sleep(1000);
    }
}
