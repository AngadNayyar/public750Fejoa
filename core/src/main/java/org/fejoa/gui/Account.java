/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.gui;

import org.fejoa.library.Client;
import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.support.Task;
import org.json.JSONException;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.Executor;


public class Account {
    final public File accountDir;
    public Client client;

    public Account(File accountDir) {
        this(accountDir, null);
    }

    public Account(File accountDir, Client client) {
        this.accountDir = accountDir;
        this.client = client;
    }

    public boolean isOpen() {
        return client != null;
    }

    public void open(String password, Executor observerScheduler) throws JSONException, IOException, CryptoException {
        if (isOpen())
            return;
        client = Client.open(accountDir, observerScheduler, password);
        client.getConnectionManager().setStartScheduler(new Task.NewThreadScheduler());
        client.getConnectionManager().setObserverScheduler(observerScheduler);
    }

    public Client getClient() {
        return client;
    }

    @Override
    public String toString() {
        return accountDir.getName();
    }

    public void setClient(Client client) {
        this.client = client;
    }
}
