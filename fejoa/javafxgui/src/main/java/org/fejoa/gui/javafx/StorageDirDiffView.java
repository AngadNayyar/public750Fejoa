/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.gui.javafx;

import javafx.scene.control.ListView;
import org.fejoa.chunkstore.*;
import org.fejoa.chunkstore.sync.DiffIterator;
import org.fejoa.chunkstore.sync.TreeIterator;

import java.util.List;


public class StorageDirDiffView extends ListView<String>{
    public StorageDirDiffView() {

    }

    public void setTo(Repository repository, HashValue startCommit, List<HashValue> parents) {
        getItems().clear();

        if (parents.size() > 1)
            return;

        HashValue parent = null;
        if (parents.size() > 0)
            parent = parents.get(0);

        if (parent == null)
            return;

        try {
            CommitBox baseCommit = repository.getCommitCache().getCommit(startCommit);
            CommitBox endCommit = repository.getCommitCache().getCommit(parent);

            IRepoChunkAccessors.ITransaction transaction = repository.getCurrentTransaction();
            TreeIterator diffIterator = new TreeIterator(transaction, endCommit, transaction, baseCommit);
            while (diffIterator.hasNext()) {
                DiffIterator.Change<FlatDirectoryBox.Entry> change = diffIterator.next();
                switch (change.type) {
                    case MODIFIED:
                        getItems().add("m " + change.path);
                        break;

                    case ADDED:
                        getItems().add("+ " + change.path);
                        break;

                    case REMOVED:
                        getItems().add("- " + change.path);
                        break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}
