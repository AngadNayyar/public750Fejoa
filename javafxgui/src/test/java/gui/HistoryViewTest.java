package gui;


import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.fejoa.chunkstore.BoxPointer;
import org.fejoa.chunkstore.CommitBox;
import org.fejoa.chunkstore.IRepoChunkAccessors;
import org.fejoa.chunkstore.Repository;
import org.fejoa.gui.javafx.HistoryListView;
import org.fejoa.library.FejoaContext;
import org.fejoa.library.database.StorageDir;
import org.fejoa.library.support.StorageLib;

import java.io.File;
import java.util.Collections;

public class HistoryViewTest extends Application {
    private final static String MAIN_DIR = "guiHistoryTest";

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) throws Exception {
        StorageLib.recursiveDeleteFile(new File(MAIN_DIR));
        FejoaContext context = new FejoaContext(MAIN_DIR);
        StorageDir storageDir = context.getStorage("test", null, null);

        HistoryListView historyView = new HistoryListView(storageDir);

        storageDir.writeString("test", "test");
        storageDir.commit("commit 1");
        storageDir.writeString("test2", "test2");
        storageDir.commit("commit 2");

        Repository repository = (Repository)storageDir.getDatabase();
        CommitBox base0 = repository.getHeadCommit();

        storageDir.writeString("test3", "test3");
        storageDir.commit("commit 3");
        CommitBox commit3 = repository.getHeadCommit();


        CommitBox branchCommit = CommitBox.create();
        branchCommit.setTree(base0.getTree());
        branchCommit.addParent(base0.getBoxPointer());
        branchCommit.setCommitMessage("Branch Commit 1".getBytes());
        IRepoChunkAccessors.ITransaction transaction = repository.getAccessors().startTransaction();
        BoxPointer commitPointer = Repository.put(branchCommit, transaction.getCommitAccessor(), true);
        branchCommit.setBoxPointer(commitPointer);
        repository.merge(transaction, branchCommit);
        repository.commitInternal("Merge.", null, Collections.singletonList(branchCommit.getBoxPointer()));
        storageDir.onTipUpdated(commit3.dataHash(), repository.getHeadCommit().dataHash());

        System.out.println(historyView.getHistoryList());

        stage.setScene(new Scene(historyView));
        stage.show();
    }

}
