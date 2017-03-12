/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.database;

import org.fejoa.chunkstore.ChunkContainer;
import org.fejoa.chunkstore.ChunkContainerInputStream;
import org.fejoa.chunkstore.ChunkContainerOutputStream;
import org.fejoa.library.crypto.CryptoException;

import java.io.IOException;

import static org.fejoa.library.database.IIOSyncDatabase.Mode.READ;
import static org.fejoa.library.database.IIOSyncDatabase.Mode.WRITE;


public class ChunkContainerRandomDataAccess implements ISyncRandomDataAccess {
    public interface IIOCallback {
        void requestRead(ChunkContainerRandomDataAccess caller) throws IOException;
        void requestWrite(ChunkContainerRandomDataAccess caller) throws IOException;
        void onClosed(ChunkContainerRandomDataAccess caller) throws IOException, CryptoException;
    }

    private ChunkContainer chunkContainer;
    final private IIOSyncDatabase.Mode mode;
    final private IIOCallback callback;

    private long position = 0;
    private ChunkContainerInputStream inputStream = null;
    private ChunkContainerOutputStream outputStream = null;

    public ChunkContainerRandomDataAccess(ChunkContainer chunkContainer, IIOSyncDatabase.Mode mode,
                                          IIOCallback callback) {
        this.chunkContainer = chunkContainer;
        this.mode = mode;
        this.callback = callback;
    }

    public ChunkContainer getChunkContainer() {
        return chunkContainer;
    }

    // Discard all ongoing write action and set a new chunk container.
    // TODO: This is temporary as long ChunkContainer does not support truncate.
    public void setChunkContainer(ChunkContainer chunkContainer) {
        inputStream = null;
        outputStream = null;
        this.chunkContainer = chunkContainer;
    }

    public void cancel() {
        chunkContainer = null;
        position = -1;
        inputStream = null;
        outputStream = null;
    }

    public IIOSyncDatabase.Mode getMode() {
        return mode;
    }

    private void checkNotCanceled() throws IOException {
        if (chunkContainer == null)
            throw new IOException("Access has been canceled");
    }

    private void prepareForWrite() throws IOException {
        checkNotCanceled();

        if (inputStream != null) {
            inputStream.close();
            inputStream = null;
        }
        if (outputStream == null) {
            outputStream = new ChunkContainerOutputStream(chunkContainer);
            outputStream.seek(position);
        }

        callback.requestWrite(this);
    }

    private void prepareForRead() throws IOException, CryptoException {
        checkNotCanceled();

        if (outputStream != null) {
            outputStream.close();
            outputStream = null;
        }
        if (inputStream == null) {
            inputStream = new ChunkContainerInputStream(chunkContainer);
            inputStream.seek(position);
        }

        callback.requestRead(this);
    }

    @Override
    public long length() {
        return Math.max(chunkContainer.getDataLength(), position);
    }

    @Override
    public long position() {
        return position;
    }

    @Override
    public void seek(long position) throws IOException, CryptoException {
        this.position = position;
        if (inputStream != null)
            inputStream.seek(position);
        else if (outputStream != null)
            outputStream.seek(position);
    }

    @Override
    public void write(byte[] data) throws IOException {
        write(data, 0, data.length);
    }

    @Override
    public int read(byte[] buffer) throws IOException, CryptoException {
        return read(buffer, 0, buffer.length);
    }

    @Override
    public void write(byte[] data, int offset, int length) throws IOException {
        prepareForWrite();
        outputStream.write(data, offset, length);
        position += length;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException, CryptoException {
        prepareForRead();
        int read = inputStream.read(buffer, offset, length);
        position += read;
        return read;
    }

    @Override
    public void flush() throws IOException {
        if (!mode.has(WRITE))
            throw new IOException("Can't flush in read only mode.");
        if (outputStream != null)
            outputStream.flush();
    }

    @Override
    public void close() throws IOException, CryptoException {
        if (inputStream != null) {
            inputStream.close();
            inputStream = null;
        } else if (outputStream != null) {
            outputStream.close();
            outputStream = null;
        }
        callback.onClosed(this);
    }
}
