/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.gui.javafx;

import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.cell.TextFieldListCell;
import javafx.util.Callback;
import javafx.util.StringConverter;
import org.fejoa.chunkstore.*;
import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.database.DatabaseDiff;
import org.fejoa.library.database.StorageDir;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;


public class HistoryListView extends ListView<HistoryListView.HistoryEntry> {
    private StorageDir storageDir;
    private StorageDir.IListener listener;

    public HistoryListView(StorageDir storageDir) {
        setCellFactory(new Callback<ListView<HistoryEntry>, ListCell<HistoryEntry>>() {
            @Override
            public ListCell<HistoryEntry> call(ListView<HistoryEntry> historyEntryListView) {
                return new TextFieldListCell<>(new StringConverter<HistoryEntry>() {
                    @Override
                    public String toString(HistoryEntry historyEntry) {
                        return historyEntry.getEntryString() + "\t" + historyEntry.getEntryDetails() +"\n" + historyEntry.getEntryStringLine2();
                    }

                    @Override
                    public HistoryEntry fromString(String s) {
                        return null;
                    }
                });
            }
        });
        setTo(storageDir);
    }

    public Repository getRepository() {
        return (Repository)storageDir.getDatabase();
    }

    public void setTo(final StorageDir storageDirIn) {
        if (storageDir != null)
            storageDir.removeListener(listener);
        this.storageDir = storageDirIn;
        if (storageDir == null) {
            clear();
            return;
        }
        final Repository repository = getRepository();
        update(repository.getHeadCommit(), repository.getCurrentTransaction());

        this.listener = new StorageDir.IListener() {
            @Override
            public void onTipChanged(DatabaseDiff diff) {
                if (HistoryListView.this.storageDir != storageDirIn)
                    return;
                update(repository.getHeadCommit(), repository.getCurrentTransaction());
            }
        };
        storageDir.addListener(listener);
    }

    public StorageDir getStorageDir() {
        return storageDir;
    }

    private void clear() {
        getItems().clear();
        historyList.clear();
    }

