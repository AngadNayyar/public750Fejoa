/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.database;

import java8.util.concurrent.CompletableFuture;

import java.util.Collection;


public interface IIODatabase extends IIOSyncDatabase {
    CompletableFuture<Boolean> hasFileAsync(String path);
    CompletableFuture<IRandomDataAccess> openAsync(String path, IIOSyncDatabase.Mode mode);
    CompletableFuture<Void> removeAsync(String path);

    CompletableFuture<byte[]> readBytesAsync(String path);
    CompletableFuture<Void> putBytesAsync(String path, byte[] data);

    CompletableFuture<Collection<String>> listFilesAsync(String path);
    CompletableFuture<Collection<String>> listDirectoriesAsync(String path);
}
