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
import javafx.scene.control.ListView;
import javafx.scene.layout.VBox;
import org.fejoa.gui.IStatusManager;
import org.fejoa.library.*;
import org.fejoa.library.database.DatabaseDiff;
import org.fejoa.library.database.StorageDir;

import java.util.Collection;


class ContactList extends ListView<String> {
    final private StorageDir.IListener listener;

    public ContactList(final ContactStore contactStore) {
        update(contactStore);

        this.listener = new StorageDir.IListener() {
            @Override
            public void onTipChanged(DatabaseDiff diff, String base, String tip) {
                update(contactStore);
            }
        };
        contactStore.getStorageDir().addListener(listener);
    }

    private void update(ContactStore contactStore) {
        getItems().clear();

        Collection<ContactPublic> contacts = contactStore.getContactList().getEntries();
        for (ContactPublic contact : contacts)
            getItems().add(contact.getId());
    }
}

public class ContactsView extends VBox {
    public ContactsView(final Client client, IStatusManager statusManager) {
        Button addContactButton = new Button("Add Contact");
        getChildren().add(addContactButton);
        getChildren().add(new ContactList(client.getUserData().getContactStore()));

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
