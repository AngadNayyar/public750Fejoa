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
        Label title = new Label("Portable Cloud Messenger");
        title.setTextFill(Color.DIMGRAY);
        
        heading.getChildren().add(title);
        heading.setAlignment(Pos.CENTER);
        heading.setId("messenger-heading");
        setId("main-window-borderpane");

        // Create the hbox containing the account functionality for the tool bar
        AccountListView accountView = new AccountListView(accountManager, statusView);
        // The spacer pane fills to fit the size of the parent hbox - pushing toolbar to float left.
        Pane spacer = new Pane();
        HBox.setHgrow(spacer,Priority.ALWAYS);

        // Create the tool bar containing the add new user button and select user drop down
        ToolBar toolBar = new ToolBar(spacer, accountView);
        toolBar.setId("top-tool-bar");

        // Create a split pane to add the tool bar under the heading hbox at the top of the window
        SplitPane headerSplit = new SplitPane(heading, toolBar);
        headerSplit.setOrientation(Orientation.VERTICAL);
        headerSplit.setId("header-split-pane");
        setTop(headerSplit);

        // Create the split pane for the status view - and set the default layout to have the status view collapsed.
        SplitPane splitPane = new SplitPane(clientViewStack, statusView);
        splitPane.setId("status-view-splitpane");
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
