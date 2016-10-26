/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.gui.javafx;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import org.fejoa.gui.IStatusManager;
import org.fejoa.gui.JobStatus;
import org.fejoa.gui.TaskStatus;
import org.fejoa.library.Client;
import org.fejoa.library.MigrationManager;
import org.fejoa.library.Remote;
import org.fejoa.library.UserData;
import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.remote.AuthInfo;
import org.fejoa.library.remote.JsonPingJob;
import org.fejoa.library.remote.RemoteJob;
import org.fejoa.library.support.Task;

import java.io.IOException;


public class GatewayView extends VBox {
    final private Client client;

    public GatewayView(Client client, IStatusManager statusManager) {
        this.client = client;

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(0, 10, 0, 10));

        Label serverUser = new Label();
        Label server = new Label();

        grid.add(new Label("Server user name:"), 0, 0);
        grid.add(serverUser, 1, 0);
        grid.add(new Label("Server:"), 0, 1);
        grid.add(server, 1, 1);

        try {
            Remote gateway = client.getUserData().getGateway();
            serverUser.setText(gateway.getUser());
            server.setText(gateway.getServer());
        } catch (IOException e) {
            e.printStackTrace();
        }

        getChildren().add(grid);
        getChildren().add(createPingButton(client, statusManager));
        getChildren().add(createMigrateLayout(statusManager));
    }

    private Node createMigrateLayout(final IStatusManager statusManager) {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(0, 10, 0, 10));

        final TextField serverUser = new TextField();
        final TextField server = new TextField();

        grid.add(new Label("Target user name:"), 0, 0);
        grid.add(serverUser, 1, 0);
        grid.add(new Label("Target server:"), 0, 1);
        grid.add(server, 1, 1);

        Button migrateButton = new Button("Migrate");
        migrateButton.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent actionEvent) {
                try {
                    createAccountAndMigrate(serverUser.getText(), server.getText(), statusManager);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        VBox layout = new VBox();
        layout.getChildren().add(new Label("Migration:"));
        layout.getChildren().add(grid);
        layout.getChildren().add(migrateButton);
        return layout;
    }

    private void createAccountAndMigrate(final String newUser, final String newServer,
                                         final IStatusManager statusManager) throws IOException, CryptoException {
        Remote remote = client.getUserData().getGateway();
        Remote newRemote = new Remote(remote.getId(), newUser, newServer);
        String password = client.getContext().getRootPassword(remote.getUser(), remote.getServer());
        client.createAccount(newRemote, password, new Task.IObserver<Void, RemoteJob.Result>() {
            @Override
            public void onProgress(Void aVoid) {

            }

            @Override
            public void onResult(RemoteJob.Result result) {
                try {
                    migrate(newUser,  newServer, statusManager);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onException(Exception exception) {

            }
        });
    }

    private void migrate(String newUser, String newServer, final IStatusManager statusManager) throws Exception {
        Remote remote = client.getUserData().getGateway();
        MigrationManager migrationManager = client.getMigrationManager();
        String password = client.getContext().getRootPassword(remote.getUser(), remote.getServer());
        final Remote newRemote = new Remote(remote.getId(), newUser, newServer);
        migrationManager.migrate(newRemote, password,
                new Task.IObserver<Void, RemoteJob.Result>() {
            @Override
            public void onProgress(Void aVoid) {

            }

            @Override
            public void onResult(RemoteJob.Result result) {
                JobStatus jobStatus = new JobStatus();
                jobStatus.setDone("Migrated to: " + newRemote.toAddress());
                statusManager.addJobStatus(jobStatus);
            }

            @Override
            public void onException(Exception exception) {
                JobStatus jobStatus = new JobStatus();
                jobStatus.setFailed("Migration failed: " + newRemote.toAddress());
                statusManager.addJobStatus(jobStatus);
            }
        });

    }

    private Button createPingButton(final Client client, final IStatusManager statusManager) {
        Button button = new Button("Ping Server");
        button.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent actionEvent) {
                final TaskStatus guiJob = new TaskStatus();
                guiJob.setStatus("Ping Pong Job");
                statusManager.addJobStatus(guiJob);
                UserData userData = client.getUserData();
                try {
                    Remote gateway = userData.getGateway();
                    Task task = client.getConnectionManager().submit(new JsonPingJob(),
                            gateway,
                            new AuthInfo.Plain(), new Task.IObserver<Void, RemoteJob.Result>() {
                                @Override
                                public void onProgress(Void o) {
                                    guiJob.setStatus("update");
                                }

                                @Override
                                public void onResult(RemoteJob.Result result) {
                                    guiJob.setStatus(result.message);
                                    guiJob.setDone();
                                }

                                @Override
                                public void onException(Exception exception) {
                                    guiJob.setStatus("Error: " + exception.getMessage());
                                    guiJob.setFailed();
                                }
                            });
                    guiJob.setTask(task);
                } catch (IOException e) {
                    e.printStackTrace();
                    guiJob.setFailed();
                }
            }
        });
        return button;
    }
}
