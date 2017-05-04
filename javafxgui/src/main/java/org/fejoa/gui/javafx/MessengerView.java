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
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldListCell;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.text.Text;
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
    //This class creates the right hand side of the messenger GUI, when the user is writing a new message.
    public CreateMessageBranchView(final UserData userData, final Messenger messenger)  {
        setBackground(new Background(new BackgroundFill(Color.WHITE, null, null)));
        setId("message-branch-view");

        HBox receiverLayout = new HBox();
        receiverLayout.setSpacing(10);
        receiverLayout.setId("receiver-layout");
        receiverLayout.setAlignment(Pos.CENTER_LEFT);
        receiverLayout.getChildren().add(new Label("Send to:"));

        final ComboBox<String> receiverComboBox = new ComboBox<>();
        for (ContactPublic cp : userData.getContactStore().getContactList().getEntries()){
            receiverComboBox.getItems().add(cp.getRemotes().getDefault().getUser());
        }
        receiverComboBox.setPromptText("Select contact");


        receiverComboBox.addEventFilter(MouseEvent.MOUSE_PRESSED, new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent mouseEvent) {
                receiverComboBox.getItems().clear();
                for (ContactPublic cp : userData.getContactStore().getContactList().getEntries()){
                    receiverComboBox.getItems().add(cp.getRemotes().getDefault().getUser());
                }
            }
        });


        final TextField receiverTextField = new TextField();
        receiverTextField.setText("@http://localhost:8180");
        HBox.setHgrow(receiverTextField, Priority.ALWAYS);
        receiverLayout.getChildren().add(receiverComboBox);
        receiverLayout.getChildren().add(receiverTextField);

        final TextArea bodyText = new TextArea();
        bodyText.setPromptText("Message body...");
        bodyText.setWrapText(true);
        Button sendButton = new Button();
        final Tooltip tooltip = new Tooltip();
        tooltip.setText("Send message");
        sendButton.setTooltip(tooltip);
        sendButton.setMinWidth(25.0);
        sendButton.getStyleClass().add("send-message-button");
        final Label errorLabel = new Label("");
        errorLabel.setId("error-label"); //TODO styling
        //Action listener for when user presses send button
        sendButton.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent actionEvent) {
                boolean matched = false;
                errorLabel.setText("");
                try {
                    List<ContactPublic> participants = new ArrayList<>();
                    //Checks to see if the contacts that user is sending a message to are valid contacts.
                    for (ContactPublic contactPublic : userData.getContactStore().getContactList().getEntries()) {
                        if (contactPublic.getRemotes().getDefault().toAddress().equals(receiverComboBox.getSelectionModel().getSelectedItem() + receiverTextField.getText())) {
                            participants.add(contactPublic);
                            matched = true;
                        }
                    }

                    if (!matched){
                        // TODO show error
                        errorLabel.setText("Sorry that contact was not found");
                        System.out.println("ERROR: Contact not valid");
                        return;
                    }
                    System.out.println("no error: Contact is valid");
                    //Each message branch is a new thread, Message is the individual messages.
                    //Here the new thread is created and new message added to it.
                    //TODO: Check for existing threads to the same user/users
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
        //Create the send attachment button and add a tooltip
        Button fileButton = new Button();
        final Tooltip tip = new Tooltip();
        tip.setText("Add an attachment");
        fileButton.setTooltip(tip);
        fileButton.setMinWidth(25.0);
        fileButton.getStyleClass().add("add-attachment-button");
        //Add the receiver box, the message body, send image, and send button to the GUI.
        HBox buttonContainer = new HBox();
        buttonContainer.setAlignment(Pos.TOP_RIGHT);
        buttonContainer.getChildren().add(errorLabel);
        buttonContainer.getChildren().add(fileButton);
        buttonContainer.getChildren().add(sendButton);
        buttonContainer.setSpacing(5);
        setSpacing(5);
        getChildren().add(receiverLayout);
        getChildren().add(bodyText);
        getChildren().add(buttonContainer);
    }

    private void updateComboBox(){

    }
}

//This is the view for when a message thread is selected, so it will show the messages in the conversation
class MessageBranchView extends VBox {
    final UserData userData;
    final MessageBranchEntry messageBranchEntry;
    final MessageBranch messageBranch;

