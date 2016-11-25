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
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.StackPane;
import org.fejoa.library.BranchInfo;
import org.fejoa.library.Client;
import org.fejoa.library.UserData;
import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.database.DatabaseDiff;
import org.fejoa.library.database.StorageDir;
import org.fejoa.library.support.StorageLib;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;


public class UserDataStorageView extends SplitPane {
    static class FileTreeEntry extends TreeItem<String> {
        final public String path;
        public FileTreeEntry(String name, String path) {
            super(name);

            this.path = path;
        }
    }

    static class BranchItem {
        private final StorageDir.IListener storageDirListener;
        final TreeItem<String> root;

        public BranchItem(final TreeItem<String> root, final StorageDir storageDir, final String path)
                throws IOException, CryptoException {
            this.root = root;

            fillTree(root, storageDir, path);

            storageDirListener = new StorageDir.IListener() {
                @Override
                public void onTipChanged(DatabaseDiff diff) {
                    try {
                        update(root, storageDir, path);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            };
            storageDir.addListener(storageDirListener);
        }

        public TreeItem<String> getRoot() {
            return root;
        }

        private void update(TreeItem<String> rootItem, StorageDir storageDir, String path) throws IOException,
                CryptoException {
            root.getChildren().clear();
            fillTree(rootItem, storageDir, path);
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
                FileTreeEntry item = new FileTreeEntry(file, StorageLib.appendDir(path, file));
                rootItem.getChildren().add(item);
            }
        }
    }

    final private List<BranchItem> branchItems = new ArrayList<>();

    public UserDataStorageView(Client client) {
        UserData userData = client.getUserData();

        TreeItem<String> rootItem = new TreeItem<>("All Branches:");
        rootItem.setExpanded(true);

        TreeView<String> treeView = new TreeView<> (rootItem);
        StackPane root = new StackPane();
        root.getChildren().add(treeView);
        final TextArea textArea = new TextArea();

        setOrientation(Orientation.HORIZONTAL);
        getItems().add(treeView);
        getItems().add(textArea);

        try {
            for (BranchInfo branchInfo : userData.getBranchList().getEntries(true)) {
                StorageDir branchStorage = userData.getStorageDir(branchInfo);
                addStorageDirToTree(branchStorage, rootItem, branchInfo.getDescription(), treeView, textArea);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void addStorageDirToTree(final StorageDir storageDir, TreeItem<String> rootItem, String branchDescription,
                                     TreeView<String> treeView, final TextArea textArea) throws IOException,
            CryptoException {
        final TreeItem<String> item = new TreeItem<> (branchDescription + ": " + storageDir.getBranch());
        item.setExpanded(false);
        BranchItem branchItem = new BranchItem(item, storageDir, "");
        branchItems.add(branchItem);
        rootItem.getChildren().add(item);

        treeView.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<TreeItem<String>>() {
            @Override
            public void changed(ObservableValue<? extends TreeItem<String>> observable, TreeItem<String> old, TreeItem<String> newItem) {
                try {
                    if (newItem instanceof FileTreeEntry && isParent(item, newItem)) {
                        textArea.setText(storageDir.readString(((FileTreeEntry) newItem).path));
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private boolean isParent(TreeItem parent, TreeItem child) {
        TreeItem current = child.getParent();
        while (current != null) {
            if (current == parent)
                return true;
            current = current.getParent();
        }
        return false;
    }
}
