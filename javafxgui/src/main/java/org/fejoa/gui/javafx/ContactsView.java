/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.gui.javafx;

import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldListCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.Callback;
import javafx.util.StringConverter;
import org.fejoa.gui.IStatusManager;
import org.fejoa.library.*;
import org.fejoa.library.command.ContactRequestCommandHandler;
import org.fejoa.library.database.DatabaseDiff;
import org.fejoa.library.database.StorageDir;

import java.util.Collection;


class AcceptRequestCell extends ListCell<ContactRequestCommandHandler.ContactRequest> {
    final private HBox layout;
    final private Label text;
    final private Button button;

    public AcceptRequestCell() {
        super();

        text = new Label();
        button = new Button("Accept");

        layout = new HBox();
        layout.getChildren().add(text);
        layout.getChildren().add(button);
    }

    @Override
    public void updateItem(final ContactRequestCommandHandler.ContactRequest contactRequest, boolean empty) {
        super.updateItem(contactRequest, empty);
        setEditable(false);
        if (contactRequest != null) {
            button.setOnAction(new EventHandler<ActionEvent>() {
                @Override
                public void handle(ActionEvent event) {
                    contactRequest.accept();
                }
            });

            Remote remote = contactRequest.contact.getRemotes().getDefault();
            text.setText(remote.getUser() + "@" + remote.getServer());
            setGraphic(layout);
        } else {
            setGraphic(null);
        }
    }
}

class ContactRequestList extends ListView<ContactRequestCommandHandler.ContactRequest> {
    public ContactRequestList() {
        setCellFactory(new Callback<ListView<ContactRequestCommandHandler.ContactRequest>, ListCell<ContactRequestCommandHandler.ContactRequest>>() {
            @Override
            public ListCell<ContactRequestCommandHandler.ContactRequest> call(ListView<ContactRequestCommandHandler.ContactRequest> contactRequestListView) {
                return new AcceptRequestCell();
            }
        });
    }
}

class ContactListView extends ListView<ContactPublic> {
    final private StorageDir.IListener listener;

    public ContactListView(StorageDir storageDir, final StorageDirList<ContactPublic> contactList) {
        update(contactList);

        this.listener = new StorageDir.IListener() {
            @Override
            public void onTipChanged(DatabaseDiff diff, String base, String tip) {
                update(contactList);
            }
        };
        storageDir.addListener(listener);

        setCellFactory(new Callback<ListView<ContactPublic>, ListCell<ContactPublic>>() {
            @Override
            public ListCell<ContactPublic> call(ListView<ContactPublic> contactPublicListView) {
                return new TextFieldListCell<>(new StringConverter<ContactPublic>() {
                    @Override
                    public String toString(ContactPublic contactPublic) {
                        Remote remote = contactPublic.getRemotes().getDefault();
                        return remote.getUser() + "@" + remote.getServer();
                    }

                    @Override
                    public ContactPublic fromString(String branch) {
                        return null;
                        //return userData.findBranchInfo(branch);
                    }
                });
            }
        });
    }

    private void update(StorageDirList<ContactPublic> contactList) {
        getItems().clear();

        Collection<ContactPublic> contacts = contactList.getEntries();
        for (ContactPublic contact : contacts)
            getItems().add(contact);
    }
}

public class ContactsView extends VBox {
    public ContactsView(final Client client,
                        final ObservableList<ContactRequestCommandHandler.ContactRequest> contactRequests,
                        IStatusManager statusManager) {
        ContactStore contactStore = client.getUserData().getContactStore();

        HBox addContactLayout = new HBox();
        addContactLayout.getChildren().add(new Label("User:"));
        final TextField userName = new TextField("User2");
        addContactLayout.getChildren().add(userName);
        addContactLayout.getChildren().add(new Label("@"));
        final TextField serverName = new TextField("http://localhost:8080");
        addContactLayout.getChildren().add(serverName);
        Button addContactButton = new Button("Add Contact");
        addContactLayout.getChildren().add(addContactButton);

        getChildren().add(addContactLayout);
        getChildren().add(new Label("Contact Requests:"));
        final ContactRequestList contactRequestList = new ContactRequestList();
        contactRequestList.setItems(contactRequests);
        getChildren().add(contactRequestList);
        getChildren().add(new Label("Requested Contacts:"));
        getChildren().add(new ContactListView(contactStore.getStorageDir(), contactStore.getRequestedContacts()));
        getChildren().add(new Label("Contact List:"));
        getChildren().add(new ContactListView(contactStore.getStorageDir(), contactStore.getContactList()));

        addContactButton.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent actionEvent) {
                try {
                    client.contactRequest(userName.getText(), serverName.getText());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }
}
