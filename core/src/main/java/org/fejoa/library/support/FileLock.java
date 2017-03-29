/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.support;

import java.io.File;
import java.io.IOException;


public class FileLock {
    final File lockFile;

    public FileLock(File lockFile) {
        this.lockFile = lockFile;
        lockFile.getParentFile().mkdirs();
    }

    public void lock() {
        //TODO make this nicer, at the moment its mostly for debugging
        try {
            for (int i = 0; i < 50; i++) {
                if (lockFile.createNewFile())
                    return;
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            throw new RuntimeException("Fail to lock the chunk store");
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("Fail to lock the chunk store");
        }
    }

    public void unlock() {
        for (int i = 0; i < 5; i++) {
            if (lockFile.delete())
                return;
        }
        throw new RuntimeException("Fail to unlock the chunk store " + lockFile.exists());
    }
}
