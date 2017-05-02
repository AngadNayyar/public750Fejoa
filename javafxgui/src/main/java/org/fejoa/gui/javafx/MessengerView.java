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
import javafx.scene.control.cell.TextFieldListCell;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.util.Callback;
import javafx.util.StringConverter;
import org.fejoa.gui.Account;
import org.fejoa.gui.IStatusManager;
import org.fejoa.library.BranchInfo;
import org.fejoa.library.Client;
import org.fejoa.library.ContactPublic;
import org.fejoa.library.UserData;
import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.database.DatabaseDiff;
import org.fejoa.library.database.StorageDir;
import org.fejoa.messaging.Message;
import org.fejoa.messaging.MessageBranch;
import org.fejoa.messaging.MessageBranchEntry;
import org.fejoa.messaging.Messenger;

import java.io.IOException;
import java.util.*;


class CreateMessageBranchView extends VBox {
    public CreateMessageBranchView(final UserData userData, final Messenger messenger)  {
        setBackground(new Background(new BackgroundFill(Color.WHITE, null, null)));

        HBox receiverLayout = new HBox();
        receiverLayout.getChildren().add(new Label("Receiver:"));
        final TextField receiverTextField = new TextField();
        receiverTextField.setText("User2@http://localhost:8180");
        HBox.setHgrow(receiverTextField, Priority.ALWAYS);
        receiverLayout.getChildren().add(receiverTextField);

        final TextArea bodyText = new TextArea();
        bodyText.setText("Message Body");
        Button sendButton = new Button("Send >");
        sendButton.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent actionEvent) {
                try {
                    List<ContactPublic> participants = new ArrayList<>();
                    for (ContactPublic contactPublic : userData.getContactStore().getContactList().getEntries()) {
                        if (contactPublic.getRemotes().getDefault().toAddress().equals(receiverTextField.getText()))
                            participants.add(contactPublic);
                    }
                    MessageBranch messageBranch = messenger.createMessageBranch(participants);
                    Message message = Message.create(userData.getContext(), userData.getMyself());
                    message.setBody(bodyText.getText());
                    messageBranch.addMessage(message);
                    messageBranch.commit();
                    userData.getKeyStore().commit();
                    messenger.publishMessageBranch(messageBranch);
                    messenger.getAppContext().commit();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        getChildren().add(receiverLayout);
        getChildren().add(bodyText);
        getChildren().add(sendButton);
    }
}

class MessageBranchView extends VBox {
    final UserData userData;
    final MessageBranchEntry messageBranchEntry;
    final MessageBranch messageBranch;

    final ListView<Message> messageListView = new ListView<>();

    final StorageDir.IListener storageListener = new StorageDir.IListener() {
        @Override
        public void onTipChanged(DatabaseDiff diff) {
            update();
        }
    };

