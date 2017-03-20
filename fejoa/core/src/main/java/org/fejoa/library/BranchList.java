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

    public BranchList(IOStorageDir storageDir, RemoteList remoteList) {
        super(storageDir);
        this.remoteList = remoteList;
    }

    @Override
    protected BranchInfo readObject(IOStorageDir storageDir) throws IOException, CryptoException {
        String baseDir = this.storageDir.getBaseDir();
        String subDir = storageDir.getBaseDir();
        if (subDir.startsWith(baseDir))
            subDir = subDir.substring(baseDir.length());
        if (subDir.startsWith("/"))
            subDir = subDir.substring(1);
        if (subDir.endsWith("/"))
            subDir = subDir.substring(0, subDir.length() - 1);
        String storageContext = "";
        String branch = subDir;
        int lastSlash = subDir.lastIndexOf("/");
        if (lastSlash > 0) {
            storageContext = subDir.substring(0, lastSlash);
            branch = subDir.substring(lastSlash + 1);
        }

        BranchInfo branchInfo = BranchInfo.open(storageDir, branch, pathPathToContext(storageContext));
        branchInfo.setRemoteList(remoteList);
        return branchInfo;
    }

    static public String contextToPath(String context) {
        return context.replace('.', '/');
    }

    static public String pathPathToContext(String context) {
        return context.replace('/', '.');
    }

    public void add(BranchInfo branchInfo) throws IOException, CryptoException {
        branchInfo.setRemoteList(remoteList);
        super.add(StorageLib.appendDir(contextToPath(branchInfo.getStorageContext()), branchInfo.getBranch()), branchInfo);
    }

    public BranchInfo get(String id, String context) throws IOException, CryptoException {
        return readObject(new IOStorageDir(storageDir, StorageLib.appendDir(contextToPath(context), id)));
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
        String path = contextToPath(context);
        getEntriesRecursive(path, new IOStorageDir(storageDir, path), entries);
        return entries;
    }

    public Collection<BranchInfo> getEntries(String context) throws IOException, CryptoException {
        return getEntries(context, true);
    }

    private void getEntriesRecursive(String path, IOStorageDir storageDir, List<BranchInfo> out)
            throws IOException, CryptoException {
        Collection<String> subDirs = storageDir.listDirectories("");
        for (String dir : subDirs) {
            IOStorageDir subDir = new IOStorageDir(storageDir, dir);
            try {
                BranchInfo branchInfo = BranchInfo.open(subDir, dir, path);
                branchInfo.setRemoteList(remoteList);
                out.add(branchInfo);
            } catch (Exception e) {
                getEntriesRecursive(StorageLib.appendDir(path, dir), subDir, out);
            }
        }
    }
}
