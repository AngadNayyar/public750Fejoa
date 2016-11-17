/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.chunkstore;

import org.fejoa.library.support.StreamHelper;
import org.fejoa.library.crypto.CryptoException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;


public class FileBox {
    private ChunkContainer dataContainer;

    static public FileBox create(ChunkContainer chunkContainer) {
        FileBox fileBox = new FileBox();
        fileBox.dataContainer = chunkContainer;
        return fileBox;
    }

    static public FileBox read(IChunkAccessor accessor, BoxPointer pointer)
            throws IOException, CryptoException {
        FileBox fileBox = new FileBox();
        fileBox.readChunkContainer(accessor, pointer);
        return fileBox;
    }

    public void flush() throws IOException, CryptoException {
        dataContainer.flush(false);
    }

    private void readChunkContainer(IChunkAccessor accessor, BoxPointer pointer) throws IOException, CryptoException {
        dataContainer = ChunkContainer.read(accessor, pointer);
    }

    public ChunkContainer getDataContainer() {
        return dataContainer;
    }

    public BoxPointer getBoxPointer() {
        return dataContainer.getBoxPointer();
    }

    @Override
    public String toString() {
        if (dataContainer == null)
            return "empty";
        ChunkContainerInputStream inputStream = new ChunkContainerInputStream(dataContainer);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            StreamHelper.copy(inputStream, outputStream);
        } catch (IOException e) {
            e.printStackTrace();
            return "invalid";
        }
        return "FileBox: " + new String(outputStream.toByteArray());
    }
}
