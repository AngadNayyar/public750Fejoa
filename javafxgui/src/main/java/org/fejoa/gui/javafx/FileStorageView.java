/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.gui.javafx;

import java8.util.function.BiConsumer;
import java8.util.function.Consumer;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Orientation;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Callback;
import org.fejoa.filestorage.*;
import org.fejoa.gui.IStatusManager;
import org.fejoa.gui.StatusManagerMessenger;
import org.fejoa.library.*;
import org.fejoa.library.command.AccessCommandHandler;
import org.fejoa.library.database.DatabaseDiff;
import org.fejoa.library.database.StorageDir;
import org.fejoa.library.support.Task;

import java.io.File;
import java.io.IOException;
import java.util.Collection;


abstract class TreeObject<T extends TreeObject<?>> {
    abstract public String getName();
    abstract public ObservableList<T> getItems();
}

class OwnStorageList extends TreeObject<StorageEntry> {
    final private ObservableList<StorageEntry> list = FXCollections.observableArrayList();

    public OwnStorageList(ContactFileStorage contactFileStorage) {
        list.clear();
        try {
            for (FileStorageEntry entry : contactFileStorage.getStorageList().getEntries())
                list.add(new StorageEntry(entry));
        } catch (Exception e) {

        }
    }

    @Override
    public String getName() {
        return "Own Storage";
    }

    @Override
    public String toString() {
        return getName();
    }

    @Override
    public ObservableList<StorageEntry> getItems() {
        return list;
    }
}

class ContactStorageList extends OwnStorageList {
    final String name;

    public ContactStorageList(ContactFileStorage contactFileStorage) {
        super(contactFileStorage);

        IContactPublic contact = contactFileStorage.getContact();
        Remote remote = contact.getRemotes().getDefault();
        String remoteString = remote.toAddress();
        this.name =  remoteString + " (" + contact.getId() + ")";
    }

    @Override
    public String getName() {
        return name;
    }
}

class StorageEntry extends TreeObject<StorageLocationEntry> {
    final private FileStorageEntry entry;
    final private ObservableList<StorageLocationEntry> list = FXCollections.observableArrayList();

    public StorageEntry(FileStorageEntry entry) {
        this.entry = entry;

        list.clear();
        try {
            for (BranchInfo.Location location : entry.getBranchInfo().getLocationEntries())
                list.add(new StorageLocationEntry(location));
        } catch (Exception e) {

        }
    }

    public FileStorageEntry getEntry() {
        return entry;
    }

    @Override
    public String getName() {
        try {
            return "Branch: " + entry.getBranch() + " -> " + entry.getPath().get();
        } catch (Exception e) {
            return "Failed tor read branch info!";
        }
    }

    @Override
    public String toString() {
        return getName();
    }

    @Override
    public ObservableList<StorageLocationEntry> getItems() {
        return list;
    }
}

class StorageLocationEntry extends TreeObject<TreeObject<?>> {
    final private BranchInfo.Location location;

    public StorageLocationEntry(BranchInfo.Location location) {
        this.location = location;
    }

    @Override
    public String getName() {
        try {
            return "Location: " + location.getRemote().toAddress();
        } catch (IOException e) {
            return "failed to load remote";
        }
    }

    public BranchInfo.Location getLocation() {
        return location;
    }

    @Override
    public String toString() {
        return getName();
    }

    @Override
    public ObservableList<TreeObject<?>> getItems() {
        return FXCollections.emptyObservableList();
    }
}

class ContactStorageEntryList extends TreeObject<OwnStorageList> {
    final private ObservableList<OwnStorageList> list = FXCollections.observableArrayList();

    public ContactStorageEntryList(UserData userData, FileStorageManager storageManager) {
        list.clear();
        for (ContactPublic contactPublic : userData.getContactStore().getContactList().getEntries()) {
            ContactFileStorage contactFileStorage;
            try {
                contactFileStorage = storageManager.getContactFileStorageList().get(contactPublic.getId());
            } catch (Exception e) {
                continue;
            }
            list.add(new ContactStorageList(contactFileStorage));
        }
    }

    @Override
    public String getName() {
        return "Contact Storage";
    }

    @Override
    public String toString() {
        return getName();
    }

