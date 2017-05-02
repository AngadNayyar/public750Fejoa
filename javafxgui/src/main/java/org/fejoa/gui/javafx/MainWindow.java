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
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import org.fejoa.gui.Account;
import org.fejoa.gui.AccountManager;
import sun.plugin.javascript.navig.Anchor;

import java.io.File;

import static javafx.scene.paint.Color.WHITE;


public class MainWindow extends BorderPane {
    final StackPane clientViewStack = new StackPane();
    final StatusView statusView = new StatusView();

    final private AccountManager accountManager;

    private AccountManager.IListener accountManagerListener;

    public MainWindow(File homeDir) {
        this.accountManager = new AccountManager(homeDir);

        // Create an HBox to contain the label for heading "Portable Cloud Messenger"
        HBox heading = new HBox();
        Label title = new Label("Portable Cloud Messenger");
        title.setTextFill(WHITE);
        heading.getChildren().add(title);
        heading.setAlignment(Pos.CENTER);
        heading.setId("messenger-heading");

        // Create the tool bar containing the add new user button and select user drop down
        AccountListView accountView = new AccountListView(accountManager, statusView);
        ToolBar toolBar = new ToolBar(accountView);
        toolBar.setId("top-tool-bar");

        // Create a split pane to add the tool bar under the heading hbox at the top of the window
        SplitPane headerSplit = new SplitPane(heading, toolBar);
        headerSplit.setOrientation(Orientation.VERTICAL);
        headerSplit.setId("header-split-pane");
        setTop(headerSplit);

        // Create the split pane for the status view - and set the default layout to have the status view collapsed.
        SplitPane splitPane = new SplitPane(clientViewStack, statusView);
        splitPane.setOrientation(Orientation.VERTICAL);
        splitPane.setDividerPosition(0, 1);
        setCenter(splitPane);

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
                clientViewStack.getChildren().add(new ClientView(account.client, statusView));
                //clientViewStack.setPrefHeight(0.0);
            }
        };
        accountManager.addListener(accountManagerListener);

        if (accountManager.getAccountList().size() > 0)
            accountManager.setSelectedAccount(accountManager.getAccountList().get(0));
    }
}
