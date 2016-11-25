/*
 * Copyright 2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.database;


import org.fejoa.chunkstore.HashValue;

public class DatabaseDiff {
    final public HashValue base;
    final public HashValue target;
    final public DatabaseDir added = new DatabaseDir("");
    final public DatabaseDir modified = new DatabaseDir("");
    final public DatabaseDir removed = new DatabaseDir("");

    public DatabaseDiff(HashValue base, HashValue target) {
        this.base = base;
        this.target = target;
    }
}