    final ListView<Message> messageListView = new ListView<>();
    final ListView<HBox> conversationThread = new ListView<>();


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
                return textFieldCell;
            }
        });

        // Added Title to Group Chat (participants names)
        VBox participantsContainer = new VBox();
        participantsContainer.setId("participants-title-containter");
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

        participantsContainer.setAlignment(Pos.TOP_CENTER);
        participantsContainer.getChildren().add(participantsLabel);
        getChildren().add(participantsContainer);
        getChildren().add(conversationThread);
        conversationThread.setId("conversation-thread-listview");

        final TextArea messageTextArea = new TextArea();
        messageTextArea.setWrapText(true);
        messageTextArea.setPrefRowCount(3);
        getChildren().add((messageTextArea));
        messageTextArea.setId("message-text-area");
        messageTextArea.setPromptText("Type message...");

        // Create send message button, add tool tip and set the id for css
        Button sendButton = new Button();
        sendButton.setMinWidth(25.0);
        final Tooltip tooltip = new Tooltip();
        tooltip.setText("Send message");
        sendButton.setTooltip(tooltip);
        sendButton.getStyleClass().add("send-message-button");

        //Send message button action listener
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
        Button fileButton = new Button();
        final Tooltip tip = new Tooltip();
        tip.setText("Add an attachment");
        fileButton.setTooltip(tip);
        fileButton.setMinWidth(25.0);
        fileButton.getStyleClass().add("add-attachment-button");
        HBox buttonContainer = new HBox();
        buttonContainer.setAlignment(Pos.TOP_RIGHT);
        buttonContainer.getChildren().add(fileButton);
        buttonContainer.getChildren().add(sendButton);
        buttonContainer.setSpacing(5);
        setSpacing(5);

        getChildren().add(buttonContainer);
        setAlignment(Pos.BOTTOM_RIGHT);
        setId("send-btn-panel");

        messageBranch.getStorageDir().addListener(storageListener);
        update();
        try {
            conversationThread.scrollTo(conversationThread.getItems().size());
        } catch (IndexOutOfBoundsException e){

        }

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
            conversationThread.getItems().clear();

            for (int i = 0; i < messageListView.getItems().size(); i++){
                HBox messageHBox = new HBox();
                Text messageText = new Text();
                HBox textbox = new HBox();
                textbox.getChildren().add(messageText);
                Message mes = messageListView.getItems().get(i);
                messageText.setText(mes.getBody());
                Pane spacer = new Pane();
                spacer.setId("message-spacer");
                HBox.setHgrow(spacer, Priority.ALWAYS);

                if (userData.getMyself().getId().equals(mes.getSender())){
                    textbox.getStyleClass().remove("message-received");
                    textbox.getStyleClass().add("message-sent");
                    messageHBox.getChildren().add(spacer);
                    messageHBox.getChildren().add(textbox);
                    messageText.setFill(Color.WHITE);
                } else {
                    textbox.getStyleClass().remove("message-sent");
                    textbox.getStyleClass().add("message-received");
                    messageHBox.getChildren().add(textbox);
                    messageHBox.getChildren().add(spacer);
                }

                conversationThread.getItems().add(messageHBox);
            }

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
    final ListView<MessageThread> totalBranchListView = new ListView<>();
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
        branchListView.setId("branch-list-view");
        VBox.setVgrow(branchListView, Priority.ALWAYS);
        update();
        client.getUserData().getStorageDir().addListener(listener);

        branchListView.setId("thread-message-list-view");

        final StackPane messageViewStack = new StackPane();
        final CreateMessageBranchView createMessageBranchView = new CreateMessageBranchView(client.getUserData(), messenger);

        messageViewStack.getChildren().add(createMessageBranchView);

        // Create the button for adding a new message and set the id to change the image background in css to icon
        Button createMessageBranchButton = new Button();
        createMessageBranchButton.setId("new-message-btn");
        final Tooltip tooltip = new Tooltip();
        tooltip.setText("Create a new message");
        createMessageBranchButton.setTooltip(tooltip);
        createMessageBranchButton.setMinWidth(25.0);
        createMessageBranchButton.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent actionEvent) {
                createMessageBranchView.toFront();
            }
        });

        // Create label "messages" above the list of messages
        Label messageLabel = new Label("Messages");
        final TextField searchField = new TextField();
        searchField.setPromptText("Search");
        searchField.setMaxWidth(Double.MAX_VALUE);
        messageLabel.setId("message-label");

        final VBox branchLayout = new VBox();
        VBox branchNamedLayout = new VBox();
        BorderPane messageTitle = new BorderPane();
        messageTitle.setId("message-title-pane");
        VBox searchBox = new VBox();

        searchBox.getChildren().add(searchField);
        // Set the label and the message button onto the header of the messages list
        messageTitle.setCenter(messageLabel);
        messageTitle.setRight(createMessageBranchButton);
        branchLayout.getChildren().add(messageTitle);
        branchLayout.getChildren().add(searchBox);
        branchLayout.getChildren().add(branchListView); // This is where the list view is added



        // Listen for changes in the text
        searchField.textProperty().addListener(new ChangeListener<String>() {
            @Override
            public void changed(ObservableValue<? extends String> observable,
                                String oldValue, String newValue) {
                ListView<MessageThread> searchedBranchListView = new ListView<>();
                String searchedString = searchField.getText();
                for (MessageThread mt : totalBranchListView.getItems()){
                    if (mt.containsParticipant(searchedString)){
                        searchedBranchListView.getItems().add(mt);
                    }
                }
                branchLayout.getChildren().remove(branchListView);
                branchListView.getItems().clear();
                branchListView.getItems().addAll(searchedBranchListView.getItems());
                branchLayout.getChildren().add(branchListView);

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
            // If there are no messages then skip and leave as default
        }

        setDividerPosition(0, 0.3);
    }

    private void update() {
        branchListView.getItems().clear();
        totalBranchListView.getItems().clear();
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
        // Copy update listview to totalBranchListView
        totalBranchListView.getItems().addAll(branchListView.getItems());

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