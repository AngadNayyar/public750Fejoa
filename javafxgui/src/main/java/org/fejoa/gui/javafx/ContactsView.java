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
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Callback;
import org.fejoa.gui.IStatusManager;
import org.fejoa.library.*;
import org.fejoa.library.command.ContactRequestCommandHandler;
import org.fejoa.library.database.DatabaseDiff;
import org.fejoa.library.database.StorageDir;

import java.util.Collection;
import java.util.List;


class ContactCell extends ListCell<ContactEntry> {
    ContactRequestLayout contactRequestLayout;
    ContactRequestSentLayout contactRequestedLayout;
    ContactLayout contactLayout;

    public ContactCell() {
        super();

        if (contactRequestLayout == null)
            contactRequestLayout = new ContactRequestLayout();
        if (contactRequestedLayout == null)
            contactRequestedLayout = new ContactRequestSentLayout();
        if (contactLayout == null)
            contactLayout = new ContactLayout();
    }

    class ContactBaseLayout extends HBox {
        final protected Label text;

        public ContactBaseLayout(String icon) {
            super();

            Image image = new Image(this.getClass().getClassLoader().getResourceAsStream(icon));
            text = new Label();
            setAlignment(Pos.CENTER_LEFT);

            getChildren().add(new ImageView(image));
            getChildren().add(text);
        }
    }

    class ContactRequestLayout extends ContactBaseLayout {
        final private Button button;

        public ContactRequestLayout() {
            super("contact_request.png");
            button = new Button("Accept");

            Pane space = new Pane();
            HBox.setHgrow(space, Priority.ALWAYS);
            getChildren().add(space);
            getChildren().add(button);
        }

        public void setTo(final ContactRequestCommandHandler.ContactRequest contactRequest) {
            button.setOnAction(new EventHandler<ActionEvent>() {
                @Override
                public void handle(ActionEvent event) {
                    contactRequest.accept();
                }
            });

            Remote remote = contactRequest.contact.getRemotes().getDefault();
            text.setText(remote.getUser() + "@" + remote.getServer());
        }
    }

    class ContactRequestSentLayout extends ContactBaseLayout {
        public ContactRequestSentLayout() {
            super("contact_requested.png");
        }

        public void setTo(ContactPublic contact) {
            Remote remote = contact.getRemotes().getDefault();
            text.setText(remote.getUser() + "@" + remote.getServer() + " (contact request sent)");
        }
    }

    class ContactLayout extends ContactBaseLayout {
        public ContactLayout() {
            super("contact.png");
        }

        public void setTo(ContactPublic contact) {
            Remote remote = contact.getRemotes().getDefault();
            text.setText(remote.getUser() + "@" + remote.getServer());
        }
    }

    @Override
    public void updateItem(final ContactEntry contact, boolean empty) {
        super.updateItem(contact, empty);
        setEditable(false);
        if (contact == null) {
            setGraphic(null);
            return;
        }

        Node layout = null;
        switch (contact.type) {
            case CONTACT_REQUEST:
                contactRequestLayout.setTo((ContactRequestCommandHandler.ContactRequest) contact.object);
                layout = contactRequestLayout;
                break;
            case CONTACT_REQUEST_SENT:
                contactRequestedLayout.setTo((ContactPublic)contact.object);
                layout = contactRequestedLayout;
                break;
            case CONTACT:
                contactLayout.setTo((ContactPublic)contact.object);
                layout = contactLayout;
                break;
        }

        setGraphic(layout);
    }
}

class ContactEntry {
    enum Type {
        CONTACT_REQUEST,
        CONTACT_REQUEST_SENT,
        CONTACT
    }

    final public Type type;
    final public Object object;

    protected ContactEntry(Type type, Object object) {
        this.type = type;
        this.object = object;
    }

    protected ContactEntry(Type type, ContactPublic contactPublic) {
        assert (type == Type.CONTACT_REQUEST_SENT) || (type == Type.CONTACT);
        this.type = type;
        this.object = contactPublic;
    }

    protected ContactEntry(ContactRequestCommandHandler.ContactRequest request) {
        this(Type.CONTACT_REQUEST, request);
    }
}

class ContactListView extends ListView<ContactEntry> {
    final private StorageDir.IListener listener;

    public ContactListView(StorageDir storageDir,
                           final ObservableList<ContactRequestCommandHandler.ContactRequest> contactRequests,
                           final StorageDirList<ContactPublic> requestedContactList,
                           final StorageDirList<ContactPublic> contactList) {
        update(contactRequests, requestedContactList, contactList);

        this.listener = new StorageDir.IListener() {
            @Override
            public void onTipChanged(DatabaseDiff diff, String base, String tip) {
                update(contactRequests, requestedContactList, contactList);
            }
        };
        storageDir.addListener(listener);

        contactRequests.addListener(new ListChangeListener<ContactRequestCommandHandler.ContactRequest>() {
            @Override
            public void onChanged(Change<? extends ContactRequestCommandHandler.ContactRequest> change) {
                update(contactRequests, requestedContactList, contactList);
            }
        });

        setCellFactory(new Callback<ListView<ContactEntry>, ListCell<ContactEntry>>() {
            @Override
            public ListCell<ContactEntry> call(ListView<ContactEntry> contactListView) {
                return new ContactCell();
            }
        });
    }

    private void update(final List<ContactRequestCommandHandler.ContactRequest> contactRequests,
                        final StorageDirList<ContactPublic> requestedContactList,
                        final StorageDirList<ContactPublic> contactList) {
        getItems().clear();

        for (ContactRequestCommandHandler.ContactRequest request : contactRequests)
            getItems().add(new ContactEntry(request));

        Collection<ContactPublic> requestedContacts = requestedContactList.getEntries();
        for (ContactPublic contact : requestedContacts)
            getItems().add(new ContactEntry(ContactEntry.Type.CONTACT_REQUEST_SENT, contact));

        Collection<ContactPublic> contacts = contactList.getEntries();
        for (ContactPublic contact : contacts)
            getItems().add(new ContactEntry(ContactEntry.Type.CONTACT, contact));
    }
}

public class ContactsView extends VBox {
    public ContactsView(final Client client,
                        final ObservableList<ContactRequestCommandHandler.ContactRequest> contactRequests,
                        IStatusManager statusManager) {
        ContactStore contactStore = client.getUserData().getContactStore();

        HBox addContactLayout = new HBox();
        addContactLayout.setAlignment(Pos.CENTER_LEFT);
        addContactLayout.getChildren().add(new Label("User:"));
        final TextField userName = new TextField("User2");
        addContactLayout.getChildren().add(userName);
        addContactLayout.getChildren().add(new Label("@"));
        final TextField serverName = new TextField("http://localhost:8080");
        addContactLayout.getChildren().add(serverName);
        Button addContactButton = new Button("Add Contact");
        addContactLayout.getChildren().add(addContactButton);

        getChildren().add(addContactLayout);
        getChildren().add(new Label("Contacts:"));
        final ContactListView contactListView = new ContactListView(contactStore.getStorageDir(), contactRequests,
                contactStore.getRequestedContacts(), contactStore.getContactList());
        getChildren().add(contactListView);

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
