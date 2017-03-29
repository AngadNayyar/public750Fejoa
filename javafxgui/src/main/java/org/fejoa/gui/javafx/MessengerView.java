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
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldListCell;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.util.Callback;
import javafx.util.StringConverter;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;


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
        Button sendButton = new Button("Send");
        sendButton.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent actionEvent) {
                try {
                    List<String> receivers = new ArrayList<>();
                    for (String receiver : receiverTextField.getText().split(","))
                        receivers.add(receiver.trim());

                    List<ContactPublic> participants = new ArrayList<>();
                    for (ContactPublic contactPublic : userData.getContactStore().getContactList().getEntries()) {
                        if (receivers.contains(contactPublic.getRemotes().getDefault().toAddress()))
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
                return new TextFieldListCell<>(new StringConverter<Message>() {
                    @Override
                    public String toString(Message message) {
                        try {
                            String senderId = message.getSender();
                            String user = "Me";
                            if (!senderId.equals(userData.getMyself().getId())) {
                                ContactPublic contactPublic = userData.getContactStore().getContactList().get(senderId);
                                if (contactPublic != null)
                                    user = contactPublic.getRemotes().getDefault().getUser();
                                else
                                    user = "Unknown";
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
            }
        });

        getChildren().add(messageListView);

        final TextArea messageTextArea = new TextArea();
        getChildren().add((messageTextArea));

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
    final ListView<MessageBranchEntry> branchListView;
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

        Button createMessageBranchButton = new Button("New Thread");
        createMessageBranchButton.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent actionEvent) {
                createMessageBranchView.toFront();
            }
        });
        VBox branchLayout = new VBox();
        branchLayout.getChildren().add(createMessageBranchButton);
        branchLayout.getChildren().add(branchListView);

        branchListView.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<MessageBranchEntry>() {
            @Override
            public void changed(ObservableValue<? extends MessageBranchEntry> observableValue,
                                MessageBranchEntry messageBranchEntry, MessageBranchEntry newEntry) {
                if (newEntry == null)
                    return;
                if (currentMessageBranchView != null)
                    messageViewStack.getChildren().remove(currentMessageBranchView);

                try {
                    currentMessageBranchView = new MessageBranchView(client.getUserData(), newEntry);
                    messageViewStack.getChildren().add(currentMessageBranchView);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        getItems().add(branchLayout);
        getItems().add(messageViewStack);

        setDividerPosition(0, 0.3);
    }

    private void update() {
        branchListView.getItems().clear();
        branchListView.getItems().addAll(messenger.getBranchList().getEntries());

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
