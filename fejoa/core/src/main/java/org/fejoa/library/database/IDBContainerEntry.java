/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.database;

import java8.util.concurrent.CompletableFuture;


public interface IDBContainerEntry {
    void setTo(IOStorageDir dir);
    CompletableFuture<Void> flush();
    void invalidate();
}


