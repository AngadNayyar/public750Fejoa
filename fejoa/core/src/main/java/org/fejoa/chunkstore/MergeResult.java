/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.chunkstore;


public enum MergeResult {
    MERGED(0),
    FAST_FORWARD(1),
    UNCOMMITTED_CHANGES(-1);

    MergeResult(int value) {
        this.value = value;
    }

    private int value;
}
