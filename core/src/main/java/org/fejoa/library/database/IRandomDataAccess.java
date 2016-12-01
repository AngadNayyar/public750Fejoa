/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.database;

import java8.util.concurrent.CompletableFuture;


public interface IRandomDataAccess extends ISyncRandomDataAccess {
    CompletableFuture<Void> writeAsync(byte[] data);
    CompletableFuture<Integer> readAsync(byte[] buffer);
    CompletableFuture<Void> writeAsync(byte[] data, int offset, int length);
    CompletableFuture<Integer> readAsync(byte[] buffer, int offset, int length);

    CompletableFuture<Void> flushAsync();
    CompletableFuture<Void> closeAsync();
}
