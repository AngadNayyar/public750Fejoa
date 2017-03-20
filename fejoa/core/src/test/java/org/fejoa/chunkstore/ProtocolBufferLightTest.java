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


public class ProtocolBufferLightTest extends TestCase {
    public void testSimple() throws IOException {
        ProtocolBufferLight protocolBufferLight = new ProtocolBufferLight();
        protocolBufferLight.put(1, 200);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        protocolBufferLight.write(outputStream);
        protocolBufferLight.clear();
        ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
        protocolBufferLight.read(inputStream);
        assertEquals(protocolBufferLight.getLong(1).intValue(), 200);

        protocolBufferLight.clear();
        protocolBufferLight.put(1, "Test");
        outputStream = new ByteArrayOutputStream();
        protocolBufferLight.write(outputStream);
        protocolBufferLight.clear();
        inputStream = new ByteArrayInputStream(outputStream.toByteArray());
        protocolBufferLight.read(inputStream);
        assertEquals(protocolBufferLight.getString(1), "Test");

        protocolBufferLight.clear();
        protocolBufferLight.put(1, "Test");
        protocolBufferLight.put(2, 123);
        protocolBufferLight.put(3, Long.MAX_VALUE / 2);
        protocolBufferLight.put(4, "Test 2");
        protocolBufferLight.put(5, "Test 5");
        outputStream = new ByteArrayOutputStream();
        protocolBufferLight.write(outputStream);
        protocolBufferLight.clear();
        inputStream = new ByteArrayInputStream(outputStream.toByteArray());
        protocolBufferLight.read(inputStream);
        assertEquals(protocolBufferLight.getString(1), "Test");
        assertEquals(protocolBufferLight.getLong(2).longValue(), 123);
        assertEquals(protocolBufferLight.getLong(3).longValue(), Long.MAX_VALUE / 2);
        assertEquals(protocolBufferLight.getString(4), "Test 2");
        assertEquals(protocolBufferLight.getString(5), "Test 5");

        protocolBufferLight.clear();
        protocolBufferLight.put(1, "Test");
        outputStream = new ByteArrayOutputStream();
        protocolBufferLight.write(outputStream);
        protocolBufferLight.clear();
        outputStream.write("7Random data".getBytes());
        inputStream = new ByteArrayInputStream(outputStream.toByteArray());
        protocolBufferLight.read(inputStream);
        assertEquals(protocolBufferLight.getString(1), "Test");

    }
}
