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
import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.database.DatabaseDiff;

import java.io.IOException;
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

            IChunkAccessor treeAccessor = repository.getCurrentTransaction().getTreeAccessor();
            TreeIterator diffIterator = new TreeIterator(treeAccessor, baseCommit, treeAccessor, endCommit);
            while (diffIterator.hasNext()) {
                DiffIterator.Change<DirectoryBox.Entry> change = diffIterator.next();
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
