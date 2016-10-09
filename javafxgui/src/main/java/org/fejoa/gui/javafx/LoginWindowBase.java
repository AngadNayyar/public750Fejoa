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
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;


public abstract class LoginWindowBase extends Stage {
    final protected Button okButton = new Button("ok");
    final protected Label statusLabel = new Label();

    public LoginWindowBase() {
        okButton.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent actionEvent) {
                close();
            }
        });
        okButton.setDisable(true);

        setOnCloseRequest(new EventHandler<WindowEvent>() {
            public void handle(WindowEvent ev) {
                ev.consume();
            }
        });
    }

    protected ChangeListener<String> textFieldChangeListener = new ChangeListener<String>() {
        @Override
        public void changed(ObservableValue<? extends String> observableValue, String s, String t1) {
            validate(getError());
        }
    };

    protected void validate(String error) {
        if (error == null) {
            okButton.setDisable(false);
            doneLabel("ok");
        } else {
            okButton.setDisable(true);
            if (error.equals("")) {
                statusLabel.setText("");
            } else {
                errorLabel(error);
            }
        }
    }

    protected void errorLabel(String label) {
        statusLabel.setTextFill(Color.valueOf("red"));
        statusLabel.setText(label);
    }

    protected void doneLabel(String label) {
        statusLabel.setTextFill(Color.valueOf("green"));
        statusLabel.setText(label);
    }

    abstract protected String getError();

    public boolean isValid() {
        return getError() == null;
    }
}
