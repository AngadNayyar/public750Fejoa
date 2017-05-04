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
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import org.fejoa.gui.Account;
import org.fejoa.library.crypto.CryptoException;

import java.io.File;
import java.nio.file.Path;


public class LoginWindow extends LoginWindowBase {
    final private PasswordField passwordField = new PasswordField();

    public String getPassword() {
        return passwordField.getText();
    }

    public LoginWindow(final Account account) {
        VBox mainLayout = new VBox();

        // Create a login window heading and label
        HBox portableCloudMessengerTitle = new HBox();
        Label portableCloud = new Label("Portable Cloud Messenger");
        portableCloudMessengerTitle.getChildren().add(portableCloud);
        mainLayout.getChildren().add(portableCloudMessengerTitle);

        // Style and add the login window heading
        portableCloud.setStyle("-fx-text-fill: dimgrey; -fx-font-weight: bold;");
        portableCloudMessengerTitle.setStyle("-fx-padding: 10; -fx-border-width: 0 0 0 0;  -fx-border-color: dimgrey");
        portableCloud.setAlignment(Pos.CENTER);
        portableCloudMessengerTitle.setAlignment(Pos.CENTER);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(0, 10, 0, 10));

        // Set the css of the login window to have a grey background
        mainLayout.setStyle("-fx-background-color: #f1f0f0; -fx-text-fill: white;");
        grid.setStyle("-fx-background-color: #f1f0f0; -fx-text-fill: white;");

        // Build combo box to select from
        ComboBox<String> accountComboBox = new ComboBox<>();
        File accountFile = account.accountDir.getParentFile();
        for (String accountName : accountFile.list()){
            accountComboBox.getItems().add(accountName);
        }


        //Added username to show the current account name.
        grid.add(new Label("Username:"), 1, 1);
        grid.add(accountComboBox, 2, 1); // TODO
//        grid.add(new Label(account.toString()), 2, 1);
        grid.add(new Label("Password:"), 1, 2);
        grid.add(passwordField, 2, 2);
        mainLayout.getChildren().add(grid);
        statusLabel.setAlignment(Pos.CENTER);
        mainLayout.getChildren().add(statusLabel);

        passwordField.textProperty().addListener(textFieldChangeListener);

        //This event handler allows the user to press the "enter" key instead of the "OK" button.
        passwordField.setOnAction(new EventHandler<ActionEvent>() {
            @Override public void handle(ActionEvent e) {
                login(account);
            }
        });

        HBox buttonLayout = new HBox();
        buttonLayout.setAlignment(Pos.BOTTOM_RIGHT);
        Button cancelButton = new Button("cancel");
        cancelButton.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent actionEvent) {
                close();
            }
        });
        okButton.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent actionEvent) {
                login(account);
            }
        });

        buttonLayout.getChildren().add(cancelButton);
        buttonLayout.getChildren().add(okButton);
        mainLayout.getChildren().add(buttonLayout);

        setScene(new Scene(mainLayout));
    }

    private void login(Account account) {
        try {
            account.open(getPassword(), new JavaFXScheduler());
            close();
        } catch (CryptoException e) {
            errorLabel("Wrong password");
        } catch (Exception e) {
            e.printStackTrace();
            errorLabel("Failed to open account!");
        }
    }

    @Override
    protected String getError() {
        if (passwordField.getText().equals(""))
            return "Enter password:";
        return null;
    }
}
