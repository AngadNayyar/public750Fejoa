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
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.Callback;
import javafx.util.StringConverter;
import org.fejoa.filestorage.CheckoutDir;
import org.fejoa.filestorage.FileStorageEntry;
import org.fejoa.filestorage.FileStorageManager;
import org.fejoa.filestorage.Index;
import org.fejoa.gui.IStatusManager;
import org.fejoa.gui.StatusManagerMessenger;
import org.fejoa.library.*;
import org.fejoa.library.command.AccessCommandHandler;
import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.database.DatabaseDiff;
import org.fejoa.library.database.StorageDir;
import org.fejoa.library.remote.ChunkStorePullJob;
import org.fejoa.library.support.Task;

import java.io.File;
import java.io.IOException;


class ContactFileStorageView extends VBox {
    static public class Entry extends TreeItem<String> {
        final public BranchInfo.Location location;

        public Entry(String name) {
            super(name);

            location = null;
        }

        public Entry(String name, BranchInfo.Location location) {
            super(name);

            this.location = location;
        }
    }

    final private Client client;
    final private UserData userData;
    final private HBox optionLayout = new HBox();
    final private TreeView<String> treeView = new TreeView<>();

    final private StorageDir.IListener listener = new StorageDir.IListener() {
        @Override
        public void onTipChanged(DatabaseDiff diff, String base, String tip) {
            update();
        }
    };

