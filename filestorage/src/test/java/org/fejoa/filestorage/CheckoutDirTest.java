/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.filestorage;

import junit.framework.TestCase;
import org.fejoa.library.FejoaContext;
import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.support.StorageLib;
import org.fejoa.library.database.StorageDir;
import org.fejoa.library.support.Task;

import java.io.*;
import java.util.ArrayList;
import java.util.List;


public class CheckoutDirTest extends TestCase {
    final List<String> cleanUpDirs = new ArrayList<String>();

    @Override
    public void tearDown() throws Exception {
        super.tearDown();

        for (String dir : cleanUpDirs)
            StorageLib.recursiveDeleteFile(new File(dir));
    }

    private Task.IObserver<CheckoutDir.Update, CheckoutDir.Result> createObserver(final List<CheckoutDir.Update> updates) {
        return new Task.IObserver<CheckoutDir.Update,
                CheckoutDir.Result> () {
            @Override
            public void onProgress(CheckoutDir.Update update) {
                updates.add(update);
                System.out.println("Update: " + update.file.getPath());
            }

            @Override
            public void onResult(CheckoutDir.Result result) {
                System.out.println("done");
            }

            @Override
            public void onException(Exception exception) {
                System.out.println("Error: " + exception.getMessage());
            }
        };
    }

    private void updateFile(File file, String content) throws IOException {
        BufferedOutputStream outputStream = new BufferedOutputStream(new FileOutputStream(file, false));
        outputStream.write(content.getBytes());
        outputStream.close();
    }

    public void testCheckout() throws IOException, InterruptedException, CryptoException {
        String databasePath = "checkout";
        String destination = "checkoutDestination";
        cleanUpDirs.add(databasePath);
        cleanUpDirs.add(destination);

        FejoaContext context = new FejoaContext(databasePath, null);
        File indexDir = new File(destination, ".index");
        Index index = new Index(context, indexDir, "testBranchIndex");

        StorageDir storageDir = context.getPlainStorage("testBranch");

        byte data[] = "test".getBytes();
        storageDir.putBytes("test1", data);
        storageDir.commit();
        storageDir.putBytes("dir/test1", data);
        storageDir.commit();

        List<CheckoutDir.Update> updates = new ArrayList<>();
        CheckoutDir checkoutDir = new CheckoutDir(storageDir, index, new File(destination));
        Task<CheckoutDir.Update, CheckoutDir.Result> task = checkoutDir.checkOut(true);
        task.setStartScheduler(new Task.CurrentThreadScheduler());
        task.start(createObserver(updates));
        assertEquals(2, updates.size());
        task.start(createObserver(updates));
        assertEquals(2, updates.size());

        updates.clear();
        storageDir.putBytes("test1", "update".getBytes());
        storageDir.commit();
        task.start(createObserver(updates));
        assertEquals(1, updates.size());

        // wait for a new mTime
        Thread.sleep(1000l);
        updates.clear();
        updateFile(new File(destination, "test1"), "edited");
        Task<CheckoutDir.Update, CheckoutDir.Result> checkIn = checkoutDir.checkIn();
        checkIn.setStartScheduler(new Task.CurrentThreadScheduler());
        checkIn.start(createObserver(updates));
        assertEquals(1, updates.size());
        assertEquals("edited", storageDir.readString("test1"));

        updates.clear();
        updateFile(new File(destination, "newFile"), "new content");
        checkIn.start(createObserver(updates));
        assertEquals("new content", storageDir.readString("newFile"));

        updates.clear();
        new File(destination, "test1").delete();
        checkIn.start(createObserver(updates));
        assertFalse(storageDir.listFiles("").contains("test1"));

        new File(destination, "dir/test1").delete();
        new File(destination, "dir").delete();
        checkIn.start(createObserver(updates));
        assertEquals(0, storageDir.listDirectories("").size());
    }
}
