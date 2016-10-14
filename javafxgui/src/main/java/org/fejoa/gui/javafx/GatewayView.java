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
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import org.fejoa.gui.IStatusManager;
import org.fejoa.gui.TaskStatus;
import org.fejoa.library.Client;
import org.fejoa.library.Remote;
import org.fejoa.library.UserData;
import org.fejoa.library.remote.AuthInfo;
import org.fejoa.library.remote.ConnectionManager;
import org.fejoa.library.remote.JsonPingJob;
import org.fejoa.library.remote.RemoteJob;
import org.fejoa.library.support.Task;

import java.io.IOException;


public class GatewayView extends VBox {
    public GatewayView(Client client, IStatusManager statusManager) {
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
