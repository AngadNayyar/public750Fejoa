package org.fejoa.tests.gui;

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
import org.fejoa.library.Client;
import org.fejoa.library.FejoaContext;
import org.fejoa.library.UserData2;
import org.fejoa.library.database.StorageDir;
import org.fejoa.library.support.StorageLib;

import java.io.File;
import java.io.IOException;
import java.util.List;


public class StorageDirViewer extends Application {
    private StorageDir storageDir;

    private void fillTree(TreeItem<String> rootItem, String path) throws IOException {
        List<String> dirs = storageDir.listDirectories(path);
        for (String dir : dirs) {
            TreeItem<String> dirItem = new TreeItem<String> (dir);
            rootItem.getChildren().add(dirItem);
            fillTree(dirItem, StorageDir.appendDir(path, dir));
        }
        List<String> files = storageDir.listFiles(path);
        for (String file : files) {
            TreeItem<String> item = new TreeItem<String> (file);
            rootItem.getChildren().add(item);
        }
    }

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) throws Exception {
        stage.setTitle("Tree View Sample");

        String baseDir = "StorageDirViewerTest";
        StorageLib.recursiveDeleteFile(new File(baseDir));
        FejoaContext context = new FejoaContext("StorageDirViewerTest");
        UserData2 userData2 = UserData2.create(context, ".chunkstore", "test");
        userData2.commit();
        storageDir = userData2.getStorageDir();

        TreeItem<String> rootItem = new TreeItem<String> ("Branch: " + storageDir.getBranch());
        rootItem.setExpanded(true);
        fillTree(rootItem, "");

        TreeView<String> treeView = new TreeView<String> (rootItem);
        StackPane root = new StackPane();
        root.getChildren().add(treeView);

        final TextArea textArea = new TextArea();

        treeView.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<TreeItem<String>>() {
            @Override
            public void changed(ObservableValue<? extends TreeItem<String>> observable, TreeItem<String> old, TreeItem<String> newItem) {
                if (newItem.getChildren().size() > 0)
                    return;

                try {
                    textArea.setText(storageDir.readString(fullPath(newItem)));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });

        SplitPane mainLayout = new SplitPane();
        mainLayout.setOrientation(Orientation.HORIZONTAL);

        mainLayout.getItems().add(treeView);
        mainLayout.getItems().add(textArea);

        stage.setScene(new Scene(mainLayout, 300, 250));
        stage.show();

        StorageLib.recursiveDeleteFile(new File(baseDir));
    }

    private String fullPath(TreeItem<String> leaf) {
        String path = leaf.getValue();
        TreeItem<String> current = leaf;
        while (current.getParent() != null) {
            // ignore root leaf
            if (current.getParent().getParent() == null)
                break;
            current = current.getParent();
            path = StorageDir.appendDir(current.getValue(), path);
        }
        return path;
    }
}
