/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library;

import org.fejoa.library.database.IOStorageDir;
import org.fejoa.library.database.MovableStorageContainer;

import java.io.IOException;


public class RemoteList extends StorageDirList<Remote> {
    static private AbstractEntryIO<Remote> createEntryIO() {
        return new AbstractEntryIO<Remote>() {
            @Override
            public String getId(Remote entry) {
                return entry.getId();
            }

            @Override
            public Remote read(IOStorageDir dir) throws IOException {
                Remote remote = new Remote();
                remote.read(dir);
                return remote;
            }
        };
    }

    public RemoteList(IOStorageDir storageDir) {
        super(storageDir, createEntryIO());
    }

    public RemoteList(MovableStorageContainer parent, String subDir) {
        super(parent, subDir, createEntryIO());
    }
}