    public MessageBranchView(final UserData userData, final MessageBranchEntry messageBranchEntry)
            throws IOException, CryptoException {
        this.userData = userData;
        this.messageBranchEntry = messageBranchEntry;
        this.messageBranch = messageBranchEntry.getMessageBranch(userData);

        setBackground(new Background(new BackgroundFill(Color.WHITE, null, null)));

        VBox.setVgrow(messageListView, Priority.ALWAYS);
        messageListView.setCellFactory(new Callback<ListView<Message>, ListCell<Message>>() {

            @Override
            public ListCell<Message> call(ListView<Message> contactPublicListView) {
                TextFieldListCell textFieldCell = new TextFieldListCell<>(new StringConverter<Message>() {

                    @Override
                    public String toString(Message message) {
                        try {
                            String senderId = message.getSender();
                            String user = "Me";
                            if (!senderId.equals(userData.getMyself().getId())) {
                                ContactPublic contactPublic = userData.getContactStore().getContactList().get(senderId);
                                user = contactPublic.getRemotes().getDefault().getUser();
                            }
                            return user + ": " + message.getBody();
                        } catch (IOException e) {
                            return "ERROR: Failed to load!";
                        }
                    }

                    @Override
                    public Message fromString(String branch) {
                        return null;
                    }
                });

                textFieldCell.setId("message-body");
                //textFieldCell.getStyleClass().add("messagebody");
                return textFieldCell;
            }
        });

        // Added Title to Group Chat (participants name)
        final Label participantsLabel = new Label();
        participantsLabel.setId("participants-label");
        String labelString = "";
        for (ContactPublic cp : messageBranch.getParticipants()){
            if (!labelString.equals("")) {
                labelString = labelString + ", ";
            }
            labelString = labelString + cp.getRemotes().getDefault().getUser(); // TODO should be a string builder
        }
        participantsLabel.setText(labelString);

        getChildren().add(participantsLabel);
        getChildren().add(messageListView);

        final TextArea messageTextArea = new TextArea();
        getChildren().add((messageTextArea));
        messageTextArea.setId("message-text-area");

        Button sendButton = new Button("Send");
        sendButton.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent actionEvent) {
                String body = messageTextArea.getText();
                if (body.equals(""))
                    return;
                try {
                    Message message = Message.create(userData.getContext(), userData.getMyself());
                    message.setBody(body);
                    messageBranch.addMessage(message);
                    messageBranch.commit();
                } catch (Exception e) {
                    e.printStackTrace();
                    return;
                }
                messageTextArea.setText("");
            }
        });
        getChildren().add(sendButton);
        setAlignment(Pos.BOTTOM_RIGHT);
        setId("send-btn-panel");

        messageBranch.getStorageDir().addListener(storageListener);
        update();
    }

    private void update() {
        messageListView.getItems().clear();
        try {
            List<Message> messages = new ArrayList<>(messageBranch.getMessages().getEntries());
            Collections.sort(messages, new Comparator<Message>() {
                @Override
                public int compare(Message message, Message message2) {
                    try {
                        Long time1 = message.getTime();
                        Long time2 = message2.getTime();
                        return time1.compareTo(time2);
                    } catch (IOException e) {
                        return 0;
                    }
                }
            });
            messageListView.getItems().addAll(messages);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (CryptoException e) {
            e.printStackTrace();
        }
    }
}

public class MessengerView extends SplitPane {
    final private Client client;
    final private IStatusManager statusManager;
    final private Messenger messenger;
    final ListView<MessageThread> branchListView;
    private MessageBranchView currentMessageBranchView;

    final private StorageDir.IListener listener = new StorageDir.IListener() {
        @Override
        public void onTipChanged(DatabaseDiff diff) {
            update();
        }
    };

