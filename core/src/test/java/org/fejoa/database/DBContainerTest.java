/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.database;

import junit.framework.TestCase;
import org.fejoa.library.database.*;

import java.util.*;
import java.util.concurrent.ExecutionException;


public class DBContainerTest extends TestCase {
    class TestContainerObject extends DBObjectContainer {
        final public DBString value;

        public TestContainerObject() {
            value = new DBString("value");
            add(value);
        }
    }

    public void testBasics() throws ExecutionException, InterruptedException {
        IOStorageDir dir = new IOStorageDir(AsyncInterfaceUtil.fakeAsync(new MemoryIODatabase()), "");

        DBObjectContainer root = new DBObjectContainer();

        DBString value1 = new DBString("value1");
        value1.set("1");
        DBString value2 = new DBString("value2");
        value2.set("2");

        root.add(value1);
        root.add(value2);

        DBObjectContainer subContainer = new DBObjectContainer();
        root.add(subContainer, "sub");

        DBString value3= new DBString("value3");
        value3.set("3");
        subContainer.add(value3);

        root.setTo(dir);
        root.flush().get();

        assertEquals("1", new DBString(dir, "value1").get().get());
        assertEquals("2", new DBString(dir, "value2").get().get());
        assertEquals("3", new DBString(dir, "sub/value3").get().get());

        DBObjectList<DBString> fileList = new DBObjectList<>(false, new DBObjectList.IValueCreator() {
            @Override
            public DBString create(String entryName) {
                return new DBString();
            }
        });

        fileList.get("entry1").set("Entry1");
        fileList.get("entry2").set("Entry2");

        root.add(fileList, "list");
        root.flush().get();

        assertEquals(2, fileList.getDirContent().get().get().size());
        assertEquals("Entry1", new DBString(dir, "list/entry1").get().get());
        assertEquals("Entry2", new DBString(dir, "list/entry2").get().get());

        DBObjectList<TestContainerObject> dirObjectList = new DBObjectList<>(true,
                new DBObjectList.IValueCreator() {
            @Override
            public TestContainerObject create(String entryName) {
                return new TestContainerObject();
            }
        });
        dirObjectList.get("entry3").value.set("Entry3");
        dirObjectList.get("entry4").value.set("Entry4");
        subContainer.add(dirObjectList, "list2");
        subContainer.flush().get();

        assertEquals("Entry3", new DBString(dir, "sub/list2/entry3/value").get().get());
        assertEquals("Entry4", new DBString(dir, "sub/list2/entry4/value").get().get());

        subContainer.invalidate();

        root = new DBObjectContainer(dir);
        subContainer = new DBObjectContainer();
        root.add(subContainer, "sub");
        dirObjectList = new DBObjectList<>(true,
                new DBObjectList.IValueCreator() {
            @Override
            public TestContainerObject create(String entryName) {
                return new TestContainerObject();
            }
        });
        subContainer.add(dirObjectList, "list2");

        assertEquals("Entry3", dirObjectList.get("entry3").value.get().get());
        assertEquals("Entry4", dirObjectList.get("entry4").value.get().get());
        Collection<String> content = dirObjectList.getDirContent().get().get();
        assertEquals(2, content.size());
    }
}
