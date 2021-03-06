/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.gui.javafx;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import org.fejoa.gui.Account;
import org.fejoa.gui.AccountManager;
import org.fejoa.gui.IStatusManager;
import org.fejoa.gui.TaskStatus;
import org.fejoa.library.Client;
import org.fejoa.library.Remote;
import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.remote.RemoteJob;
import org.fejoa.library.support.StorageLib;
import org.fejoa.library.support.Task;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.Executor;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import org.fejoa.gui.Account;
import org.fejoa.gui.AccountManager;
import org.fejoa.gui.IStatusManager;
import org.fejoa.gui.TaskStatus;
import org.fejoa.library.Client;
import org.fejoa.library.Remote;
import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.remote.RemoteJob;
import org.fejoa.library.support.StorageLib;
import org.fejoa.library.support.Task;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.Executor;


public class AccountListView extends HBox {
    final private AccountManager accountManager;
    final ComboBox<Account> accountView = new ComboBox<>();
    private AccountManager.IListener accountManagerListener;
    Label greetingUser;

    public AccountListView(final AccountManager accountManager,
                           final IStatusManager statusManager) {
        this.accountManager = accountManager;

        accountView.setId("account-view-dropdown");
        accountView.setItems(new ObservableListAdapter<>(accountManager.getAccountList()));
        accountView.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<Account>() {
            @Override
            public void changed(ObservableValue<? extends Account> observableValue, Account oldAccount,
                                Account account) {
                if (account != null && !account.isOpen()) {
                    try {
                        account.open("test", new JavaFXScheduler());
                    } catch (Exception e) {
                        e.printStackTrace();
                        LoginWindow loginWindow = new LoginWindow(account);
                        loginWindow.showAndWait();
                    }
                    account.setClient(account.getClient());
                }
                AccountListView.this.accountManager.setSelectedAccount(account);
            }
        });

        if (accountManager.getSelectedAccount() != null)
            accountView.getSelectionModel().select(accountManager.getSelectedAccount());
        accountManagerListener = new AccountManager.IListener() {
            @Override
            public void onAccountSelected(Account account) {
                if (account != null) {
                    accountView.getSelectionModel().select(account);
                    greetingUser.setText("Welcome,");
                }
                else {
                    greetingUser.setText("Welcome!");
                    accountView.getSelectionModel().clearSelection();
                }
            }
        };
        accountManager.addListener(accountManagerListener);

        // Create a hbox for the buttons in the tool bar and create the new user button
        final Button addAccountButton = new Button();
        addAccountButton.setId("add-account-btn");
        addAccountButton.setMinWidth(25.0);
        final Tooltip tooltip = new Tooltip();
        tooltip.setText("Add a new account");
        addAccountButton.setTooltip(tooltip);

        // Create a new label to welcome the logged in user - or just welcome if no current users
        try {
            greetingUser = new Label("Welcome,");

        } catch (IndexOutOfBoundsException e) {
            greetingUser = new Label("Welcome!");
        }

        greetingUser.setId("welcome-label");

        // Add the buttons and labels to the hbox to be added to the toolbar
        getChildren().add(greetingUser);
        setAlignment(Pos.CENTER);
        getChildren().add(accountView);
        getChildren().add(addAccountButton);
        setSpacing(3.0);

        addAccountButton.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent actionEvent) {
                CreateAccountWindow loginWindow = new CreateAccountWindow("User1");
                loginWindow.showAndWait();
                if (!loginWindow.isValid())
                    return;
                File dir = new File(accountManager.getAccountDir(), loginWindow.getUserName());
                dir.mkdirs();
                try {
                    Executor observerExecutor = new JavaFXScheduler();
                    Client client = Client.create(dir, observerExecutor, loginWindow.getUserName(),
                            loginWindow.getServer(), loginWindow.getPassword());
                    client.commit();
                    client.getConnectionManager().setStartScheduler(new Task.NewThreadScheduler());
                    client.getConnectionManager().setObserverScheduler(observerExecutor);

                    createAccountOnServer(client, loginWindow.getUserName(), loginWindow.getPassword(),
                            loginWindow.getServer(), statusManager);
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (CryptoException e) {
                    e.printStackTrace();
                }
            }
        });
    }
    private void createAccountOnServer(final Client client, String user, String password, String server,
                                       IStatusManager statusManager) {
        final TaskStatus guiJob = new TaskStatus();
        guiJob.setStatus("Create Account on Server");
        statusManager.addJobStatus(guiJob);
        try {
            client.createAccount(new Remote(user, server), password, new Task.IObserver<Void, RemoteJob.Result>() {
                @Override
                public void onProgress(Void aVoid) {
                }

                @Override
                public void onResult(RemoteJob.Result result) {
                    guiJob.setStatus("Account Created: " + result.message);
                    guiJob.setDone();

                    Account newAccount = new Account(client.getContext().getHomeDir(), client);
                    accountManager.getAccountList().add(newAccount);
                    accountView.getSelectionModel().select(newAccount);
                }

                @Override
                public void onException(Exception exception) {
                    onFail(exception, guiJob, client);
                }
            });
        } catch (Exception e) {
            onFail(e, guiJob, client);
        }
    }

    private void onFail(Exception e, TaskStatus status, Client client) {
        e.printStackTrace();
        status.setStatus(e.getMessage());
        status.setFailed();

        StorageLib.recursiveDeleteFile(client.getContext().getHomeDir());
    }
}
