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
import javafx.geometry.Orientation;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldListCell;
import javafx.util.Callback;
import javafx.util.StringConverter;
import org.fejoa.chunkstore.*;
import org.fejoa.library.BranchInfo;
import org.fejoa.library.UserData;
import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.database.DatabaseDiff;
import org.fejoa.library.database.StorageDir;
import org.fejoa.library.support.StorageLib;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;


class BranchList extends ListView<BranchInfo> {
    final private StorageDir.IListener listener;

    public BranchList(final UserData userData) {
        listener = new StorageDir.IListener() {
            @Override
            public void onTipChanged(DatabaseDiff diff) {
                update(userData);
            }
        };

        setCellFactory(new Callback<ListView<BranchInfo>, ListCell<BranchInfo>>() {
            @Override
            public ListCell<BranchInfo> call(ListView<BranchInfo> branchInfoListView) {
                return new TextFieldListCell<>(new StringConverter<BranchInfo>() {
                    @Override
                    public String toString(BranchInfo branchInfo) {
                        return "(" + branchInfo.getDescription() + ") " + branchInfo.getBranch();
                    }

                    @Override
                    public BranchInfo fromString(String branch) {
                        return null;
                        //return userData.findBranchInfo(branch);
                    }
                });
            }
        });

        userData.getStorageDir().addListener(listener);

        update(userData);
    }

    private void update(UserData userData) {
        getItems().clear();

        try {
            for (BranchInfo branchInfo : userData.getBranchList().getEntries()) {
                getItems().add(branchInfo);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

public class HistoryView extends SplitPane {
    final private HistoryListView historyView = new HistoryListView(null);
    final private StorageDirDiffView storageDirDiffView = new StorageDirDiffView();
    final private TreeView<String> dirView = new TreeView<>();

    public HistoryView(final UserData userData) {
        BranchList branchList = new BranchList(userData);
        getItems().add(branchList);
        getItems().add(historyView);

        SplitPane diffDirSplitPane = new SplitPane(storageDirDiffView, dirView);
        diffDirSplitPane.setOrientation(Orientation.VERTICAL);

        getItems().add(diffDirSplitPane);

        setDividerPosition(0, 0.3);
        setDividerPosition(1, 0.6);

        final TreeItem<String> item = new TreeItem<> ("Tree");
        item.setExpanded(false);
        dirView.setRoot(item);

        branchList.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<BranchInfo>() {
            @Override
            public void changed(ObservableValue<? extends BranchInfo> observableValue, BranchInfo old, BranchInfo newItem) {
                StorageDir storageDir = null;
                if (newItem != null) {
                    try {
                        storageDir = userData.getStorageDir(newItem);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                historyView.setTo(storageDir);
            }
        });

        historyView.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<HistoryListView.HistoryEntry>() {
            @Override
            public void changed(ObservableValue<? extends HistoryListView.HistoryEntry> observableValue,
                                HistoryListView.HistoryEntry historyEntry, HistoryListView.HistoryEntry selectedItem) {
                if (selectedItem == null)
                    return;
                CommitBox commitBox = selectedItem.getCommitBox();
                Repository repository = (Repository) historyView.getStorageDir().getDatabase();
                List<HashValue> parents = new ArrayList<>();
                IRepoChunkAccessors.ITransaction transaction = repository.getCurrentTransaction();
                for (ChunkContainerRef parent : commitBox.getParents()) {
                    try {
                        CommitBox parentCommit = CommitBox.read(transaction.getCommitAccessor(parent), parent);
                        parents.add(parentCommit.getPlainHash());
                    } catch (Exception e) {
                        e.printStackTrace();
                        continue;
                    }
                }
                storageDirDiffView.setTo(repository, commitBox.getPlainHash(), parents);

                dirView.getRoot().getChildren().clear();
                try {
                    Repository repo = new Repository(repository, commitBox);
                    fillTree(dirView.getRoot(), new StorageDir(repo, "", userData.getContext().getContextExecutor()), "");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private void fillTree(TreeItem<String> rootItem, StorageDir storageDir, String path) throws IOException,
            CryptoException {
        Collection<String> dirs = storageDir.listDirectories(path);
        for (String dir : dirs) {
            TreeItem<String> dirItem = new TreeItem<String> (dir);
            rootItem.getChildren().add(dirItem);
            fillTree(dirItem, storageDir, StorageLib.appendDir(path, dir));
        }
        Collection<String> files = storageDir.listFiles(path);
        for (String file : files) {
            UserDataStorageView.FileTreeEntry item
                    = new UserDataStorageView.FileTreeEntry(file, StorageLib.appendDir(path, file));
            rootItem.getChildren().add(item);
        }
    }
}