    private void update(CommitBox head, IRepoChunkAccessors.ITransaction transaction) {
        clear();

        try {
            fill(head, transaction);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public class HistoryEntry {
        final List<Integer> activeBranches = new ArrayList<>();
        final private Integer commitBranchId;
        final private CommitBox commitBox;

        public HistoryEntry(Integer commitBranchId, CommitBox commitHash) {
            this.commitBranchId = commitBranchId;
            this.commitBox = commitHash;
        }

        public CommitBox getCommitBox() {
            return commitBox;
        }

        public String getEntryString() {
            String out = "";
            for (Integer branchId : activeBranches) {
                if (branchId == commitBranchId)
                    out += "*";
                else
                    out += "|";
            }
            return out;
        }

        public String getEntryStringLine2() {
            String out = "";
            for (Integer branchId : activeBranches) {
                out += "|";
            }
            return out;
        }

        public String getEntryDetails() {
            CommitBox commitBox = this.commitBox;
            String out = shortHash(commitBox.getPlainHash().toString()) + " ";
            String commitMessage = new String(commitBox.getCommitMessage());
            try {
                JSONObject jsonObject = new JSONObject(commitMessage);
                commitMessage = jsonObject.getString("message");
            } catch (JSONException e) {
                //e.printStackTrace();
            }

            out += commitMessage;
            out += " parents = [";
            for (int parentIndex = 0; parentIndex < commitBox.getParents().size(); parentIndex++) {
                ChunkContainerRef parent = commitBox.getParents().get(parentIndex);
                CommitBox parentCommit;
                try {
                    parentCommit = CommitBox.read(getRepository().getCurrentTransaction().getCommitAccessor(parent),
                            parent);
                    out += shortHash(parentCommit.getPlainHash().toString());
                } catch (Exception e) {
                    e.printStackTrace();
                }
                if (parentIndex < commitBox.getParents().size() - 1)
                    out += ",";
            }
            out += "]";
            return out;
        }

        public String shortHash(String hash) {
            return hash.substring(0, 8);
        }
    }

    public class HistoryList {
        Integer chainIdCounter = 0;

        final List<HistoryEntry> entries = new ArrayList<>();

        public int size() {
            return entries.size();
        }

        public HistoryEntry get(int i) {
            return entries.get(i);
        }

        public void clear() {
            entries.clear();
        }

        public Integer getNewChainId() {
            chainIdCounter++;
            return chainIdCounter;
        }

        public String toString() {
            String out = "";
            for (int i = 0; i < entries.size(); i++) {
                HistoryEntry entry = entries.get(i);
                out += entry.getEntryString() + "\t" + entry.getEntryDetails() + "\n"
                        + entry.getEntryStringLine2();
                if (i < entries.size() - 1)
                    out += "\n";
            }
            return out;
        }

        public void add(HistoryEntry historyEntry) {
            entries.add(historyEntry);
        }
    }

    static class Chain {
        final public Integer chainId;
        final public List<CommitBox> commits = new ArrayList<>();

        public Chain(Integer chainId) {
            this.chainId = chainId;
        }

        public boolean contains(CommitBox commitBox) {
            return commits.contains(commitBox);
        }

        public CommitBox oldest() {
            return commits.get(commits.size() - 1);
        }
    }

    private Chain loadLeftMostChain(IRepoChunkAccessors.ITransaction transaction, CommitBox start, List<Chain> stopChains)
            throws IOException, CryptoException {
        Chain chain = new Chain(historyList.getNewChainId());
        chain.commits.add(start);
        while (chain.oldest().getParents().size() > 0) {
            CommitBox parent = CommitBox.read(transaction.getCommitAccessor(chain.oldest().getParents().get(0)),
                    chain.oldest().getParents().get(0));
            for (Chain stopChain : stopChains) {
                if (stopChain.contains(parent))
                    return chain;
            }
            chain.commits.add(parent);
        }
        return chain;
    }

    private HistoryList historyList = new HistoryList();

    public HistoryList getHistoryList() {
        return historyList;
    }

    private void fill(CommitBox head, IRepoChunkAccessors.ITransaction transaction) throws IOException, CryptoException {
        if (head == null)
            return;
        CommitBox current = head;
        List<Chain> activeChains = new ArrayList<>();
        Chain chain = loadLeftMostChain(transaction, head, activeChains);
        activeChains.add(chain);
        fillChain(chain, transaction, activeChains);

        historyListToViewItems();
    }

    private void fillChain(Chain chain, IRepoChunkAccessors.ITransaction transaction, List<Chain> activeChains)
            throws IOException, CryptoException {
        for (CommitBox commit : chain.commits) {
            HistoryEntry historyEntry = new HistoryEntry(chain.chainId, commit);

            for (Chain activeChain : activeChains)
                historyEntry.activeBranches.add(activeChain.chainId);
            historyList.add(historyEntry);

            if (commit.getParents().size() > 1) {
                List<Chain> parentChains = new ArrayList<>();
                for (int i = 1; i < commit.getParents().size(); i++) {
                    CommitBox parent = CommitBox.read(transaction.getCommitAccessor(commit.getParents().get(i)),
                            commit.getParents().get(i));
                    Chain parentChain = loadLeftMostChain(transaction, parent, activeChains);
                    parentChains.add(parentChain);
                }
                Collections.sort(parentChains, new Comparator<Chain>() {
                    @Override
                    public int compare(Chain chain, Chain t1) {
                        return new Integer(chain.commits.size()).compareTo(t1.commits.size());
                    }
                });
                for (Chain parentChain : parentChains)
                    activeChains.add(parentChain);
                for (Chain parentChain : parentChains)
                    fillChain(parentChain, transaction, activeChains);
            }
        }
        activeChains.remove(chain);
    }

    private void historyListToViewItems() {
        for (int i = 0; i < historyList.size(); i++)
            getItems().add(historyList.get(i));
    }
}
