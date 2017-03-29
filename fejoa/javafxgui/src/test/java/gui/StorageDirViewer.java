/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package gui;

import javafx.application.Application;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Orientation;
import javafx.scene.Scene;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.fejoa.gui.javafx.JavaFXScheduler;
import org.fejoa.library.BranchInfo;
import org.fejoa.library.FejoaContext;
import org.fejoa.library.UserData;
import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.database.StorageDir;
import org.fejoa.library.support.StorageLib;

import java.io.File;
import java.io.IOException;
import java.util.Collection;


public class StorageDirViewer extends Application {
    class FileTreeEntry extends TreeItem<String> {
        final public String path;
        public FileTreeEntry(String name, String path) {
            super(name);

            this.path = path;
        }
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

    public static void main(String[] args) {
        launch(args);
    }

    private void addStorageDirToTree(final StorageDir storageDir, TreeItem<String> rootItem, String branchDescription,
                                     TreeView<String> treeView, final TextArea textArea) throws IOException,
            CryptoException {
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

    @Override
    public void start(Stage stage) throws Exception {
        stage.setTitle("Tree View Sample");

        TreeItem<String> rootItem = new TreeItem<>("All Branches:");
        rootItem.setExpanded(true);

        TreeView<String> treeView = new TreeView<> (rootItem);
        StackPane root = new StackPane();
        root.getChildren().add(treeView);
        final TextArea textArea = new TextArea();

        SplitPane mainLayout = new SplitPane();
        mainLayout.setOrientation(Orientation.HORIZONTAL);

        mainLayout.getItems().add(treeView);
        mainLayout.getItems().add(textArea);

        String baseDir = "StorageDirViewerTest";
        StorageLib.recursiveDeleteFile(new File(baseDir));
        FejoaContext context = new FejoaContext("StorageDirViewerTest", new JavaFXScheduler());
        UserData userData = UserData.create(context, "test");
        userData.commit();

        for (BranchInfo branchInfo : userData.getBranchList().getEntries()) {
            StorageDir branchStorage = userData.getStorageDir(branchInfo);
            addStorageDirToTree(branchStorage, rootItem, branchInfo.getDescription(), treeView, textArea);
        }

        stage.setScene(new Scene(mainLayout, 300, 250));
        stage.show();

        StorageLib.recursiveDeleteFile(new File(baseDir));
    }
}
