/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.chunkstore;

import org.fejoa.library.crypto.CryptoHelper;

import java.io.*;
import java.util.ArrayList;
import java.util.List;


/**
 * TODO fix concurrency and maybe integrate it more tight into the chunk store.
 */
public class ChunkStoreBranchLog {
    static public class Entry {
        int rev;
        String message = "";
        final List<HashValue> changes = new ArrayList<>();

        public Entry(int rev, String message) {
            this.rev = rev;
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
            return new HashValue(CryptoHelper.sha256Hash(message.getBytes()));
            //return entryId;
        }

        static public Entry fromHeader(String header) {
            Entry entry = new Entry();
            if (header.equals(""))
                return entry;
            int splitIndex1 = header.indexOf(" ");
            if (splitIndex1 < 0) {
                entry.rev = Integer.parseInt(header);
            } else {
                entry.rev = Integer.parseInt(header.substring(0, splitIndex1));
                entry.message = header.substring(splitIndex1 + 1);
            }
            return entry;
        }

        public String getHeader() {
            return "" + rev + " " + message;
        }

        static private Entry read(BufferedReader reader) throws IOException {
            String header = reader.readLine();
            if (header == null || header.length() == 0)
                return null;
            Entry entry = fromHeader(header);
            int nChanges = Integer.parseInt(reader.readLine());
            for (int i = 0; i < nChanges; i++) {
                entry.changes.add(HashValue.fromHex(reader.readLine()));
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
    final private List<Entry> entries = new ArrayList<>();

    public ChunkStoreBranchLog(File logfile) throws IOException {
        this.logfile = logfile;

        read();
    }

    public void lock() {
        // TODO: implement
    }

    public void unlock() {
        // TODO: implement
    }

    public List<Entry> getEntries() {
        return entries;
    }

    private void read() throws IOException {
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
            latestRev = entries.get(0).rev + 1;
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

    public void add(String message, List<HashValue> changes) throws IOException {
        Entry entry = new Entry(nextRevId(), message);
        entry.changes.addAll(changes);
        write(entry);
        entries.add(entry);
    }

    private void write(Entry entry) throws IOException {
        if(!logfile.exists()) {
            logfile.getParentFile().mkdirs();
            logfile.createNewFile();
        }

        FileOutputStream outputStream = new FileOutputStream(logfile, false);
        try {
            entry.write(outputStream);
        } finally {
            outputStream.close();
        }
    }
}
