/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.gui.javafx;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Side;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.BorderPane;
import javafx.scene.paint.Color;
import org.fejoa.gui.JobStatus;
import org.fejoa.library.Client;
import org.fejoa.gui.IStatusManager;
import org.fejoa.library.ContactPublic;
import org.fejoa.library.command.ContactRequestCommand;
import org.fejoa.library.command.ContactRequestCommandHandler;
import org.fejoa.library.command.IncomingCommandManager;
import org.fejoa.library.remote.TaskUpdate;
import org.fejoa.library.support.Task;


public class ClientView extends BorderPane {
    final private Client client;
    private ObservableList<ContactRequestCommandHandler.ContactRequest> contactRequests
            = FXCollections.observableArrayList();

    public ClientView(final Client client, final IStatusManager statusManager) {
        this.client = client;

        IncomingCommandManager incomingCommandManager = client.getIncomingCommandManager();
        ContactRequestCommandHandler contactRequestCommandHandler
                = (ContactRequestCommandHandler)incomingCommandManager.getHandler(ContactRequestCommand.COMMAND_NAME);
        contactRequestCommandHandler.setListener(new ContactRequestCommandHandler.IListener() {
            @Override
            public void onContactRequest(ContactRequestCommandHandler.ContactRequest contactRequest) {
                contactRequests.add(contactRequest);

                JobStatus jobStatus = new JobStatus();
                jobStatus.setDone("Contact Request Received");
                statusManager.addJobStatus(jobStatus);
            }

            @Override
            public void onContactRequestReply(ContactRequestCommandHandler.ContactRequest contactRequest) {
                contactRequest.accept();

                JobStatus jobStatus = new JobStatus();
                jobStatus.setDone("Contact Request Reply Received");
                statusManager.addJobStatus(jobStatus);
            }

            @Override
            public void onContactRequestFinished(ContactPublic contactPublic) {
                ContactRequestCommandHandler.ContactRequest finishedRequest = null;
                for (ContactRequestCommandHandler.ContactRequest request : contactRequests) {
                    if (request.contact.getId().equals(contactPublic.getId())) {
                        finishedRequest = request;
                        break;
                    }
                }
                if (finishedRequest == null) {
                    JobStatus jobStatus = new JobStatus();
                    jobStatus.setFailed("Unknown contact requested finished!");
                    statusManager.addJobStatus(jobStatus);
                    return;
                }
                contactRequests.remove(finishedRequest);

                JobStatus jobStatus = new JobStatus();
                jobStatus.setDone("Contact Request Finished");
                statusManager.addJobStatus(jobStatus);
            }

            @Override
            public void onError(Exception e) {
                JobStatus jobStatus = new JobStatus();
                jobStatus.setFailed("Error in Contact Request Handling: " + e.getMessage());
                statusManager.addJobStatus(jobStatus);
            }
        });

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
        } catch (Exception exception) {
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

        // Create the tab pane for the left hand side to switch between functions
        // set the id for styling in css.
        TabPane tabPane = new TabPane();
        tabPane.setId("tab-pane-menu");
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabPane.setSide(Side.TOP);

        Tab gatewayTab = new Tab();
        ImageView gatewayView = new ImageView(new Image(Resources.ICON_GATEWAY));
        gatewayView.setFitWidth(20.0);
        gatewayView.setFitHeight(20.0);
        gatewayTab.setGraphic(gatewayView);
        gatewayTab.setContent(new GatewayView(client, statusManager));
        final Tooltip gatewaytooltip = new Tooltip();
        gatewaytooltip.setText("Gateway");
        gatewayTab.setTooltip(gatewaytooltip);
        tabPane.getTabs().add(gatewayTab);

        Tab userDataStorageTab = new Tab();
        ImageView branchView = new ImageView(new Image(Resources.ICON_BRANCH));
        branchView.setFitWidth(20.0);
        branchView.setFitHeight(20.0);
        userDataStorageTab.setGraphic(branchView);
        userDataStorageTab.setContent(new UserDataStorageView(client));
        final Tooltip branchtooltip = new Tooltip();
        branchtooltip.setText("Branches");
        userDataStorageTab.setTooltip(branchtooltip);
        tabPane.getTabs().add(userDataStorageTab);

        Tab historyTab = new Tab();
        ImageView historyView = new ImageView(new Image(Resources.ICON_HISTORY));
        historyView.setFitWidth(20.0);
        historyView.setFitHeight(20.0);
        historyTab.setGraphic(historyView);
        historyTab.setContent(new HistoryView(client.getUserData()));
        final Tooltip historytooltip = new Tooltip();
        historytooltip.setText("History");
        historyTab.setTooltip(historytooltip);
        tabPane.getTabs().add(historyTab);

        Tab contactsTab = new Tab();
        ImageView contactsView = new ImageView(new Image(Resources.ICON_CONTACTS));
        contactsView.setFitHeight(20.0);
        contactsView.setFitWidth(20.0);
        contactsTab.setGraphic(contactsView);
        contactsTab.setContent(new ContactsView(client, contactRequests, statusManager));
        final Tooltip contactstooltip = new Tooltip();
        contactstooltip.setText("Contacts");
        contactsTab.setTooltip(contactstooltip);
        tabPane.getTabs().add(contactsTab);

        Tab fileStorageTab = new Tab();
        ImageView fileView = new ImageView(new Image(Resources.ICON_FILES));
        fileView.setFitWidth(20.0);
        fileView.setFitHeight(20.0);
        fileStorageTab.setGraphic(fileView);
        fileStorageTab.setContent(new FileStorageView(client, statusManager));
        final Tooltip filetooltip = new Tooltip();
        filetooltip.setText("Files");
        fileStorageTab.setTooltip(filetooltip);
        tabPane.getTabs().add(fileStorageTab);

        // Add a messenger view to the tab content of the messages tab
        Tab messengerTap = new Tab();
        MessengerView messengerView = new MessengerView(client, statusManager);
        ImageView messageView = new ImageView(new Image(Resources.ICON_MESSENGER));
        messageView.setFitHeight(20.0);
        messageView.setFitWidth(20.0);
        messengerTap.setGraphic(messageView);
        messengerView.setId("messenger-view");
        messengerTap.setContent(messengerView);
        final Tooltip messagetooltip = new Tooltip();
        messagetooltip.setText("Messenger");
        messengerTap.setTooltip(messagetooltip);
        tabPane.getTabs().add(messengerTap);

        // set the default selected tab to be the messages tab when opening the gui
        tabPane.getSelectionModel().select(messengerTap);

        setCenter(tabPane);
    }

    public Client getClient() {
        return client;
    }
}