    public ContactFileStorageView(Client client) {
        this.client = client;
        this.userData = client.getUserData();
        userData.getStorageDir().addListener(listener);

        final Entry item = new Entry("Contact File Storage");
        item.setExpanded(true);
        treeView.setRoot(item);

        getChildren().add(optionLayout);
        getChildren().add(treeView);

        final Button checkoutButton = new Button("Check Out");
        checkoutButton.setDisable(true);
        optionLayout.getChildren().add(checkoutButton);
        checkoutButton.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent actionEvent) {
                try {
                    Entry selectedEntry = (Entry)treeView.getSelectionModel().getSelectedItem();
                    checkOut(selectedEntry.location);
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (CryptoException e) {
                    e.printStackTrace();
                }
            }
        });

        treeView.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<TreeItem<String>>() {
            @Override
            public void changed(ObservableValue<? extends TreeItem<String>> observable, TreeItem<String> old,
                                TreeItem<String> newItem) {
                if (newItem == null) {
                    checkoutButton.setDisable(true);
                    return;
                }
                Entry entryItem = (Entry)newItem;
                checkoutButton.setDisable(entryItem.location == null);
            }
        });
        update();
    }

    private void checkOut(final BranchInfo.Location location) throws IOException, CryptoException {
        client.pullContactBranch(location.getRemote(), location, new Task.IObserver<Void, ChunkStorePullJob.Result>() {
            @Override
            public void onProgress(Void aVoid) {

            }

            @Override
            public void onResult(ChunkStorePullJob.Result result) {
                try {
                    String branchName = location.getBranchInfo().getBranch();
                    File destination = new File(client.getContext().getHomeDir(), branchName);
                    StorageDir indexStorage = client.getContext().getPlainStorage(new File(destination, ".index"),
                            branchName);
                    Index index = new Index(indexStorage);
                    CheckoutDir checkoutDir = new CheckoutDir(userData.getStorageDir(location.getBranchInfo()), index, destination);
                    checkoutDir.checkOut().start(new Task.IObserver<CheckoutDir.Update, CheckoutDir.Result>() {
                        @Override
                        public void onProgress(CheckoutDir.Update update) {

                        }

                        @Override
                        public void onResult(CheckoutDir.Result result) {

                        }

                        @Override
                        public void onException(Exception exception) {
                            exception.printStackTrace();
                        }
                    });
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (CryptoException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onException(Exception exception) {
                exception.printStackTrace();
            }
        });
    }

    private void update() {
        treeView.getRoot().getChildren().clear();
        for (ContactPublic contactPublic : userData.getContactStore().getContactList().getEntries()) {
            Remote remote = contactPublic.getRemotes().getDefault();
            String remoteString = remote.toAddress();
            Entry item = new Entry(contactPublic.getId() + " (" + remoteString + ")");
            int nContactBranches = 0;
            try {
                for (BranchInfo branchInfo : contactPublic.getBranchList().getEntries()) {
                    nContactBranches++;
                    Entry branchItem = new Entry(branchInfo.getBranch());
                    item.getChildren().add(branchItem);
                    for (BranchInfo.Location location : branchInfo.getLocations().getEntries()) {
                        Entry locationItem = new Entry(location.getRemote().toAddress(), location);
                        branchItem.getChildren().add(locationItem);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (nContactBranches > 0)
                treeView.getRoot().getChildren().add(item);
        }
    }

}

public class FileStorageView extends VBox {
    final private Client client;
    final StatusManagerMessenger statusManager;

    final private FileStorageManager fileStorageManager;
    final private StorageDir.IListener listener = new StorageDir.IListener() {
        @Override
        public void onTipChanged(DatabaseDiff diff, String base, String tip) {
            update();
        }
    };

    final ListView<FileStorageEntry> fileStorageListView;
    final ContactFileStorageView contactFileStorageListView;

    public FileStorageView(Client client, IStatusManager statusManager) {
        this.client = client;
        this.statusManager = new StatusManagerMessenger(statusManager);

        fileStorageManager = new FileStorageManager(client);
        fileStorageManager.addAccessGrantedHandler(new AccessCommandHandler.IContextHandler() {
            @Override
            public boolean handle(String senderId, BranchInfo branchInfo) throws Exception {
                return true;
            }
        });

        // create layout
        HBox createLayout = new HBox();
        final TextField pathTextArea = new TextField();
        Button createStorageButton = new Button("Create Storage");
        createStorageButton.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent actionEvent) {
                try {
                    fileStorageManager.createNewStorage(pathTextArea.getText());
                }  catch (Exception e) {
                    e.printStackTrace();
                    FileStorageView.this.statusManager.error(e);
                }
            }
        });
        createLayout.getChildren().add(pathTextArea);
        createLayout.getChildren().add(createStorageButton);

        // storage list
        fileStorageListView = new ListView<>();
        fileStorageListView.setCellFactory(new Callback<ListView<FileStorageEntry>, ListCell<FileStorageEntry>>() {
            @Override
            public ListCell<FileStorageEntry> call(ListView<FileStorageEntry> contactPublicListView) {
                return new TextFieldListCell<>(new StringConverter<FileStorageEntry>() {
                    @Override
                    public String toString(FileStorageEntry entry) {
                        return entry.getBranch() + " -> " + entry.getPath().getPath();
                    }

                    @Override
                    public FileStorageEntry fromString(String branch) {
                        return null;
                    }
                });
            }
        });

        // contact list
        contactFileStorageListView = new ContactFileStorageView(client);

        // sync layout
        HBox syncLayout = new HBox();
        final Button syncButton = new Button("Sync");
        syncButton.setDisable(true);
        syncButton.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent actionEvent) {
                for (FileStorageEntry entry : fileStorageListView.getSelectionModel().getSelectedItems())
                    sync(entry);
            }
        });
        final MenuButton shareButton = new MenuButton("Share");
        shareButton.setDisable(true);

        syncLayout.getChildren().add(syncButton);
        syncLayout.getChildren().add(shareButton);

        fileStorageListView.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<FileStorageEntry>() {
            @Override
            public void changed(ObservableValue<? extends FileStorageEntry> observableValue, FileStorageEntry entry,
                                FileStorageEntry newEntry) {
                syncButton.setDisable(newEntry == null);
                shareButton.setDisable(newEntry == null);
                if (newEntry != null)
                    updateShareMenu(shareButton, newEntry);
            }
        });

        getChildren().add(createLayout);
        getChildren().add(syncLayout);
        getChildren().add(fileStorageListView);
        getChildren().add(contactFileStorageListView);

        client.getUserData().getStorageDir().addListener(listener);
        update();
    }

    private void updateShareMenu(MenuButton shareButton, final FileStorageEntry entry) {
        shareButton.getItems().clear();
        for (final ContactPublic contactPublic : client.getUserData().getContactStore().getContactList().getEntries()) {
            Remote remote = contactPublic.getRemotes().getDefault();
            String remoteString = remote.getUser() + "@" + remote.getServer();
            MenuItem item = new MenuItem(remoteString + ": " + contactPublic.getId());
            shareButton.getItems().add(item);
            item.setOnAction(new EventHandler<ActionEvent>() {
                @Override
                public void handle(ActionEvent actionEvent) {
                    try {
                        fileStorageManager.grantAccess(entry, BranchAccessRight.PULL_PUSH, contactPublic);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
        }
    }

    private void update() {
        fileStorageListView.getItems().clear();
        fileStorageListView.getItems().addAll(fileStorageManager.getFileStorageList().getEntries());
    }

    private void sync(FileStorageEntry entry) {
        try {
            fileStorageManager.sync(entry, new Task.IObserver<CheckoutDir.Update, CheckoutDir.Result>() {
                @Override
                public void onProgress(CheckoutDir.Update update) {

                }

                @Override
                public void onResult(CheckoutDir.Result result) {

                }

                @Override
                public void onException(Exception exception) {
                    exception.printStackTrace();
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
