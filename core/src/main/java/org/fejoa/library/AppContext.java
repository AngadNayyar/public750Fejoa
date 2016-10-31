/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library;

import org.fejoa.library.command.AccessCommand;
import org.fejoa.library.command.AccessCommandHandler;
import org.fejoa.library.command.IncomingCommandManager;
import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.database.StorageDir;
import org.fejoa.library.remote.SyncManager;

import java.io.IOException;
import java.util.Collection;


public class AppContext extends StorageDirObject {
    final private String appContext;
    final private UserData userData;

    protected AppContext(FejoaContext context, String appContext, StorageDir storageDir, UserData userData) {
        super(context, storageDir);

        this.appContext = appContext;
        this.userData = userData;
    }

    public void commit() throws IOException {
        userData.commit();
    }

    public UserData getUserData() {
        return userData;
    }

    public StorageDir getStorageDir(String branchId) throws IOException, CryptoException {
        BranchInfo branchInfo = userData.getBranchList().get(branchId, appContext);
        return userData.getStorageDir(branchInfo);
    }

    public void addAccessGrantedHandler(IncomingCommandManager manager, AccessCommandHandler.IContextHandler handler) {
        AccessCommandHandler accessHandler = (AccessCommandHandler)manager.getHandler(AccessCommand.COMMAND_NAME);
        accessHandler.addContextHandler(appContext, handler);
        manager.handleCommands();
    }

    public void watchBranches(SyncManager syncManager, Collection<BranchInfo.Location> branches) throws IOException {
        syncManager.addWatching(branches);
    }
}
