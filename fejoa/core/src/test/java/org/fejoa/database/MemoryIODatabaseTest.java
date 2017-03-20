/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.database;

import junit.framework.TestCase;
import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.database.MemoryIODatabase;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;


public class MemoryIODatabaseTest extends TestCase {

    private void add(Map<String, byte[]> data, MemoryIODatabase database, String key, String value)
            throws IOException, CryptoException {
        while (key.length() > 0 && key.charAt(0) == '/')
            key = key.substring(1);
        data.put(key, value.getBytes());
        database.putBytes(key, value.getBytes());
    }

    private void assertEquals(Map<String, byte[]> data, MemoryIODatabase database) {
        Map<String, byte[]> databaseEntries = database.getEntries();
        assertEquals(data.size(), databaseEntries.size());
        for (Map.Entry<String, byte[]> entry : data.entrySet()) {
            assertTrue(databaseEntries.containsKey(entry.getKey()));
            assertTrue(Arrays.equals(databaseEntries.get(entry.getKey()), entry.getValue()));
        }
    }

    public void testBasics() throws IOException, CryptoException {
        MemoryIODatabase database = new MemoryIODatabase();

        Map<String, byte[]> expected = new HashMap<>();
        add(expected, database, "test", "test");
        add(expected, database, "/test2", "test2");
        add(expected, database, "sub/test3", "test3");
        add(expected, database, "sub/test4", "test4");
        add(expected, database, "sub/sub2/test5", "test5");
        add(expected, database, "sub/sub2/test6", "test6");
        add(expected, database, "sub/sub3/test7", "test7");

        assertEquals(expected, database);

        assertEquals(1, database.listDirectories("").size());
        assertEquals(2, database.listFiles("").size());

        assertEquals(2, database.listDirectories("sub").size());
        assertEquals(2, database.listFiles("sub").size());

        assertEquals(0, database.listDirectories("sub/sub2").size());
        assertEquals(2, database.listFiles("sub/sub2").size());

        assertEquals(0, database.listDirectories("sub/sub3").size());
        assertEquals(1, database.listFiles("sub/sub3").size());

        assertFalse(database.hasFile("sub/invalid/invalidFile"));

        database.remove("sub/sub3/test7");
        assertFalse(database.hasFile("sub/sub3/test7"));
        assertEquals(1, database.listDirectories("sub").size());

        database.remove("sub/test4");
        assertFalse(database.hasFile("sub/test4"));
        assertEquals(1, database.listFiles("sub").size());

        database.remove("sub/sub2/test5");
        database.remove("sub/sub2/test6");
        assertEquals(0, database.listDirectories("sub").size());

        database.remove("sub/test3");
        assertEquals(0, database.listDirectories("sub").size());

        database.remove("test");
        database.remove("test2");

        assertEquals(0, database.listDirectories("").size());
    }
}
