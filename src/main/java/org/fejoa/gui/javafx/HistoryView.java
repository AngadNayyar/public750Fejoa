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
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SplitPane;
import javafx.scene.control.cell.TextFieldListCell;
import javafx.util.Callback;
import javafx.util.StringConverter;
import org.fejoa.chunkstore.BoxPointer;
import org.fejoa.chunkstore.CommitBox;
import org.fejoa.chunkstore.HashValue;
import org.fejoa.chunkstore.Repository;
import org.fejoa.library.BranchInfo;
import org.fejoa.library.UserData;
import org.fejoa.library.database.DatabaseDiff;
import org.fejoa.library.database.StorageDir;

import java.util.ArrayList;
import java.util.List;


class BranchList extends ListView<BranchInfo> {
    final private StorageDir.IListener listener;

    public BranchList(final UserData userData) {
        listener = new StorageDir.IListener() {
            @Override
            public void onTipChanged(DatabaseDiff diff, String base, String tip) {
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

        for (BranchInfo branchInfo : userData.getBranchList().getEntries()) {
            try {
                getItems().add(branchInfo);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}

public class HistoryView extends SplitPane {
    final private HistoryListView historyView = new HistoryListView(null);
    final private StorageDirDiffView storageDirDiffView = new StorageDirDiffView();

    public HistoryView(final UserData userData) {
        BranchList branchList = new BranchList(userData);
        getItems().add(branchList);
        getItems().add(historyView);
        getItems().add(storageDirDiffView);

        setDividerPosition(0, 0.3);
        setDividerPosition(1, 0.6);

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
                                HistoryListView.HistoryEntry historyEntry, HistoryListView.HistoryEntry newItem) {
                CommitBox commitBox = newItem.getCommitBox();
                Repository repository = (Repository) historyView.getStorageDir().getDatabase();
                List<HashValue> parents = new ArrayList<>();
                for (BoxPointer parent : commitBox.getParents()) {
                    parents.add(parent.getDataHash());
                }
                storageDirDiffView.setTo(repository, commitBox.getBoxPointer().getDataHash(), parents);
            }
        });
    }
}
