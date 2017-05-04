/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.gui.javafx;

import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.ToolBar;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import org.fejoa.gui.Account;
import org.fejoa.gui.AccountManager;
import sun.plugin.javascript.navig.Anchor;

import java.io.File;

import static javafx.scene.paint.Color.BLACK;
import static javafx.scene.paint.Color.WHITE;
import static javafx.scene.paint.Color.color;


public class MainWindow extends BorderPane {
    final StackPane clientViewStack = new StackPane();
    final StatusView statusView = new StatusView();

    final private AccountManager accountManager;

    private AccountManager.IListener accountManagerListener;

    public MainWindow(File homeDir) {
        this.accountManager = new AccountManager(homeDir);

        // Create an HBox to contain the label for heading "Portable Cloud Messenger"
        HBox heading = new HBox();
        Image logoImage = new Image(Resources.ICON_LOGO);
        ImageView logo = new ImageView(logoImage);
        logo.setFitHeight(30.0);
        logo.setFitWidth(300.0);

        heading.getChildren().add(logo);
        heading.setAlignment(Pos.CENTER);
        heading.setId("messenger-heading");
        setId("main-window-borderpane");

        // Create the hbox containing the account functionality for the tool bar
        AccountListView accountView = new AccountListView(accountManager, statusView);

        // Create spacer panes to fill the parent hbox - pushing toolbar to float left.
        Pane spacer = new Pane();
        HBox.setHgrow(spacer,Priority.ALWAYS);
        Pane leftHeaderSpacer = new Pane();
        leftHeaderSpacer.setPrefWidth(150.0);
        HBox.setHgrow(leftHeaderSpacer,Priority.ALWAYS);

        // Create a HBox to add the tool bar under the heading hbox at the top of the window
        HBox headerSplit = new HBox(leftHeaderSpacer, heading, spacer, accountView);
        headerSplit.setId("header-split-pane");
        setTop(headerSplit);

        // Create the split pane for the status view - and set the default layout to have the status view collapsed.
        SplitPane splitPane = new SplitPane(clientViewStack, statusView);
        splitPane.setId("status-view-splitpane");
        splitPane.setOrientation(Orientation.VERTICAL);
        splitPane.setDividerPosition(0, 1);
        setCenter(splitPane);

        clientViewStack.setId("client-view-stack-pane");

        accountManagerListener = new AccountManager.IListener() {
            @Override
            public void onAccountSelected(Account account) {
                if (!account.isOpen())
                    return;

                for (Node clientView : clientViewStack.getChildren()) {
                    if (((ClientView)clientView).getClient() == account.getClient()) {
                        clientView.toFront();
                        return;
                    }
                }
                ClientView cv = new ClientView(account.client, statusView);
                cv.setId("client-view");
                clientViewStack.getChildren().add(cv);
                //clientViewStack.setPrefHeight(0.0);
            }
        };
        accountManager.addListener(accountManagerListener);

        if (accountManager.getAccountList().size() > 0)
            accountManager.setSelectedAccount(accountManager.getAccountList().get(0));
    }
}
