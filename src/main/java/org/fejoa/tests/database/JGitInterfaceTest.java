/*
 * Copyright 2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.tests.database;

import junit.framework.TestCase;
import org.fejoa.chunkstore.Config;
import org.fejoa.chunkstore.HashValue;
import org.fejoa.library.database.DatabaseDiff;
import org.fejoa.library.database.DatabaseDir;
import org.fejoa.library.support.StorageLib;
import org.fejoa.library.database.JGitInterface;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;


public class JGitInterfaceTest extends TestCase {
    final List<String> cleanUpDirs = new ArrayList<String>();

    @Override
    public void setUp() throws Exception {
        super.setUp();

    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();

        for (String dir : cleanUpDirs)
            StorageLib.recursiveDeleteFile(new File(dir));
    }

    public void testInitWriteRead() throws Exception {
        testInitWriteReadSimple("gitTest", "test");
    }

    public void testInitWriteReadSubdirs() throws Exception {
        testInitWriteReadSimple("gitTest2", "sub/dir/test");
    }

    private void testInitWriteReadSimple(String gitDir, String dataPath) throws Exception {
        cleanUpDirs.add(gitDir);

        JGitInterface git = new JGitInterface();
        git.init(gitDir, "testBranch", true);

        String testString = "Hello jGit!";
        try {
            git.writeBytes(dataPath, testString.getBytes());
            git.commit();

            // and read again
            String result = new String(git.readBytes(dataPath));
            assertEquals(testString, result);
        } catch (Exception e) {
            System.out.print(e.getMessage());
        }
    }

    public void testDoubleWrite() throws IOException {
        String gitDir = "doubleWriteGit";
        cleanUpDirs.add(gitDir);

        JGitInterface git = new JGitInterface();
        git.init(gitDir, "testBranch", true);

        byte data[] = "test".getBytes();
        git.writeBytes("test1", data);
        git.writeBytes("test1", data);

        git.commit();

        git.writeBytes("dir/test1", data);
        git.writeBytes("dir/test1", data);

        git.commit();
    }

    public void testListEntries() throws IOException {
        String gitDir = "listEntriesGit";
        cleanUpDirs.add(gitDir);

        JGitInterface git = new JGitInterface();
        git.init(gitDir, "testBranch", true);

        byte data[] = "test".getBytes();
        git.writeBytes("test1", data);
        git.writeBytes("test2", data);

        git.writeBytes("dir/test3", data);
        git.writeBytes("dir/test4", data);

        git.writeBytes("dir2/test5", data);
        git.writeBytes("dir2/test6", data);

        git.writeBytes("dir/sub1/test7", data);
        git.writeBytes("dir/sub1/test8", data);

        git.writeBytes("dir/sub2/sub3/test9", data);
        git.writeBytes("dir/sub2/sub3/test10", data);


        git.commit();

        Collection<String> entries = git.listDirectories("");
        assertTrue(equals(entries, Arrays.asList("dir", "dir2")));

        entries = git.listDirectories("dir");
        assertTrue(equals(entries, Arrays.asList("sub1", "sub2")));

        entries = git.listDirectories("dir/sub2");
        assertTrue(equals(entries, Arrays.asList("sub3")));

        entries = git.listFiles("");
        assertTrue(equals(entries, Arrays.asList("test1", "test2")));

        entries = git.listFiles("dir");
        assertTrue(equals(entries, Arrays.asList("test3", "test4")));

        entries = git.listFiles("dir/sub1");
        assertTrue(equals(entries, Arrays.asList("test7", "test8")));

        entries = git.listFiles("dir/sub2/sub3");
        assertTrue(equals(entries, Arrays.asList("test9", "test10")));
    }

    public void testRemove() throws IOException {
        String gitDir = "removeEntriesGit";
        cleanUpDirs.add(gitDir);

        JGitInterface git = new JGitInterface();
        git.init(gitDir, "testBranch", true);

        byte data[] = "test".getBytes();
        git.writeBytes("test1", data);
        git.writeBytes("test2", data);

        git.writeBytes("dir/test3", data);
        git.writeBytes("dir/test4", data);

        git.writeBytes("dir2/test5", data);
        git.writeBytes("dir2/test6", data);

        git.writeBytes("dir/sub1/test7", data);
        git.writeBytes("dir/sub1/test8", data);

        git.writeBytes("dir/sub2/sub3/test9", data);
        git.writeBytes("dir/sub2/sub3/test10", data);

        git.commit();


        git.remove("test2");
        Collection<String> entries = git.listFiles("");
        assertTrue(equals(entries, Arrays.asList("test1")));

        git.remove("dir/sub1/test7");
        entries = git.listFiles("dir/sub1");
        assertTrue(equals(entries, Arrays.asList("test8")));

        git.remove("dir2");
        entries = git.listDirectories("");
        assertTrue(equals(entries, Arrays.asList("dir")));

        git.remove("dir/sub2");
        entries = git.listDirectories("dir");
        assertTrue(equals(entries, Arrays.asList("sub1")));

        git.remove("");
        entries = git.listDirectories("");
        assertTrue(entries.size() == 0);

        git.writeBytes("dir/file", "data".getBytes());
        git.remove("dir");
        entries = git.listDirectories("");
        assertTrue(entries.size() == 0);
    }

    private boolean equals(Collection<String> list1, Collection<String> list2) {
        return list1.containsAll(list2) && list2.containsAll(list1);
    }

    class DatabaseStingEntry {
        public String path;
        public String content;

        public DatabaseStingEntry(String path, String content) {
            this.path = path;
            this.content = content;
        }
    }

    private void add(JGitInterface database, List<DatabaseStingEntry> content, DatabaseStingEntry entry)
            throws Exception {
        content.add(entry);
        database.writeBytes(entry.path, entry.content.getBytes());
    }

    private boolean containsContent(JGitInterface database, List<DatabaseStingEntry> content) throws IOException {
        for (DatabaseStingEntry entry : content) {
            byte bytes[] = database.readBytes(entry.path);
            if (!entry.content.equals(new String(bytes)))
                return false;
        }
        return true;
    }

    public void testDiffImport() throws Exception {
        List<DatabaseStingEntry> content = new ArrayList<>();

        String gitDir = "gitDiff";
        cleanUpDirs.add(gitDir);

        JGitInterface git = new JGitInterface();
        git.init(gitDir, "testBranch", true);

        add(git, content, new DatabaseStingEntry("test1", "data1"));
        HashValue commit1 = git.commit();

        add(git, content, new DatabaseStingEntry("folder/test2", "data2"));
        add(git, content, new DatabaseStingEntry("folder/test3", "data2"));

        HashValue commit2 = git.commit();
        assertEquals(commit2, git.getTip());

        DatabaseDiff diff = git.getDiff(Config.newSha1Hash(), commit2);
        assertTrue(diff.added.getFiles().contains("test1"));
        DatabaseDir folderDir = diff.added.findDirectory("folder");
        assertNotNull(folderDir);
        assertTrue(folderDir.getFiles().contains("test2"));
        assertTrue(folderDir.getFiles().contains("test3"));

        diff = git.getDiff(commit1, commit2);
        folderDir = diff.added.findDirectory("folder");
        assertNotNull(folderDir);
        assertTrue(folderDir.getFiles().contains("test2"));
        assertTrue(folderDir.getFiles().contains("test3"));

    }
}