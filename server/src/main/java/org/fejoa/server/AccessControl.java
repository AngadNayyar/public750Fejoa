/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.server;

import org.fejoa.chunkstore.ChunkStore;
import org.fejoa.chunkstore.ChunkStoreBranchLog;
import org.fejoa.library.BranchAccessRight;

import java.io.File;
import java.io.IOException;


public class AccessControl {
    final private Session session;
    final private String user;

    public AccessControl(Session session, String user) {
        this.session = session;
        this.user = user;
    }

    private boolean hasAccess(String branch, int rights) {
        if (DebugSingleton.get().isNoAccessControl())
            return true;
        if (session.hasRootRole(user))
            return true;
        int roleRights = getBranchAccessRights(branch);
        if ((roleRights & rights) == rights)
            return true;
        return false;
    }

    public int getBranchAccessRights(String branch) {
        if (DebugSingleton.get().isNoAccessControl() || isRootUser() || session.hasMigrationRole(user))
            return BranchAccessRight.ALL;
        return session.getRoleRights(user, branch);
    }

    public boolean canStartMigration() {
        return session.hasRootRole(user);
    }

    public boolean isRootUser() {
        return session.hasRootRole(user);
    }

    public ChunkStore getChunkStore(String branch, int rights) throws IOException {
        if (!hasAccess(branch, rights))
            return null;
        File dir = getChunkStoreDir();
        if (ChunkStore.exists(dir, branch))
            return ChunkStore.open(dir, branch);
        else {
            dir.mkdirs();
            return ChunkStore.create(dir, branch);
        }
    }

    public ChunkStoreBranchLog getChunkStoreBranchLog(String branch, int rights) throws IOException {
        if (!hasAccess(branch, rights))
            return null;
        return new ChunkStoreBranchLog(new File(getChunkStoreDir(), "branches/" + branch));
    }

    private File getChunkStoreDir() {
        return new File(session.getServerUserDir(user), ".chunkstore");
    }
}
