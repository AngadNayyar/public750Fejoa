/*
 * Copyright 2017.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.chunkstore;


import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Number that can be stored efficiently if small.
 *
 * The same MSB encoding as described in
 * http://git.kernel.org/cgit/git/git.git/tree/Documentation/technical/pack-format.txt?id=HEAD
 * is used.
 */
public class VarInt {
    /**
     *
     * @param inputStream
     * @param bitsToUseFromFirstByte the number of lower bits to use from the first byte
     */
    static public long read (InputStream inputStream, int bitsToUseFromFirstByte) throws IOException {
        return read(inputStream, 0l, bitsToUseFromFirstByte, 0);
    }

    static public long read(InputStream inputStream) throws IOException {
        return read(inputStream, 0l, 8, 0);
    }

    static private long read(InputStream inputStream, long currentValue, int bitsToUse, int position)
            throws IOException {
        // only for the first byte we allow less than 8 bits to be used
        if (bitsToUse != 8)
            assert position == 0;

        int nextByte = inputStream.read();
        if (nextByte < 0)
            throw new IOException("Unexpected EOF");
        boolean hasNextByte = (nextByte & (0x1 << (bitsToUse - 1))) != 0;
        int mask = 0xFF >> (8 - bitsToUse + 1);
        long data = nextByte & mask;
        currentValue |= data << position;

        if (!hasNextByte)
            return currentValue;
        position += bitsToUse - 1;
        return read(inputStream, currentValue, 8, position);
    }

    static public void write(OutputStream outputStream, long number) throws IOException {
        write(outputStream, number, 8);
    }

    static public void write(OutputStream outputStream, long number, int firstBitsSize) throws IOException {
        int stepSize = firstBitsSize - 1;
        int prevStepSize = stepSize;
        int byteToWrite = 0;
        for (int i = 0; i <= 64; i += stepSize) {
            if (i > 0) {
                prevStepSize = stepSize;
                stepSize = 7;
            }
            long windowMask = (0xFFL >> (8 - stepSize));
            long mask = windowMask << i;
            int nextByte = (int)((number & mask) >> i);

            if (i == 0) {
                byteToWrite = nextByte;
                continue;
            }

            if (nextByte != 0)
                byteToWrite |= (0x1 << prevStepSize);
            outputStream.write(byteToWrite);

            byteToWrite = nextByte;
            if (nextByte == 0)
                break;
        }
        if (byteToWrite != 0)
            outputStream.write(byteToWrite);
    }
}
