/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.remote;


public class Errors {
    final static public int FOLLOW_UP_JOB = 1;

    final static public int DONE = 0;
    final static public int OK = 0;
    final static public int ERROR = -1;
    final static public int EXCEPTION = -2;

    // json handler
    final static public int NO_HANDLER_FOR_REQUEST = -10;
    final static public int INVALID_JSON_REQUEST = -11;

    // access
    final static public int ACCESS_DENIED = -20;

    // migration
    final static public int MIGRATION_ALREADY_STARTED = -30;
}