    public MessengerView(final Client client, IStatusManager statusManager) {
        this.client = client;
        this.statusManager = statusManager;

        messenger = new Messenger(client);

        branchListView = new ListView<>();
        VBox.setVgrow(branchListView, Priority.ALWAYS);
        update();
        client.getUserData().getStorageDir().addListener(listener);

        final StackPane messageViewStack = new StackPane();
        final CreateMessageBranchView createMessageBranchView = new CreateMessageBranchView(client.getUserData(), messenger);

        messageViewStack.getChildren().add(createMessageBranchView);

        // Create the button for adding a new message and set the id to change the image background in css to icon
        Button createMessageBranchButton = new Button();
        createMessageBranchButton.setId("new-message-btn");
        createMessageBranchButton.setMinWidth(25.0);
        createMessageBranchButton.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent actionEvent) {
                createMessageBranchView.toFront();
            }
        });

        // Create label "messages" above the list of messages
        Label messageLabel = new Label("Messages");
        final TextField searchField = new TextField("Search");
        Button searchButton = new Button("-O"); // TODO Make a listener for this
        messageLabel.setId("message-label");

        final VBox branchLayout = new VBox();
        VBox branchNamedLayout = new VBox();
        HBox messageTitle = new HBox();
        HBox searchBox = new HBox();

        searchBox.getChildren().add(searchField);
        searchBox.getChildren().add(searchButton);
        messageTitle.getChildren().add(messageLabel);
        messageTitle.getChildren().add(createMessageBranchButton);
        messageTitle.setAlignment(Pos.CENTER_RIGHT);
        branchLayout.getChildren().add(messageTitle);
        branchLayout.getChildren().add(searchBox);
        branchLayout.getChildren().add(branchListView); // This is where the list view is added

        // TODO Make a text box for text to be entered, then make a button to be used to search
        // TODO apply a listener to that button, and on click search through branchListView
        // TODO place this inside listener
        searchButton.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                ListView<MessageThread> searchedBranchListView = new ListView<>();
                String searchedString = searchField.getText();
                for (MessageThread mt : branchListView.getItems()){
                    if (mt.containsParticipant(searchedString)){
                        searchedBranchListView.getItems().add(mt);
                    }
                }
                branchLayout.getChildren().remove(branchListView); // TODO needs to also remove the added searchedBranchListView if added
                branchLayout.getChildren().add(searchedBranchListView); // TODO also needs the listener to work, so maybe change branchListViews items???
            }
        });

        branchListView.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<MessageThread>() {
            @Override
            public void changed(ObservableValue<? extends MessageThread> observableValue,
                                MessageThread messageBranchEntry, MessageThread newEntry) {
                if (newEntry == null)
                    return;
                if (currentMessageBranchView != null)
                    messageViewStack.getChildren().remove(currentMessageBranchView);

                try {
                    currentMessageBranchView = new MessageBranchView(client.getUserData(), newEntry.getMessageBranchEntry());
                    messageViewStack.getChildren().add(currentMessageBranchView);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

//        getItems().add(branchNamedLayout);
        getItems().add(branchLayout);
        getItems().add(messageViewStack);

        // Set the top message to be the active selected message when the user opens the messages tab
        try {
            branchListView.getSelectionModel().select(branchListView.getItems().get(0));
        } catch (IndexOutOfBoundsException e){
            // If there are no messages then skip
        }

        setDividerPosition(0, 0.3);
    }

    private void update() {
        branchListView.getItems().clear();
        // Get all the entries as participants add to collection then add to branch list view
        try {
            // For each thread, get the message branch and then participants of those message branches, then add those to the MessageThread object and then into the ListView
            for (MessageBranchEntry m : messenger.getBranchList().getEntries()){
                String participants = "";
                MessageThread tempMessageThread = new MessageThread();
                for (ContactPublic participant : m.getMessageBranch(client.getUserData()).getParticipants()) {
                    participants = participants + participant.getRemotes().getDefault().getUser(); //TODO should probably be a string builder
                }
                tempMessageThread.setMessageBranchEntry(m);
                tempMessageThread.setParticipants(participants);
                branchListView.getItems().add(tempMessageThread);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (CryptoException e) {
            e.printStackTrace();
        }
        List<BranchInfo.Location> locations = new ArrayList<>();
        for (MessageBranchEntry entry : messenger.getBranchList().getEntries()) {
            try {
                BranchInfo branchInfo = entry.getMessageBranchInfo(client.getUserData());
                if (branchInfo == null)
                    continue;
                locations.addAll(branchInfo.getLocationEntries());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        try {
            messenger.getAppContext().watchBranches(client.getSyncManager(), locations);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

class MessageThread {
    private String participants;
    private MessageBranchEntry messageBranchEntry;

    MessageThread(){
        // Default constructor
    }

    void setParticipants(String participants){
        this.participants = participants;
    }

    void setMessageBranchEntry(MessageBranchEntry messageBranchEntry){
        this.messageBranchEntry = messageBranchEntry;
    }

    MessageBranchEntry getMessageBranchEntry() {
        return messageBranchEntry;
    }

    @Override
    public String toString(){
        return participants;
    }

    Boolean containsParticipant(String participant){
        return participants.contains(participant);
    }


}