    @Override
    public ObservableList<OwnStorageList> getItems() {
        return list;
    }
}


class FileStorageTreeView extends TreeView<TreeObject<?>> {
    final private TreeItem<TreeObject<?>> rootNode;

    public FileStorageTreeView() {
        rootNode = new TreeItem<>();
        setRoot(rootNode);
        setShowRoot(false);

        setCellFactory(new Callback<TreeView<TreeObject<?>>, TreeCell<TreeObject<?>>>() {
            @Override
            public TreeCell<TreeObject<?>> call(TreeView<TreeObject<?>> treeObjectTreeView) {
                return new TreeCell<TreeObject<?>>() {
                    @Override
                    protected void updateItem(TreeObject<?> item, boolean empty) {
                        super.updateItem(item, empty);
                        textProperty().unbind();
                        if (empty) {
                            setGraphic(null);
                            setText(null);
                            return;
                        }
                        setText(item.getName());
                        if (item instanceof ContactStorageList) {
                            setGraphic(new ImageView(Resources.getIcon(Resources.ICON_CONTACT_32)));
                        } else if (item instanceof StorageEntry) {
                            setGraphic(new ImageView(Resources.getIcon(Resources.ICON_FOLDER_32)));
                        } else if (item instanceof StorageLocationEntry) {
                            setGraphic(new ImageView(Resources.getIcon(Resources.ICON_REMOTE_32)));
                        }
                    }
                };
            }
        });
    }

    public void setTo(UserData userData, FileStorageManager fileStorageManager) {
        rootNode.getChildren().clear();
        try {
            rootNode.getChildren().add(createTree(new OwnStorageList(fileStorageManager.getOwnFileStorage())));
        } catch (Exception e) {

        }
        rootNode.getChildren().add(createTree(new ContactStorageEntryList(userData, fileStorageManager)));
    }

    private TreeItem<TreeObject<?>> createTree(TreeObject<?> item) {
        TreeItem<TreeObject<?>> treeItem = new TreeItem<TreeObject<?>>(item);
        treeItem.setExpanded(true);
        for (TreeObject<?> subItem : item.getItems())
            treeItem.getChildren().add(createTree(subItem));
        return treeItem;
    }
}


class FileStorageInfoView extends VBox {
    final private Label checkOutDirInfo = new Label();
    final private ListView<String> sharedWithList = new ListView<>();

    public FileStorageInfoView() {
        HBox pathLayout = new HBox();
        pathLayout.getChildren().add(new Label("Checkout path:"));
        pathLayout.getChildren().add(checkOutDirInfo);

        getChildren().add(pathLayout);
        getChildren().add(sharedWithList);
    }

    public void setTo(FileStorageEntry fileStorageEntry) {
        fileStorageEntry.getPath().whenComplete(new BiConsumer<String, Throwable>() {
            @Override
            public void accept(String s, Throwable throwable) {
                if (s != null)
                    checkOutDirInfo.setText(new File(s).getAbsolutePath());
                else
                    checkOutDirInfo.setText("Failed to read path...");
            }
        });

        sharedWithList.setVisible(false);
        try {
            Collection<ContactAccess> contactAccessList = fileStorageEntry.getBranchInfo().getContactAccessList()
                    .getEntries();
            if (contactAccessList.size() > 0)
                sharedWithList.setVisible(true);
            for (ContactAccess contactAccess : contactAccessList) {
                contactAccess.getContact().thenAccept(new Consumer<String>() {
                    @Override
                    public void accept(String s) {
                        sharedWithList.getItems().add(s);
                    }
                });
            }
        } catch (Exception e) {

        }

    }
}

public class FileStorageView extends SplitPane {
    final private Client client;
    final StatusManagerMessenger statusManager;

    final private FileStorageManager fileStorageManager;
    final private StorageDir.IListener listener = new StorageDir.IListener() {
        @Override
        public void onTipChanged(DatabaseDiff diff) {
            update();
        }
    };

    final FileStorageTreeView fileStorageTreeView = new FileStorageTreeView();;
    final StackPane stackPane = new StackPane();

