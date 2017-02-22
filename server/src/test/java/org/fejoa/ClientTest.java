/*
 * Copyright 2015 - 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa;

import junit.framework.TestCase;
import org.fejoa.chunkstore.HashValue;
import org.fejoa.library.*;
import org.fejoa.library.command.*;
import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.remote.Errors;
import org.fejoa.library.remote.RemoteJob;
import org.fejoa.library.remote.TaskUpdate;
import org.fejoa.library.remote.WatchJob;
import org.fejoa.library.support.StorageLib;
import org.fejoa.library.support.LooperThread;
import org.fejoa.library.support.Task;
import org.fejoa.server.CookiePerPortManager;
import org.fejoa.server.JettyServer;

import java.io.File;
import java.io.IOException;
import java.net.CookieHandler;
import java.net.CookiePolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;

import static org.fejoa.library.UserData.USER_DATA_CONTEXT;
import static org.fejoa.server.JettyServer.DEFAULT_PORT;


public class ClientTest extends TestCase {
    abstract class TestTask {
        TestTask nextTask;

        public void setNextTask(TestTask nextTask) {
            if (this.nextTask != null)
                finishAndFail("next task already set!");
            this.nextTask = nextTask;
        }

        protected void cleanUp() {

        }

        protected void onTaskPerformed() {
            cleanUp();
            try {
                nextTask.perform(this);
            } catch (Exception e) {
                e.printStackTrace();
                finishAndFail(e.getMessage());
            }
        }

        abstract protected void perform(TestTask previousTask) throws Exception;
    }

    private class ClientStatus {
        final public String name;
        final public String server;
        final public Remote remote;
        public boolean firstSync;

        public ClientStatus(String name, String server) {
            this.name = name;
            this.server = server;
            this.remote = new Remote(name, server);
        }
    }

    final static String TEST_DIR = "jettyTest";
    final static String SERVER_TEST_DIR_1 = TEST_DIR + "/Server1";
    final static String SERVER_TEST_DIR_2 = TEST_DIR + "/Server2";
    final static String SERVER_TEST_DIR_3 = TEST_DIR + "/Server3";
    final static String SERVER_URL_1 = "http://localhost:" + DEFAULT_PORT + "/";
    final static String SERVER_URL_2 = "http://localhost:" + (DEFAULT_PORT + 1) + "/";
    final static String USER_NAME_1 = "testUser1";
    final static String USER_NAME_1_NEW = "testUser1New";
    final static String SERVER_URL_1_NEW = "http://localhost:"  + (DEFAULT_PORT + 2) + "/";
    final static String USER_NAME_2 = "testUser2";
    final static String PASSWORD = "password";

    final private List<String> cleanUpDirs = new ArrayList<>();
    private JettyServer server1;
    private Client client1;
    private ClientStatus clientStatus1;

    private JettyServer server2;
    private Client client2;
    private ClientStatus clientStatus2;

    private JettyServer serverNew;
    private LooperThread clientThread = new LooperThread(100);

    private boolean failure = false;
    private Semaphore finishedSemaphore;

    class SimpleObserver<T extends RemoteJob.Result> implements Task.IObserver<Void, T> {
        final private Runnable onSuccess;

        public SimpleObserver(Runnable onSuccess) {
            this.onSuccess = onSuccess;

        }

        @Override
        public void onProgress(Void aVoid) {
            System.out.println("onProgress: ");
        }

        @Override
        public void onResult(T result) {
            if (result.status != Errors.DONE)
                finishAndFail(result.message);
            System.out.println("onNext: " + result.message);
            onSuccess.run();
        }

        @Override
        public void onException(Exception exception) {
            finishAndFail(exception.getMessage());
        }
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        finishedSemaphore = new Semaphore(0);
        Executor executor = new Task.LooperThreadScheduler(clientThread);

        cleanUpDirs.add(TEST_DIR);
        for (String dir : cleanUpDirs)
            StorageLib.recursiveDeleteFile(new File(dir));

        // allow cookies per port number in order so run multiple servers on localhost
        CookieHandler.setDefault(new CookiePerPortManager(null, CookiePolicy.ACCEPT_ALL));

        server1 = new JettyServer(SERVER_TEST_DIR_1, DEFAULT_PORT);
        server1.start();

        server2 = new JettyServer(SERVER_TEST_DIR_2, DEFAULT_PORT + 1);
        server2.start();

        serverNew = new JettyServer(SERVER_TEST_DIR_3, DEFAULT_PORT + 2);
        serverNew.start();

        clientStatus1 = new ClientStatus(USER_NAME_1, SERVER_URL_1);
        client1 = Client.create(new File(TEST_DIR + "/" + USER_NAME_1), executor, clientStatus1.name,
                clientStatus1.server, PASSWORD);
        client1.commit();
        client1.getConnectionManager().setStartScheduler(new Task.NewThreadScheduler());
        client1.getConnectionManager().setObserverScheduler(new Task.LooperThreadScheduler(clientThread));

        clientStatus2 = new ClientStatus(USER_NAME_2, SERVER_URL_2);
        client2 = Client.create(new File(TEST_DIR + "/" + USER_NAME_2), executor, clientStatus2.name,
                clientStatus2.server, PASSWORD);
        client2.commit();
        client2.getConnectionManager().setStartScheduler(new Task.NewThreadScheduler());
        client2.getConnectionManager().setObserverScheduler(new Task.LooperThreadScheduler(clientThread));

        AccessCommandHandler accessHandler = (AccessCommandHandler)client1.getIncomingCommandManager().getHandler(
                AccessCommand.COMMAND_NAME);
        accessHandler.addContextHandler(UserData.USER_DATA_CONTEXT, new AccessCommandHandler.IContextHandler() {
            @Override
            public boolean handle(String senderId, BranchInfo branchInfo) throws Exception {
                return true;
            }
        });

        clientThread.start();
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();

        server1.stop();

        for (String dir : cleanUpDirs)
            StorageLib.recursiveDeleteFile(new File(dir));
    }

    private void finishAndFail(Exception exception) {
        exception.printStackTrace();
        finishAndFail(exception.getMessage());
    }

    private void finishAndFail(String message) {
        failure = true;
        finishedSemaphore.release();
        fail(message);
    }

    class FinishTask extends TestTask {
        @Override
        protected void perform(TestTask previousTask) throws Exception {
            client1.stopSyncing();
            client2.stopSyncing();
            finishedSemaphore.release();
        }
    }

    class MergeTask extends TestTask {
        final private List<TestTask> tasks = new ArrayList<>();

        public MergeTask(TestTask... tasks) {
            for (TestTask task : tasks) {
                task.setNextTask(this);
                this.tasks.add(task);
            }
        }

        @Override
        protected void perform(TestTask previousTask) throws Exception {
            if (!tasks.remove(previousTask))
                throw new Exception("Unexpected task");
            if (tasks.size() == 0)
                onTaskPerformed();
        }
    }

    class CreateAndSyncAccountTask extends TestTask {
        final private Client client;
        final private ClientStatus status;

        CreateAndSyncAccountTask(Client client, ClientStatus status) {
            this.client = client;
            this.status = status;
        }

        @Override
        protected void perform(TestTask previousTask) throws Exception {
            client.createAccount(status.remote, PASSWORD, new SimpleObserver(new Runnable() {
                @Override
                public void run() {
                    try {
                        onAccountCreated(client, status);
                    } catch (Exception e) {
                        finishAndFail(e);
                    }
                }
            }));
        }

        private void onAccountCreated(Client client, final ClientStatus status) throws Exception {
            System.out.println("Account Created");
            // watch
            client.startSyncing(new Task.IObserver<TaskUpdate, Void>() {
                @Override
                public void onProgress(TaskUpdate update) {
                    System.out.println(update.toString());
                    if (!status.firstSync && update.getTotalWork() > 0 && update.getProgress() == update.getTotalWork()) {
                        status.firstSync = true;
                        try {
                            startCommandManagers();
                        } catch (Exception e) {
                            onException(e);
                        }
                        onTaskPerformed();
                    }
                }

                @Override
                public void onResult(Void aVoid) {
                    System.out.println(status.name + ": sync ok");
                }

                @Override
                public void onException(Exception exception) {
                    if (exception.getMessage().equals("canceled"))
                        return;
                    exception.printStackTrace();
                    finishAndFail(exception);
                }
            });
        }

        private void startCommandManagers() throws IOException, CryptoException {
            client.startCommandManagers(new Task.IObserver<TaskUpdate, Void>() {
                @Override
                public void onProgress(TaskUpdate update) {
                    System.out.println(status.name + ": " + update.toString());
                }

                @Override
                public void onResult(Void aVoid) {
                    System.out.println(status.name + ": Command sent");
                }

                @Override
                public void onException(Exception exception) {
                    finishAndFail(exception.getMessage());
                }
            });
            ContactRequestCommandHandler handler = (ContactRequestCommandHandler)client.getIncomingCommandManager()
                    .getHandler(ContactRequestCommand.COMMAND_NAME);
            handler.setListener(new ContactRequestCommandHandler.AutoAccept() {
                @Override
                public void onError(Exception exception) {
                    exception.printStackTrace();
                    finishAndFail(exception);
                }
            });
        }
    }

    class ContactRequestTask extends TestTask {
        @Override
        protected void perform(TestTask previousTask) throws Exception {
            ContactRequestCommandHandler handler = (ContactRequestCommandHandler)client1.getIncomingCommandManager()
                    .getHandler(ContactRequestCommand.COMMAND_NAME);
            handler.setListener(new ContactRequestCommandHandler.AutoAccept() {
                @Override
                public void onContactRequestReply(ContactRequestCommandHandler.ContactRequest contactRequest) {
                    super.onContactRequestReply(contactRequest);
                    onTaskPerformed();
                }

                @Override
                public void onError(Exception exception) {
                    exception.printStackTrace();
                    finishAndFail(exception);
                }
            });
            client1.contactRequest(USER_NAME_2, SERVER_URL_2);
        }
    }

    class GrantAccessForClient1Task extends TestTask {
        private AccessCommandHandler.IListener listener = new AccessCommandHandler.IListener() {
            @Override
            public void onError(Exception e) {
                finishAndFail(e);
            }

            @Override
            public void onAccessGranted(String contactId, AccessTokenContact accessTokenContact) {
                System.out.println("Access granted: " + contactId + " access entry: "
                        + accessTokenContact.getAccessEntry());

                try {
                    waitTillClient2UploadedTheAccessStore(0);
                } catch (Exception e) {
                    onError(e);
                }
            }
        };

        @Override
        protected void perform(TestTask previousTask) throws Exception {
            AccessCommandHandler handler = (AccessCommandHandler)client1.getIncomingCommandManager()
                    .getHandler(AccessCommand.COMMAND_NAME);

            handler.setListener(listener);

            UserData clientUserData = client2.getUserData();
            ContactStore contactStore = clientUserData.getContactStore();
            ContactPublic client2Contact = contactStore.getContactList().get(
                    client1.getUserData().getMyself().getId());

            // grant access to the access branch
            String branch = clientUserData.getAccessStore().getId();
            System.out.println("Client2 grant access for:" + branch);
            client2.grantAccess(branch, USER_DATA_CONTEXT, BranchAccessRight.PULL, client2Contact);
        }

        @Override
        protected void cleanUp() {
            AccessCommandHandler handler = (AccessCommandHandler)client1.getIncomingCommandManager()
                    .getHandler(AccessCommand.COMMAND_NAME);
            handler.setListener(null);
        }

        private void waitTillClient2UploadedTheAccessStore(final int retryCount) throws IOException, CryptoException {
            final UserData userData = client2.getUserData();
            final BranchInfo accessBranchInfo = userData.findBranchInfo(userData.getAccessStore().getId(), USER_DATA_CONTEXT);
            client2.peekRemoteStatus(accessBranchInfo.getLocationEntries().iterator().next(),
                    new Task.IObserver<Void, WatchJob.Result>() {
                @Override
                public void onProgress(Void aVoid) {

                }

                @Override
                public void onResult(WatchJob.Result result) {
                    if (result.status != Errors.DONE || result.updated == null) {
                        finishAndFail(result.message);
                        return;
                    }

                    HashValue localLogTip;
                    try {
                        localLogTip = userData.getContext().getStorageLogTip(accessBranchInfo.getBranch());
                    } catch (IOException e) {
                        finishAndFail(e.getMessage());
                        return;
                    }
                    for (WatchJob.BranchLogTip branchLogTip : result.updated) {
                        if (localLogTip.equals(branchLogTip.logTip)) {
                            System.out.println("Access store updated, retry counts: " + retryCount);
                            onTaskPerformed();
                            return;
                        }
                    }

                    int count = retryCount + 1;
                    if (count > 10) {
                        finishAndFail("failed to check access store status: too many retries");
                        return;
                    }
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    try {
                        waitTillClient2UploadedTheAccessStore(count);
                    } catch (Exception e) {
                        onException(e);
                    }
                }

                @Override
                public void onException(Exception exception) {
                    finishAndFail("failed to check access store status");
                }
            });
        }
    }

    class PullContactBranchFromClient2Task extends TestTask {
        @Override
        protected void perform(TestTask previousTask) throws Exception {
            UserData clientUserData = client1.getUserData();
            ContactStore contactStore = clientUserData.getContactStore();
            ContactPublic client2Contact = contactStore.getContactList().get(
                    client2.getUserData().getMyself().getId());

            final BranchInfo contactBranch = client2Contact.getBranchList().getEntries().iterator().next();
            BranchInfo.Location location = contactBranch.getLocationEntries().iterator().next();

            client1.pullBranch(new Remote(USER_NAME_2, SERVER_URL_2), location,
                    new SimpleObserver(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                TestCase.assertFalse(client1.getContext().getPlainStorage(contactBranch.getBranch()).getTip().equals(""));
                            } catch (Exception e) {
                                finishAndFail(e);
                            }
                            onTaskPerformed();
                        }
                    }));
        }
    }

    class MigrateTask extends TestTask {
        private MigrationCommandHandler.IListener listener = new MigrationCommandHandler.IListener() {
            @Override
            public void onError(Exception e) {
                finishAndFail(e);
            }

            @Override
            public void onContactMigrated(String contactId) {
                System.out.println("Contact migrated: " + contactId);

                ContactPublic contactPublic = (ContactPublic)client2.getUserData().getContactStore()
                        .getContactFinder().get(contactId);
                Remote newRemote = contactPublic.getRemotes().getDefault();
                TestCase.assertEquals(USER_NAME_1_NEW, newRemote.getUser());
                TestCase.assertEquals(SERVER_URL_1_NEW, newRemote.getServer());

                MigrationCommandHandler handler = (MigrationCommandHandler)client2.getIncomingCommandManager()
                        .getHandler(MigrationCommand.COMMAND_NAME);
                handler.setListener(null);
                onTaskPerformed();
            }
        };

        @Override
        protected void perform(TestTask previousTask) throws Exception {
            client1.createAccount(new Remote(USER_NAME_1_NEW, SERVER_URL_1_NEW), PASSWORD, client1.getUserData(),
                    new SimpleObserver(new Runnable() {
                @Override
                public void run() {
                    try {
                        migrate();
                    } catch (Exception e) {
                        finishAndFail(e);
                    }
                }
            }));
        }

        private void migrate() throws Exception {
            MigrationCommandHandler handler = (MigrationCommandHandler)client2.getIncomingCommandManager()
                    .getHandler(MigrationCommand.COMMAND_NAME);
            handler.setListener(listener);

            MigrationManager migrationManager = client1.getMigrationManager();
            final Remote remote = client1.getUserData().getGateway();
            migrationManager.migrate(new Remote(remote.getId(), USER_NAME_1_NEW, SERVER_URL_1_NEW), PASSWORD,
                    new Task.IObserver<Void, RemoteJob.Result>() {
                @Override
                public void onProgress(Void aVoid) {

                }

                @Override
                public void onResult(RemoteJob.Result result) {
                    try {
                        Remote newGateway = client1.getUserData().getGateway();
                        assertEquals(remote.getId(), newGateway.getId());
                        assertEquals(USER_NAME_1_NEW, newGateway.getUser());
                        assertEquals(SERVER_URL_1_NEW, newGateway.getServer());
                    } catch (IOException e) {
                        finishAndFail(e);
                    }
                }

                @Override
                public void onException(Exception exception) {
                    MigrationCommandHandler handler = (MigrationCommandHandler)client2.getIncomingCommandManager()
                            .getHandler(MigrationCommand.COMMAND_NAME);
                    handler.setListener(null);
                    finishAndFail(exception);
                }
            });
        }
    }

    static public void chainUpTasks(TestTask... tasks) {
        for (int i = 0; i < tasks.length - 1; i++)
            tasks[i].setNextTask(tasks[i + 1]);
    }

    public void testClient() throws Exception {
        CreateAndSyncAccountTask createAccountTask1 = new CreateAndSyncAccountTask(client1, clientStatus1);
        CreateAndSyncAccountTask createAccountTask2 = new CreateAndSyncAccountTask(client2, clientStatus2);

        // start it
        chainUpTasks(new MergeTask(createAccountTask1, createAccountTask2),
                new ContactRequestTask(),
                new GrantAccessForClient1Task(),
                new PullContactBranchFromClient2Task(),
                new MigrateTask(),
                new FinishTask());

        createAccountTask1.perform(null);
        createAccountTask2.perform(null);

        finishedSemaphore.acquire();
        assertFalse(failure);
        Thread.sleep(1000);
        clientThread.quit(true);
    }
}
