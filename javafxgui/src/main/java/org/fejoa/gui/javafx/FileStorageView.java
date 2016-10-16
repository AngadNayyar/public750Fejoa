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
import javafx.collections.ListChangeListener;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.control.Button;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.TextFieldListCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.Callback;
import javafx.util.StringConverter;
import org.fejoa.filestorage.CheckoutDir;
import org.fejoa.filestorage.Index;
import org.fejoa.gui.IStatusManager;
import org.fejoa.gui.StatusManagerMessenger;
import org.fejoa.library.*;
import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.crypto.CryptoHelper;
import org.fejoa.library.database.DatabaseDiff;
import org.fejoa.library.database.DefaultCommitSignature;
import org.fejoa.library.database.IOStorageDir;
import org.fejoa.library.database.StorageDir;
import org.fejoa.library.support.Task;

import java.io.File;
import java.io.IOException;


class FileStorageEntry implements IStorageDirBundle {
    final static private String PATH_KEY = "path";

    private File path;
    private String branch;

    public FileStorageEntry() {
    }

    public FileStorageEntry(File path, BranchInfo branchInfo) {
        this.path = path;
        this.branch = branchInfo.getBranch();
    }

    public File getPath() {
        return path;
    }

    public String getBranch() {
        return branch;
    }

    public String getId() {
        return CryptoHelper.sha1HashHex(path.getPath() + branch);
    }

    @Override
    public void write(IOStorageDir dir) throws IOException, CryptoException {
        dir.writeString(PATH_KEY, path.getPath());
        dir.writeString(Constants.BRANCH_KEY, branch);
    }

    @Override
    public void read(IOStorageDir dir) throws IOException, CryptoException {
        path = new File(dir.readString(PATH_KEY));
        branch = dir.readString(Constants.BRANCH_KEY);
    }
}

public class FileStorageView extends VBox {
    final private Client client;
    final StatusManagerMessenger statusManager;
    final static private String IDENTIFIER = "org.fejoa.filestorage";

    final private StorageDirList<FileStorageEntry> storageList;
    final private StorageDir.IListener listener = new StorageDir.IListener() {
        @Override
        public void onTipChanged(DatabaseDiff diff, String base, String tip) {
            update();
        }
    };

    final ListView<FileStorageEntry> fileStorageListView;

    public FileStorageView(Client client, IStatusManager statusManager) {
        this.client = client;
        this.statusManager = new StatusManagerMessenger(statusManager);

        AppContext appContext = client.getUserData().getConfigStore().getAppContext(IDENTIFIER);
        storageList = new StorageDirList<>(appContext.getStorageDir(),
                new StorageDirList.AbstractEntryIO<FileStorageEntry>() {
            @Override
            public String getId(FileStorageEntry entry) {
                return entry.getId();
            }

            @Override
            public FileStorageEntry read(IOStorageDir dir) throws IOException, CryptoException {
                FileStorageEntry fileStorageEntry = new FileStorageEntry();
                fileStorageEntry.read(dir);
                return fileStorageEntry;
            }
        });

        // create layout
        HBox createLayout = new HBox();
        final TextField pathTextArea = new TextField();
        Button createStorageButton = new Button("Create Storage");
        createStorageButton.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent actionEvent) {
                createNewStorage(pathTextArea.getText());
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
        syncLayout.getChildren().add(syncButton);

        fileStorageListView.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<FileStorageEntry>() {
            @Override
            public void changed(ObservableValue<? extends FileStorageEntry> observableValue, FileStorageEntry entry,
                                FileStorageEntry newEntry) {
                syncButton.setDisable(newEntry == null);
            }
        });

        getChildren().add(createLayout);
        getChildren().add(syncLayout);
        getChildren().add(fileStorageListView);

        client.getUserData().getStorageDir().addListener(listener);
        update();
    }

    private void update() {
        fileStorageListView.getItems().clear();
        fileStorageListView.getItems().addAll(storageList.getEntries());
    }

    private void sync(FileStorageEntry entry) {
        try {
            File destination = entry.getPath();
            StorageDir indexStorage = client.getContext().getPlainStorage(new File(destination, ".index"),
                    entry.getBranch());
            Index index = new Index(indexStorage);
            UserData userData = client.getUserData();
            BranchInfo branchInfo = userData.findBranchInfo(entry.getBranch());
            CheckoutDir checkoutDir = new CheckoutDir(userData.getStorageDir(branchInfo), index, destination);
            Task<CheckoutDir.Update, CheckoutDir.Result> checkIn = checkoutDir.checkIn();
            checkIn.setStartScheduler(new Task.CurrentThreadScheduler());
            checkIn.start(new Task.IObserver<CheckoutDir.Update, CheckoutDir.Result>() {
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

    private void createNewStorage(String path) {
        File file = new File(path);
        /*if (file.exists()) {
            statusManager.info("File storage dir: " + file.getPath() + "exists");
            return;
        }*/

        file.mkdirs();

        try {
            FejoaContext context = client.getContext();
            UserData userData = client.getUserData();
            String branch = CryptoHelper.sha1HashHex(context.getCrypto().generateInitializationVector(32));
            SymmetricKeyData keyData = SymmetricKeyData.create(context, context.getCryptoSettings().symmetric);
            userData.getKeyStore().addSymmetricKey(keyData.keyId().toHex(), keyData);
            SigningKeyPair signingKeyPair = userData.getMyself().getSignatureKeys().getDefault();
            StorageDir storageDir = context.getStorage(branch, keyData,
                    new DefaultCommitSignature(context, signingKeyPair));
            BranchInfo branchInfo = new BranchInfo(userData.getRemoteStore(), storageDir.getBranch(), "File Storage");
            branchInfo.setCryptoInfo(keyData.keyId(), userData.getKeyStore(), true);
            Remote remote = userData.getGateway();
            branchInfo.addLocation(remote.getId(), context.getRootAuthInfo(remote));
            userData.addBranch(branchInfo);

            storageList.add(new FileStorageEntry(file, branchInfo));
            userData.commit(true);
        } catch (Exception e) {
            e.printStackTrace();
            statusManager.error(e);
        }
    }
}