    public FileStorageView(final Client client, IStatusManager statusManager) {
        this.client = client;
        this.statusManager = new StatusManagerMessenger(statusManager);

        fileStorageManager = new FileStorageManager(client);
        fileStorageManager.addAccessGrantedHandler(new AccessCommandHandler.IContextHandler() {
            @Override
            public boolean handle(String senderId, BranchInfo branchInfo) throws Exception {
                IContactPublic contactPublic = client.getUserData().getContactStore().getContactFinder().get(senderId);
                if (contactPublic == null)
                    return false;
                fileStorageManager.addContactStorage(contactPublic, branchInfo.getBranch(), "");
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

        // sync layout
        HBox syncLayout = new HBox();
        final Button syncButton = new Button("Sync");
        syncButton.setDisable(true);

        final MenuButton shareButton = new MenuButton("Share");
        shareButton.setDisable(true);

        syncLayout.getChildren().add(syncButton);
        syncLayout.getChildren().add(shareButton);

        fileStorageTreeView.getSelectionModel().selectedItemProperty().addListener(
                new ChangeListener<TreeItem<TreeObject<?>>>() {
            @Override
            public void changed(ObservableValue<? extends TreeItem<TreeObject<?>>> observableValue,
                                TreeItem<TreeObject<?>> oldEntry, TreeItem<TreeObject<?>> newEntry) {
                syncButton.setDisable(true);
                shareButton.setDisable(true);
                setTo(null);
                if (newEntry != null) {
                    final FileStorageEntry fileStorageEntry = getFileStorageEntry(newEntry);
                    if (fileStorageEntry == null)
                        return;
                    setTo(fileStorageEntry);
                    boolean isOwnStorage = fileStorageEntry.getStorageOwner().getId().equals(
                            client.getUserData().getMyself().getId());
                    if (newEntry.getValue() instanceof StorageLocationEntry) {
                        final StorageLocationEntry locationEntry = (StorageLocationEntry)newEntry.getValue();
                        syncButton.setDisable(false);
                        syncButton.setOnAction(new EventHandler<ActionEvent>() {
                            @Override
                            public void handle(ActionEvent actionEvent) {
                                sync(fileStorageEntry, locationEntry.getLocation());
                            }
                        });
                    }
                    if (isOwnStorage) {
                        shareButton.setDisable(false);
                        updateShareMenu(shareButton, fileStorageEntry);
                    }
                }
            }
        });

        VBox leftLayoutLayout = new VBox();
        leftLayoutLayout.getChildren().add(createLayout);
        leftLayoutLayout.getChildren().add(fileStorageTreeView);
        VBox.setVgrow(fileStorageTreeView, Priority.ALWAYS);

        VBox rightLayout = new VBox();
        rightLayout.getChildren().add(syncLayout);
        rightLayout.getChildren().add(stackPane);
        VBox.setVgrow(stackPane, Priority.ALWAYS);

        setOrientation(Orientation.HORIZONTAL);
        getItems().addAll(leftLayoutLayout, rightLayout);
        setDividerPosition(0, 0.3);

        client.getUserData().getStorageDir().addListener(listener);
        update();
    }

    private void setTo(FileStorageEntry entry) {
        if (entry == null) {
            stackPane.getChildren().clear();
            return;
        }
        FileStorageInfoView infoView = new FileStorageInfoView();
        infoView.setTo(entry);
        stackPane.getChildren().add(infoView);
    }

    private FileStorageEntry getFileStorageEntry(TreeItem<TreeObject<?>> storageLocationEntry) {
        StorageEntry storageEntry = null;
        do {
            if (storageLocationEntry.getValue() instanceof StorageEntry) {
                storageEntry = (StorageEntry)storageLocationEntry.getValue();
                break;
            }
            storageLocationEntry = storageLocationEntry.getParent();
        } while (storageLocationEntry.getParent() != null);
        if (storageEntry == null)
            return null;
        return storageEntry.getEntry();
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
        fileStorageTreeView.setTo(client.getUserData(), fileStorageManager);
    }

    private void sync(FileStorageEntry entry, BranchInfo.Location location) {
        try {
            fileStorageManager.sync(entry, location, true,
                    new Task.IObserver<CheckoutDir.Update, CheckoutDir.Result>() {
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
