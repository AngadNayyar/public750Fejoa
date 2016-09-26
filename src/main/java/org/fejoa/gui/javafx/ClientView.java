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
import org.fejoa.library.Client;
import org.fejoa.gui.IStatusManager;


public class ClientView extends HBox {
    final private Client client;

    public ClientView(final Client client, final IStatusManager statusManager) {
        this.client = client;
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

        getChildren().add(tabPane);
    }

    public Client getClient() {
        return client;
    }
}
