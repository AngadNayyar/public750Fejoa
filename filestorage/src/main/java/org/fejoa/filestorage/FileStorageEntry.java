/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.filestorage;

import org.fejoa.library.BranchInfo;
import org.fejoa.library.Constants;
import org.fejoa.library.IStorageDirBundle;
import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.crypto.CryptoHelper;
import org.fejoa.library.database.IOStorageDir;

import java.io.File;
import java.io.IOException;


public class FileStorageEntry implements IStorageDirBundle {
    final static private String PATH_KEY = "path";

    private File path;
    private String branch;

    public FileStorageEntry() {
    }

    public FileStorageEntry(File path, BranchInfo branchInfo) {
        this.path = path;
        this.branch = branchInfo.getBranch();
    }

    public File getPath() {
        return path;
    }

    public String getBranch() {
        return branch;
    }

    public String getId() {
        return CryptoHelper.sha1HashHex(path.getPath() + branch);
    }

    @Override
    public void write(IOStorageDir dir) throws IOException, CryptoException {
        dir.writeString(PATH_KEY, path.getPath());
        dir.writeString(Constants.BRANCH_KEY, branch);
    }

    @Override
    public void read(IOStorageDir dir) throws IOException, CryptoException {
        path = new File(dir.readString(PATH_KEY));
        branch = dir.readString(Constants.BRANCH_KEY);
    }
}
