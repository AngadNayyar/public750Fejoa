/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.chunkstore.sync;

import org.fejoa.library.support.StreamHelper;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;


public class Request {
    static final public int PROTOCOL_VERSION = 1;

    static final public String CS_REQUEST_METHOD = "csRequest";

    // requests
    static final public int GET_REMOTE_TIP = 1;
    static final public int GET_CHUNKS = 3;
    static final public int PUT_CHUNKS = 4;
    static final public int HAS_CHUNKS = 5;
    static final public int GET_ALL_CHUNKS = 6;

    // errors
    static final public int ERROR = -1;
    static final public int OK = 0;
    static final public int PULL_REQUIRED = 1;

    static public void writeRequestHeader(DataOutputStream outputStream, int request) throws IOException {
        outputStream.writeInt(PROTOCOL_VERSION);
        outputStream.writeInt(request);
    }

    static public void writeResponseHeader(DataOutputStream outputStream, int request, int status) throws IOException {
        writeRequestHeader(outputStream, request);
        outputStream.writeInt(status);
    }

    static public int receiveRequest(DataInputStream inputStream) throws IOException {
        int version = inputStream.readInt();
        if (version != PROTOCOL_VERSION)
            throw new IOException("Version " + PROTOCOL_VERSION + " expected but got:" + version);
        return inputStream.readInt();
    }

    static public int receiveHeader(DataInputStream inputStream, int expectedRequest) throws IOException {
        int request = receiveRequest(inputStream);
        if (expectedRequest != request)
            throw new IOException("Unexpected request: " + request + " but " + expectedRequest + " expected");
        int status = inputStream.readInt();
        if (status <= ERROR) {
            throw new IOException("ERROR (request " + expectedRequest + "): "
                    + StreamHelper.readString(inputStream, 1024 * 20));
        }
        return status;
    }
}
