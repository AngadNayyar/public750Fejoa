/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.chunkstore;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Lock;


/**
 * TODO fix concurrency and maybe integrate it more tight into the chunk store.
 */
public class ChunkStoreBranchLog {
    static public class Entry {
        int rev;
        HashValue id = Config.newBoxHash();
        String message = "";
        final List<HashValue> changes = new ArrayList<>();

        public Entry(int rev, HashValue id, String message) {
            this.rev = rev;
            this.id = id;
            this.message = message;
        }

        private Entry() {

        }

        public int getRev() {
            return rev;
        }

        public String getMessage() {
            return message;
        }

        public HashValue getEntryId() {
            return id;
        }

        static public Entry fromHeader(String header) {
            Entry entry = new Entry();
            if (header.equals(""))
                return entry;
            String[] parts = header.split(" ");
            if (parts.length != 3)
                return entry;

            entry.rev = Integer.parseInt(parts[0]);
            entry.id = HashValue.fromHex(parts[1]);
            entry.message = parts[2];
            return entry;
        }

        public String getHeader() {
            return "" + rev + " " + id.toHex() + " " + message;
        }

        static private Entry read(BufferedReader reader) throws IOException {
            String header = reader.readLine();
            if (header == null || header.length() == 0)
                return null;
            Entry entry = fromHeader(header);
            int nChanges = Integer.parseInt(reader.readLine());
            for (int i = 0; i < nChanges; i++) {
                String line = reader.readLine();
                if (line == null)
                    throw new IOException("Missing change in log file");
                else
                    entry.changes.add(HashValue.fromHex(line));
            }
            return entry;
        }

        public void write(OutputStream outputStream) throws IOException {
            outputStream.write((getHeader() + "\n").getBytes());
            outputStream.write(("" + changes.size() + "\n").getBytes());
            for (HashValue change : changes)
                outputStream.write((change.toHex() + "\n").getBytes());
        }
    }

    private int latestRev = 1;
    final private File logfile;
    final private Lock fileLock;
    final private List<Entry> entries = new ArrayList<>();

    public ChunkStoreBranchLog(File logfile) throws IOException {
        this.logfile = logfile;
        this.fileLock = LockBucket.getInstance().getLock(logfile.getAbsolutePath());

        read();
    }

    private void lock() {
        fileLock.lock();
    }

    private void unlock() {
        fileLock.unlock();
    }

    public List<Entry> getEntries() {
        return entries;
    }

    private void read() throws IOException {
        try {
            lock();

            BufferedReader reader;
            try {
                FileInputStream fileInputStream = new FileInputStream(logfile);
                reader = new BufferedReader(new InputStreamReader(fileInputStream));
            } catch (FileNotFoundException e) {
                return;
            }
            Entry entry;
            while ((entry = Entry.read(reader)) != null)
                entries.add(entry);

            if (entries.size() > 0)
                latestRev = entries.get(entries.size() - 1).rev + 1;
        } finally {
            unlock();
        }
    }

    private int nextRevId() {
        int currentRev = latestRev;
        latestRev++;
        return currentRev;
    }

    public Entry getLatest() {
        if (entries.size() == 0)
            return null;
        return entries.get(entries.size() - 1);
    }

    public void add(HashValue id, String message, List<HashValue> changes) throws IOException {
        Entry entry = new Entry(nextRevId(), id, message);
        entry.changes.addAll(changes);
        add(entry);
    }

    public void add(Entry entry) {
        try {
            lock();
            write(entry);
            entries.add(entry);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            unlock();
        }
    }

    private void write(Entry entry) throws IOException {
        if(!logfile.exists()) {
            logfile.getParentFile().mkdirs();
            logfile.createNewFile();
        }

        FileOutputStream outputStream = new FileOutputStream(logfile, true);
        try {
            entry.write(outputStream);
        } finally {
            outputStream.close();
        }
    }
}
