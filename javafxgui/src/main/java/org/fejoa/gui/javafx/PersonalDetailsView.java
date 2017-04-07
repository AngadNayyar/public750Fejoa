/*
 * Copyright 2017.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.gui.javafx;

import java8.util.concurrent.CompletableFuture;
import java8.util.concurrent.CompletionStage;
import java8.util.function.BiFunction;
import java8.util.function.Consumer;
import java8.util.function.Function;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import org.fejoa.chunkstore.HashValue;
import org.fejoa.library.*;
import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.database.DatabaseDiff;
import org.fejoa.library.database.StorageDir;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.Executor;


class PersonalDetails {
    final static private String NICK_NAME_KEY = "nickName";
    final static private String NAME_KEY = "name";
    final static private String SURNAME_KEY = "surname";

    final private JSONObject storage;

    public PersonalDetails(JSONObject storage) {
        this.storage = storage;
    }

    public String getNickName() {
        if (storage.has(NICK_NAME_KEY))
            return storage.getString(NICK_NAME_KEY);
        return "";
    }

    public void setNickName(String value) {
        storage.put(NICK_NAME_KEY, value);
    }

    public String getName() {
        if (storage.has(NAME_KEY))
            return storage.getString(NAME_KEY);
        return "";
    }

    public void setName(String value) {
        storage.put(NAME_KEY, value);
    }

    public String getSurname() {
        if (storage.has(SURNAME_KEY))
            return storage.getString(SURNAME_KEY);
        return "";
    }

    public void setSurname(String value) {
        storage.put(SURNAME_KEY, value);
    }
}


public class PersonalDetailsView extends VBox {
    final private Client client;
    final private TextField nickName = new TextField();
    final private TextField name = new TextField();
    final private TextField surname = new TextField();

    public PersonalDetailsView(final Client client) {
        this.client = client;
        final UserData userData = client.getUserData();
        final PersonalDetailsManager personalDetailsManager = userData.getMyself().getPersonalDetails(client);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(0, 10, 0, 10));

        grid.add(new Label("Nick name:"), 0, 1);
        grid.add(nickName, 1, 1);
        grid.add(new Label("Name:"), 0, 2);
        grid.add(name, 1, 2);
        grid.add(new Label("Surname:"), 0, 3);
        grid.add(surname, 1, 3);

        final Button applyButton = new Button("Apply");
        final Executor executor = client.getContext().getContextExecutor();
        applyButton.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent actionEvent) {
                applyButton.setDisable(true);
                final CompletableFuture<PersonalDetailsBranch> detailsBranchFuture
                        = personalDetailsManager.get(PersonalDetailsManager.DEFAULT_ENTRY).getOrCreate();
                detailsBranchFuture.thenComposeAsync(
                        new Function<PersonalDetailsBranch, CompletionStage<JSONObject>>() {
                    @Override
                    public CompletionStage<JSONObject> apply(PersonalDetailsBranch detailsBranch) {
                        return detailsBranch.getDetails();
                    }
                }, executor).thenComposeAsync(new Function<JSONObject, CompletableFuture<JSONObject>>() {
                    @Override
                    public CompletableFuture<JSONObject> apply(JSONObject object) {
                        boolean changed = false;
                        PersonalDetails personalDetails = new PersonalDetails(object);
                        if (!nickName.getText().equals(personalDetails.getNickName())) {
                            changed = true;
                            personalDetails.setNickName(nickName.getText());
                        }
                        if (!name.getText().equals(personalDetails.getName())) {
                            changed = true;
                            personalDetails.setName(name.getText());
                        }
                        if (!surname.getText().equals(personalDetails.getSurname())) {
                            changed = true;
                            personalDetails.setSurname(surname.getText());
                        }
                        if (!changed)
                            return CompletableFuture.completedFuture(null);

                        return CompletableFuture.completedFuture(object);
                    }
                }, executor).thenComposeAsync(new Function<JSONObject, CompletionStage<HashValue>>() {
                    @Override
                    public CompletionStage<HashValue> apply(JSONObject object) {
                        if (object == null)
                            return CompletableFuture.completedFuture(null);
                        PersonalDetailsBranch detailsBranch = detailsBranchFuture.join();
                        detailsBranch.setDetails(object);
                        return detailsBranch.commit();
                    }
                }, executor).handleAsync(new BiFunction<HashValue, Throwable, Void>() {
                    @Override
                    public Void apply(HashValue hashValue, Throwable throwable) {
                        applyButton.setDisable(false);
                        if (hashValue != null) {
                            // start listening for changes
                            update(personalDetailsManager);
                        }
                        return null;
                    }
                }, executor);
            }
        });

        final Button shareButton = new Button("Share with contacts");
        shareButton.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent actionEvent) {
                shareButton.setDisable(true);
                final CompletableFuture<PersonalDetailsBranch> detailsBranchFuture = personalDetailsManager.get(
                        PersonalDetailsManager.DEFAULT_ENTRY).getOrCreate();
                detailsBranchFuture.thenAcceptAsync(new Consumer<PersonalDetailsBranch>() {
                    @Override
                    public void accept(PersonalDetailsBranch detailsBranch) {
                        try {
                            detailsBranch.publishDetails(client,
                                    userData.getContactStore().getContactList().getEntries());
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }, executor).handleAsync(new BiFunction<Void, Throwable, Void>() {
                    @Override
                    public Void apply(Void aVoid, Throwable throwable) {
                        try {
                            if (throwable != null)
                                return null;
                            PersonalDetailsBranch detailsBranch = detailsBranchFuture.join();
                            try {
                                postUpdate(client, detailsBranch.getBranch());
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                            return null;
                        } finally {
                            shareButton.setDisable(false);
                        }
                    }
                }, executor);
            }
        });

        getChildren().add(grid);
        getChildren().add(applyButton);
        getChildren().add(shareButton);

        update(personalDetailsManager);
    }

    private void postUpdate(Client client, String branch) throws IOException, CryptoException {
        UserData userData = client.getUserData();
        for (ContactPublic contactPublic : userData.getContactStore().getContactList().getEntries())
            client.postBranchUpdate(contactPublic, branch, PersonalDetailsManager.PERSONAL_DETAILS_CONTEXT);
    }

    private StorageDir.IListener defaultBranchListener;

    private void update(final PersonalDetailsManager personalDetailsManager) {
        final Executor executor = client.getContext().getContextExecutor();
        PersonalDetailsManager.Entry entry = personalDetailsManager.get(PersonalDetailsManager.DEFAULT_ENTRY);
        entry.getDetailsBranch().thenComposeAsync(new Function<PersonalDetailsBranch, CompletableFuture<JSONObject>>() {
            @Override
            public CompletableFuture<JSONObject> apply(PersonalDetailsBranch detailsBranch) {
                // install listener
                if (defaultBranchListener == null) {
                    defaultBranchListener = new StorageDir.IListener() {
                        @Override
                        public void onTipChanged(DatabaseDiff diff) {
                            update(personalDetailsManager);
                        }
                    };
                    detailsBranch.getStorageDir().addListener(defaultBranchListener);
                }
                return detailsBranch.getDetails();
            }
        }, executor).thenAcceptAsync(new Consumer<JSONObject>() {
            @Override
            public void accept(JSONObject object) {
                PersonalDetails personalDetails = new PersonalDetails(object);
                nickName.setText(personalDetails.getNickName());
                name.setText(personalDetails.getName());
                surname.setText(personalDetails.getSurname());
            }
        }, executor);
    }
}
