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
import org.fejoa.library.database.StorageDir;

import java.io.IOException;
import java.util.List;


public class UserDataStorageView extends SplitPane {
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


        for (BranchInfo branchInfo : userData.getBranchList().getEntries()) {
            StorageDir branchStorage = null;
            try {
                branchStorage = userData.getStorageDir(branchInfo);
                addStorageDirToTree(branchStorage, rootItem, branchInfo.getDescription(), treeView, textArea);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    class FileTreeEntry extends TreeItem<String> {
        final public String path;
        public FileTreeEntry(String name, String path) {
            super(name);

            this.path = path;
        }
    }

    private void fillTree(TreeItem<String> rootItem, StorageDir storageDir, String path) throws IOException {
        List<String> dirs = storageDir.listDirectories(path);
        for (String dir : dirs) {
            TreeItem<String> dirItem = new TreeItem<String> (dir);
            rootItem.getChildren().add(dirItem);
            fillTree(dirItem, storageDir, StorageDir.appendDir(path, dir));
        }
        List<String> files = storageDir.listFiles(path);
        for (String file : files) {
            FileTreeEntry item = new FileTreeEntry(file, StorageDir.appendDir(path, file));
            rootItem.getChildren().add(item);
        }
    }

    private void addStorageDirToTree(final StorageDir storageDir, TreeItem<String> rootItem, String branchDescription,
                                     TreeView<String> treeView, final TextArea textArea) throws IOException {
        final TreeItem<String> item = new TreeItem<> (branchDescription + ": " + storageDir.getBranch());
        item.setExpanded(false);
        fillTree(item, storageDir, "");
        rootItem.getChildren().add(item);

        treeView.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<TreeItem<String>>() {
            @Override
            public void changed(ObservableValue<? extends TreeItem<String>> observable, TreeItem<String> old, TreeItem<String> newItem) {
                try {
                    if (newItem instanceof FileTreeEntry && isParent(item, newItem))
                        textArea.setText(storageDir.readString(((FileTreeEntry) newItem).path));
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