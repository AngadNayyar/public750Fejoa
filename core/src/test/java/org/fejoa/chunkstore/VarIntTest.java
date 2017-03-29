/*
 * Copyright 2017.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.chunkstore;

import junit.framework.TestCase;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;


public class VarIntTest extends TestCase {

    private void assertParsing(byte[] data, long expected, int bitsToUse) throws IOException {
        ByteArrayInputStream inputStream = new ByteArrayInputStream(data);
        assertEquals(expected, VarInt.read(inputStream, bitsToUse));
    }

    private void assertParsing(byte[] data, long expected) throws IOException {
        ByteArrayInputStream inputStream = new ByteArrayInputStream(data);
        assertEquals(expected, VarInt.read(inputStream));
    }

    private void assertWriteAndParsing(long number, int firstBitsToUse) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        VarInt.write(outputStream, number, firstBitsToUse);
        byte[] out = outputStream.toByteArray();
        assertParsing(out, number, firstBitsToUse);
    }

    private void assertWriteAndParsing(long number) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        VarInt.write(outputStream, number);
        byte[] out = outputStream.toByteArray();
        assertParsing(out, number);
    }

    public void testSimple() throws IOException {
        byte[] data = new byte[2];
        data[0] = 2;
        assertParsing(data, 2);

        data[0] = 120;
        assertParsing(data, 120);

        data[0] = 13;
        data[0] = (byte)(data[0] | (0x1 << 6));
        assertParsing(data, 13, 6);

        data[0] = 0;
        data[0] = (byte)(data[0] | (0x1 << 7));
        data[1] = 1;
        assertParsing(data, 128);

        data[0] = 0;
        data[0] = (byte)(data[0] | (0x1 << 7));
        data[0] = (byte)(data[0] | (0x1 << 6));
        data[1] = 1;
        assertParsing(data, 192);

        assertWriteAndParsing(13);

        assertWriteAndParsing(13, 6);

        assertWriteAndParsing(500);

        assertWriteAndParsing(500, 4);

        assertWriteAndParsing((long)Integer.MAX_VALUE * 2L);

        assertWriteAndParsing(Long.MAX_VALUE);

        assertWriteAndParsing(Integer.MAX_VALUE, 4);

        assertWriteAndParsing((long)Integer.MAX_VALUE * 2L, 4);

        assertWriteAndParsing(Long.MAX_VALUE, 4);
    }
}
