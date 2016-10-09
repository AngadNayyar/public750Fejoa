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
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.fejoa.gui.Account;
import org.fejoa.library.crypto.CryptoException;


public class LoginWindow extends LoginWindowBase {
    final private PasswordField passwordField = new PasswordField();

    public String getPassword() {
        return passwordField.getText();
    }

    public LoginWindow(final Account account) {
        VBox mainLayout = new VBox();
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(0, 10, 0, 10));

        grid.add(new Label("Password:"), 0, 1);
        grid.add(passwordField, 1, 1);
        mainLayout.getChildren().add(grid);
        statusLabel.setAlignment(Pos.CENTER);
        mainLayout.getChildren().add(statusLabel);

        passwordField.textProperty().addListener(textFieldChangeListener);

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
