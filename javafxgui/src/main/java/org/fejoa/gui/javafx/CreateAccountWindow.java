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
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;


public class CreateAccountWindow extends LoginWindowBase {
    final private TextField userNameField = new TextField();
    final private PasswordField passwordField = new PasswordField();
    final private PasswordField passwordField2 = new PasswordField();
    final private TextField serverField = new TextField();

    public String getUserName() {
        return userNameField.getText();
    }

    public String getPassword() {
        return passwordField.getText();
    }

    public String getServer() {
        return serverField.getText();
    }

    public CreateAccountWindow(String defaultUserName) {
        VBox mainLayout = new VBox();
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(0, 10, 0, 10));

        if (defaultUserName != null)
            userNameField.setText(defaultUserName);
        grid.add(new Label("User name:"), 0, 0);
        grid.add(userNameField, 1, 0);
        grid.add(new Label("Password:"), 0, 1);
        grid.add(passwordField, 1, 1);
        grid.add(new Label("Retype Password:"), 0, 2);
        grid.add(passwordField2, 1, 2);
        grid.add(new Label("Server:"), 0, 3);
        grid.add(serverField, 1, 3);
        serverField.setText("http://localhost:8180");
        mainLayout.getChildren().add(grid);
        statusLabel.setAlignment(Pos.CENTER);
        mainLayout.getChildren().add(statusLabel);

        userNameField.textProperty().addListener(textFieldChangeListener);
        passwordField.textProperty().addListener(textFieldChangeListener);
        passwordField2.textProperty().addListener(textFieldChangeListener);
        serverField.textProperty().addListener(textFieldChangeListener);

        HBox buttonLayout = new HBox();
        buttonLayout.setAlignment(Pos.BOTTOM_RIGHT);
        Button cancelButton = new Button("cancel");
        cancelButton.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent actionEvent) {
                close();
            }
        });

        buttonLayout.getChildren().add(cancelButton);
        buttonLayout.getChildren().add(okButton);
        mainLayout.getChildren().add(buttonLayout);

        setScene(new Scene(mainLayout));
    }

    @Override
    protected String getError() {
        String userName = userNameField.getText();
        String password = passwordField.getText();
        String password2 = passwordField2.getText();
        String server = getServer();

        if (userName.equals("") || password.equals("") || password2.equals("") || server.equals(""))
            return "";
        if (!password.equals(password2))
            return "Password miss match";
        return null;
    }
}
