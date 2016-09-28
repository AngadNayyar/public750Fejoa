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
import javafx.scene.control.Button;
import javafx.scene.layout.VBox;
import org.fejoa.gui.IStatusManager;
import org.fejoa.library.Client;
import org.fejoa.library.ContactRequest;


public class ContactsView extends VBox {
    public ContactsView(final Client client, IStatusManager statusManager) {
        Button addContactButton = new Button("Add Contact");
        getChildren().add(addContactButton);

        addContactButton.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent actionEvent) {
                ContactRequest contactRequest = new ContactRequest(client);
                contactRequest.startRequest("User2", "http://localhost:8080", new ContactRequest.AutoAcceptHandler() {
                    @Override
                    public void onFinish() {

                    }

                    @Override
                    public void onException(Exception exception) {

                    }
                });
            }
        });
    }
}
