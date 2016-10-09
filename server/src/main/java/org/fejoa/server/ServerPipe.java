/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.server;

import org.fejoa.library.remote.IRemotePipe;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;


public class ServerPipe implements IRemotePipe {
    private class RemoteOutputStream extends OutputStream {
        private OutputStream rawOutputStream;

        @Override
        public void write(int i) throws IOException {
            if (rawOutputStream == null) {
                responseHandler.setResponseHeader(responseHeader);
                rawOutputStream = responseHandler.addData();
            }

            rawOutputStream.write(i);
        }
    }

    final private String responseHeader;
    final private InputStream inputStream;
    final private RemoteOutputStream outputStream = new RemoteOutputStream();
    final private Portal.ResponseHandler responseHandler;

    public ServerPipe(String responseHeader, Portal.ResponseHandler responseHandler, InputStream inputStream) {
        this.responseHeader = responseHeader;
        this.inputStream = inputStream;
        this.responseHandler = responseHandler;
    }

    @Override
    public InputStream getInputStream() {
        return inputStream;
    }

    @Override
    public OutputStream getOutputStream() {
        return outputStream;
    }
}
