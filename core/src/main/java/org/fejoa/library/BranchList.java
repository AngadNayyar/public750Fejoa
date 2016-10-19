/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library;

import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.database.IOStorageDir;
import org.fejoa.library.database.MovableStorageList;
import org.fejoa.library.support.StorageLib;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;


public class BranchList extends MovableStorageList<BranchInfo> {
    final private RemoteList remoteList;

    public BranchList(IOStorageDir storageDir, RemoteList remoteList) throws IOException, CryptoException {
        super(storageDir);
        this.remoteList = remoteList;
    }

    @Override
    protected BranchInfo readObject(IOStorageDir storageDir, String id) throws IOException, CryptoException {
        return BranchInfo.open(storageDir, id);
    }

    private String contextToPath(String context) {
        return context.replace('.', '/');
    }

    public void add(BranchInfo branchInfo, String context) throws IOException, CryptoException {
        branchInfo.setRemoteList(remoteList);
        super.add(StorageLib.appendDir(contextToPath(context), branchInfo.getBranch()), branchInfo);
    }

    public BranchInfo get(String id, String context) throws IOException, CryptoException {
        return readObject(new IOStorageDir(storageDir, StorageLib.appendDir(contextToPath(context), id)), id);
    }

    @Override
    public Collection<BranchInfo> getEntries() throws IOException, CryptoException {
        return this.getEntries(true);
    }

    public Collection<BranchInfo> getEntries(boolean recursive) throws IOException, CryptoException {
        return getEntries("", recursive);
    }

    public Collection<BranchInfo> getEntries(String context, boolean recursive) throws IOException, CryptoException {
        if (!recursive)
            return this.getEntries("");

        List<BranchInfo> entries = new ArrayList<>();
        getEntriesRecursive(new IOStorageDir(storageDir, contextToPath(context)), entries);
        return entries;
    }

    public Collection<BranchInfo> getEntries(String context) throws IOException, CryptoException {
        return getEntries(context, true);
    }

    private void getEntriesRecursive(IOStorageDir storageDir, List<BranchInfo> out)
            throws IOException, CryptoException {
        Collection<String> subDirs = storageDir.listDirectories("");
        for (String dir : subDirs) {
            IOStorageDir subDir = new IOStorageDir(storageDir, dir);
            try {
                BranchInfo branchInfo = BranchInfo.open(subDir, dir);
                branchInfo.setRemoteList(remoteList);
                out.add(branchInfo);
            } catch (Exception e) {
                getEntriesRecursive(subDir, out);
            }
        }
    }
}