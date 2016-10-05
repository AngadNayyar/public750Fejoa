/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.gui.javafx;

import javafx.geometry.Side;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import org.fejoa.gui.JobStatus;
import org.fejoa.library.Client;
import org.fejoa.gui.IStatusManager;
import org.fejoa.library.ContactPublic;
import org.fejoa.library.command.ContactRequestCommandHandler;
import org.fejoa.library.remote.TaskUpdate;
import org.fejoa.library.support.Task;

import java.io.IOException;


public class ClientView extends HBox {
    final private Client client;

    public ClientView(final Client client, final IStatusManager statusManager) {
        this.client = client;

        final JobStatus syncStatus = new JobStatus();
        syncStatus.setStatus("WatchJob:");
        statusManager.addJobStatus(syncStatus);
        try {
            client.startSyncing(new Task.IObserver<TaskUpdate, Void>() {
                JobStatus ongoing;
                @Override
                public void onProgress(TaskUpdate update) {
                    if (ongoing == null) {
                        ongoing = new JobStatus();
                        statusManager.addJobStatus(ongoing);
                    }
                    ongoing.setStatus(update.toString());
                    if (update.getProgress() == update.getTotalWork()) {
                        ongoing.setDone();
                        ongoing = null;
                    }
                }

                @Override
                public void onResult(Void aVoid) {
                    syncStatus.setStatus("sync ok");
                    syncStatus.setDone();
                }

                @Override
                public void onException(Exception exception) {
                    syncStatus.setStatus(exception.getMessage());
                    syncStatus.setFailed();
                }
            });
        } catch (IOException exception) {
            syncStatus.setStatus(exception.getMessage());
            syncStatus.setFailed();
        }
        try {
            client.startCommandManagers(new Task.IObserver<TaskUpdate, Void>() {
                @Override
                public void onProgress(TaskUpdate update) {
                    final JobStatus commandManagerStatus = new JobStatus();
                    statusManager.addJobStatus(commandManagerStatus);
                    commandManagerStatus.setStatus(update.toString());
                    commandManagerStatus.setDone();
                }

                @Override
                public void onResult(Void aVoid) {
                    final JobStatus commandManagerStatus = new JobStatus();
                    statusManager.addJobStatus(commandManagerStatus);
                    commandManagerStatus.setStatus("Command sent");
                    commandManagerStatus.setDone();
                }

                @Override
                public void onException(Exception exception) {
                    final JobStatus commandManagerStatus = new JobStatus();
                    statusManager.addJobStatus(commandManagerStatus);
                    commandManagerStatus.setStatus(exception.getMessage());
                    commandManagerStatus.setFailed();
                }
            });
        } catch (Exception exception) {
            final JobStatus commandManagerStatus = new JobStatus();
            statusManager.addJobStatus(commandManagerStatus);
            commandManagerStatus.setStatus(exception.getMessage());
            commandManagerStatus.setFailed();
        }

        setBackground(new Background(new BackgroundFill(Color.WHITE, null, null)));

        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabPane.setSide(Side.LEFT);

        Tab gatewayTab = new Tab("Gateway");
        gatewayTab.setContent(new GatewayView(client, statusManager));
        tabPane.getTabs().add(gatewayTab);

        Tab userDataStorageTab = new Tab("Storage");
        userDataStorageTab.setContent(new UserDataStorageView(client));
        tabPane.getTabs().add(userDataStorageTab);

        Tab historyTab = new Tab("History");
        historyTab.setContent(new HistoryView(client.getUserData()));
        tabPane.getTabs().add(historyTab);

        Tab contactsTab = new Tab("Contacts");
        contactsTab.setContent(new ContactsView(client, statusManager));
        tabPane.getTabs().add(contactsTab);

        getChildren().add(tabPane);
    }

    public Client getClient() {
        return client;
    }
}
