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
import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.database.DatabaseDiff;
import org.fejoa.library.database.StorageDir;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;


public class HistoryView extends ListView<String> {
    private StorageDir storageDir;
    private StorageDir.IListener listener;

    public HistoryView(StorageDir storageDir) {
        setTo(storageDir);
    }

    public Repository getRepository() {
        return (Repository)storageDir.getDatabase();
    }

    public void setTo(final StorageDir storageDirIn) {
        this.storageDir = storageDirIn;
        if (storageDirIn == null) {
            clear();
            return;
        }
        final Repository repository = getRepository();
        update(repository.getHeadCommit(), repository.getCommitCache());

        this.listener = new StorageDir.IListener() {
            @Override
            public void onTipChanged(DatabaseDiff diff, String base, String tip) {
                if (HistoryView.this.storageDir != storageDirIn)
                    return;
                update(repository.getHeadCommit(), repository.getCommitCache());
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
        if (storageDir != null)
            storageDir.removeListener(listener);
        listener = null;
    }

    private void update(CommitBox head, CommitCache commitCache) {
        clear();

        try {
            fill(head, commitCache);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static public class HistoryEntry {
        final List<Integer> activeBranches = new ArrayList<>();
        final private Integer commitBranchId;
        final private CommitBox commitHash;

        public HistoryEntry(Integer commitBranchId, CommitBox commitHash) {
            this.commitBranchId = commitBranchId;
            this.commitHash = commitHash;
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

        public String getEntryString(int i) {
            String out = "";
            HistoryEntry entry = entries.get(i);
            for (Integer branchId : entry.activeBranches) {
                if (branchId == entry.commitBranchId)
                    out += "*";
                else
                    out += "|";
            }
            return out;
        }

        public String getEntryDetails(int i) {
            CommitBox commitBox = entries.get(i).commitHash;
            String out = shortHash(commitBox.dataHash().toString()) + " "
                    + new String(commitBox.getCommitMessage());
            out += "(";
            for (int parentIndex = 0; parentIndex < commitBox.getParents().size(); parentIndex++) {
                BoxPointer parent = commitBox.getParents().get(parentIndex);
                CommitBox parentCommit;
                try {
                    parentCommit = CommitBox.read(getRepository().getCurrentTransaction().getCommitAccessor(), parent);
                    out += shortHash(parentCommit.dataHash().toString());
                } catch (Exception e) {
                    e.printStackTrace();
                }
                if (parentIndex < commitBox.getParents().size() - 1)
                    out += ",";
            }
            out += ")\n";
            return out;
        }

        public String toString() {
            String out = "";
            for (int i = 0; i < entries.size(); i++) {
                out += getEntryString(i) + "\t" + getEntryDetails(i);
            }
            return out;
        }

        public String shortHash(String hash) {
            return hash.substring(0, 8);
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

    private Chain loadLeftMostChain(CommitCache commitCache, CommitBox start, List<Chain> stopChains)
            throws IOException, CryptoException {
        Chain chain = new Chain(historyList.getNewChainId());
        chain.commits.add(start);
        while (chain.oldest().getParents().size() > 0) {
            CommitBox parent = commitCache.getCommit(chain.oldest().getParents().get(0).getDataHash());
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

    private void fill(CommitBox head, CommitCache commitCache) throws IOException, CryptoException {
        if (head == null)
            return;
        CommitBox current = head;
        List<Chain> activeChains = new ArrayList<>();
        Chain chain = loadLeftMostChain(commitCache, head, activeChains);
        activeChains.add(chain);
        fillChain(chain, commitCache, activeChains);

        historyListToViewItems();
    }

    private void fillChain(Chain chain, CommitCache commitCache, List<Chain> activeChains)
            throws IOException, CryptoException {
        for (CommitBox commit : chain.commits) {
            HistoryEntry historyEntry = new HistoryEntry(chain.chainId, commit);
            for (Chain activeChain : activeChains)
                historyEntry.activeBranches.add(activeChain.chainId);
            historyList.add(historyEntry);

            if (commit.getParents().size() > 1) {
                List<Chain> parentChains = new ArrayList<>();
                for (int i = 1; i < commit.getParents().size(); i++) {
                    CommitBox parent = commitCache.getCommit(commit.getParents().get(i).getDataHash());
                    Chain parentChain = loadLeftMostChain(commitCache, parent, activeChains);
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
                    fillChain(parentChain, commitCache, activeChains);
            }
        }
        activeChains.remove(chain);
    }

    private void historyListToViewItems() {
        for (int i = 0; i < historyList.size(); i++)
            getItems().add(historyList.getEntryString(i) + "\t" + historyList.getEntryDetails(i));
    }
}
