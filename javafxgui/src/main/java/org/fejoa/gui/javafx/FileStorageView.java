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
import javafx.geometry.Orientation;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldListCell;
import javafx.scene.image.ImageView;
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


abstract class TreeObject<T extends TreeObject<?>> {
    abstract public String getName();
    abstract public ObservableList<T> getItems();
}

class OwnStorageList extends TreeObject<OwnStorageEntry> {
    final private FileStorageManager fileStorageManager;

    final private ObservableList<OwnStorageEntry> list = FXCollections.observableArrayList();

    public OwnStorageList(FileStorageManager storageManager) {
        this.fileStorageManager = storageManager;

        list.clear();
        for (FileStorageEntry entry : fileStorageManager.getFileStorageList().getEntries())
            list.add(new OwnStorageEntry(entry));
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
    public ObservableList<OwnStorageEntry> getItems() {
        return list;
    }
}

class OwnStorageEntry extends TreeObject<TreeObject<?>> {
    final private FileStorageEntry entry;

    public OwnStorageEntry(FileStorageEntry entry) {
        this.entry = entry;
    }

    @Override
    public String getName() {
        return "Branch: " + entry.getBranch() + " -> " + entry.getPath().getPath();
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

class ContactStorageEntryList extends TreeObject<ContactTreeEntry> {
    final private ObservableList<ContactTreeEntry> list = FXCollections.observableArrayList();

    public ContactStorageEntryList(UserData userData) {
        list.clear();
        for (ContactPublic contactPublic : userData.getContactStore().getContactList().getEntries()) {
            ContactTreeEntry contactTreeEntry = new ContactTreeEntry(contactPublic);
            if (contactTreeEntry.getItems().size() > 0)
                list.add(contactTreeEntry);
            /*
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
                list.add(item);*/
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
    public ObservableList<ContactTreeEntry> getItems() {
        return list;
    }
}


class ContactTreeEntry extends TreeObject<ContactStorageEntry> {
    final private ContactPublic contactPublic;
    final private String name;
    final private ObservableList<ContactStorageEntry> list = FXCollections.observableArrayList();

    public ContactTreeEntry(ContactPublic contactPublic) {
        this.contactPublic = contactPublic;

        Remote remote = contactPublic.getRemotes().getDefault();
        String remoteString = remote.toAddress();
        this.name =  remoteString + " (" + contactPublic.getId() + ")";

        try {
            for (BranchInfo branchInfo : contactPublic.getBranchList().getEntries(FileStorageManager.STORAGE_CONTEXT))
                list.add(new ContactStorageEntry(branchInfo));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return getName();
    }

    @Override
    public ObservableList<ContactStorageEntry> getItems() {
        return list;
    }
}

class ContactStorageEntry extends TreeObject<ContactStorageLocationEntry> {
    final private BranchInfo branchInfo;
    final private ObservableList<ContactStorageLocationEntry> list = FXCollections.observableArrayList();

    public ContactStorageEntry(BranchInfo branchInfo) {
        this.branchInfo = branchInfo;

        try {
            for (BranchInfo.Location location : branchInfo.getLocations().getEntries())
                list.add(new ContactStorageLocationEntry(location));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public String getName() {
        return "Branch: " + branchInfo.getBranch();
    }

    @Override
    public String toString() {
        return getName();
    }

    @Override
    public ObservableList<ContactStorageLocationEntry> getItems() {
        return list;
    }
}

class ContactStorageLocationEntry extends TreeObject<TreeObject<?>> {
    final private BranchInfo.Location location;

    public ContactStorageLocationEntry(BranchInfo.Location location) {
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

    @Override
    public String toString() {
        return getName();
    }

    @Override
    public ObservableList<TreeObject<?>> getItems() {
        return FXCollections.emptyObservableList();
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
                        if (item instanceof ContactTreeEntry) {
                            setGraphic(new ImageView(Resources.getIcon(Resources.ICON_CONTACT_32)));
                        } else if (item instanceof ContactStorageEntry || item instanceof OwnStorageEntry) {
                            setGraphic(new ImageView(Resources.getIcon(Resources.ICON_FOLDER_32)));
                        } else if (item instanceof ContactStorageLocationEntry) {
                            setGraphic(new ImageView(Resources.getIcon(Resources.ICON_REMOTE_32)));
                        }
                    }
                };
            }
        });
    }

    public void setTo(UserData userData, FileStorageManager fileStorageManager) {
        rootNode.getChildren().clear();
        rootNode.getChildren().add(createTree(new OwnStorageList(fileStorageManager)));
        rootNode.getChildren().add(createTree(new ContactStorageEntryList(userData)));
    }

    private TreeItem<TreeObject<?>> createTree(TreeObject<?> item) {
        TreeItem<TreeObject<?>> treeItem = new TreeItem<TreeObject<?>>(item);
        for (TreeObject<?> subItem : item.getItems())
            treeItem.getChildren().add(createTree(subItem));
        return treeItem;
    }
}

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
        public void onTipChanged(DatabaseDiff diff) {
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

        VBox rightLayout = new VBox();
        rightLayout.getChildren().add(createLayout);
        rightLayout.getChildren().add(syncLayout);
        rightLayout.getChildren().add(fileStorageListView);
        rightLayout.getChildren().add(contactFileStorageListView);

        setOrientation(Orientation.HORIZONTAL);
        getItems().addAll(fileStorageTreeView, rightLayout);
        setDividerPosition(0, 0.3);

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
        fileStorageTreeView.setTo(client.getUserData(), fileStorageManager);

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
