/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.chunkstore;

import org.fejoa.library.crypto.CryptoHelper;
import org.fejoa.library.support.StreamHelper;
import org.fejoa.library.crypto.CryptoException;

import java.io.*;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;


abstract class DirectoryEntry {
    enum TYPE {
        NONE(0x00),
        DATA(0x01),
        ENC_ATTRS_DIR(0x02),
        ATTRS_DIR(0x04),
        BASIC_FILE_ATTRS(0x08);

        final private int value;

        TYPE(int value) {
            this.value = (byte)value;
        }

        public int getValue() {
            return value;
        }
    }

    interface AttrIO {
        void read(DataInputStream inputStream) throws IOException;
        void write(DataOutputStream outputStream) throws IOException;
    }

    class BasicFileAttrsIO implements AttrIO {
        @Override
        public void read(DataInputStream inputStream) {

        }

        @Override
        public void write(DataOutputStream outputStream) {

        }
    }

    class DataIO implements AttrIO {
        @Override
        public void read(DataInputStream inputStream) throws IOException {
            dataPointer = new BoxPointer();
            dataPointer.read(inputStream);
        }

        @Override
        public void write(DataOutputStream outputStream) throws IOException {
            dataPointer.write(outputStream);
        }
    }

    class EncAttrsIO implements AttrIO {
        @Override
        public void read(DataInputStream inputStream) throws IOException {
            encAttrsDir = new BoxPointer();
            encAttrsDir.read(inputStream);
        }

        @Override
        public void write(DataOutputStream outputStream) throws IOException {
            encAttrsDir.write(outputStream);
        }
    }

    class AttrsDirIO implements AttrIO {
        @Override
        public void read(DataInputStream inputStream) throws IOException {
            attrsDir = new BoxPointer();
            attrsDir.read(inputStream);
        }

        @Override
        public void write(DataOutputStream outputStream) throws IOException {
            attrsDir.write(outputStream);
        }
    }

    static public int MAX_NAME_LENGTH = 1024 * 5;

    private String name;
    private BoxPointer dataPointer;
    private BoxPointer encAttrsDir;
    private BoxPointer attrsDir;
    private Object object;

    public DirectoryEntry(String name, BoxPointer dataPointer) {
        this.name = name;
        this.dataPointer = dataPointer;
    }

    public DirectoryEntry() {

    }

    private List<AttrIO> getAttrIOs(int value) {
        List<AttrIO> list = new ArrayList<>();
        if ((value & TYPE.DATA.value) != 0)
            list.add(new DataIO());
        if ((value & TYPE.ENC_ATTRS_DIR.value) != 0)
            list.add(new EncAttrsIO());
        if ((value & TYPE.ATTRS_DIR.value) != 0)
            list.add(new AttrsDirIO());
        if ((value & TYPE.BASIC_FILE_ATTRS.value) != 0)
            list.add(new BasicFileAttrsIO());
        return list;
    }

    private byte getAttrIOs() {
        int value = 0;
        if (dataPointer != null)
            value |= TYPE.DATA.value;
        if (encAttrsDir != null)
            value |= TYPE.ENC_ATTRS_DIR.value;
        if (attrsDir != null)
            value |= TYPE.ATTRS_DIR.value;
        return (byte)value;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof DirectoryEntry))
            return false;
        DirectoryEntry others = (DirectoryEntry)o;
        if (!name.equals(others.name))
            return false;
        if (dataPointer != null && !dataPointer.equals(others.dataPointer))
            return false;
        if (attrsDir != null && !attrsDir.equals(others.attrsDir))
            return false;
        return true;
    }

    public void setObject(Object object) {
        this.object = object;
    }

    public Object getObject() {
        return object;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BoxPointer getDataPointer() {
        return dataPointer;
    }

    public void setDataPointer(BoxPointer dataPointer) {
        this.dataPointer = dataPointer;
    }

    public BoxPointer getAttrsDir() {
        return attrsDir;
    }

    public void setAttrsDir(BoxPointer attrsDir) {
        this.attrsDir = attrsDir;
    }

    /**
     * Write the entry to a MessageDigest.
     *
     * @param messageDigest
     */
    public void dataHash(MessageDigest messageDigest) {
        DigestOutputStream digestOutputStream = new DigestOutputStream(new OutputStream() {
            @Override
            public void write(int i) throws IOException {

            }
        }, messageDigest);
        DataOutputStream outputStream = new DataOutputStream(digestOutputStream);
        try {
            writePlainData(outputStream);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void writePlainData(DataOutputStream outputStream) throws IOException {
        StreamHelper.writeString(outputStream, name);

        byte attrs = getAttrIOs();
        outputStream.writeByte(attrs);
        for (AttrIO attrIO : getAttrIOs(attrs))
            attrIO.write(outputStream);
    }

    public void write(DataOutputStream outputStream) throws IOException {
        writePlainData(outputStream);
    }

    public void read(DataInputStream inputStream) throws IOException {
        name = StreamHelper.readString(inputStream, MAX_NAME_LENGTH);
        byte attrs = inputStream.readByte();
        for (AttrIO attrIO : getAttrIOs(attrs))
            attrIO.read(inputStream);
    }
}

public class FlatDirectoryBox extends TypedBlob {
    public static class Entry extends DirectoryEntry {
        boolean isFile;

        public Entry(String name, BoxPointer dataPointer, boolean isFile) {
            super(name, dataPointer);
            this.isFile = isFile;
        }

        protected Entry(boolean isFile) {
            this.isFile = isFile;
        }

        public void markModified() {
            setDataPointer(null);
        }

        public boolean isFile() {
            return isFile;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Entry))
                return false;
            if (isFile != ((Entry) o).isFile)
                return false;
            return super.equals(o);
        }
    }

    final private Map<String, Entry> entries = new HashMap<>();

    private FlatDirectoryBox() {
        super(BlobTypes.FLAT_DIRECTORY);
    }

    static public FlatDirectoryBox create() {
        return new FlatDirectoryBox();
    }

    static public FlatDirectoryBox read(IChunkAccessor accessor, BoxPointer boxPointer)
            throws IOException, CryptoException {
        ChunkContainer chunkContainer = ChunkContainer.read(accessor, boxPointer);
        return read(chunkContainer);
    }

    static public FlatDirectoryBox read(ChunkContainer chunkContainer)
            throws IOException, CryptoException {
        return read(BlobTypes.FLAT_DIRECTORY, new DataInputStream(new ChunkContainerInputStream(chunkContainer)));
    }

    static private FlatDirectoryBox read(short type, DataInputStream inputStream) throws IOException {
        assert type == BlobTypes.FLAT_DIRECTORY;
        FlatDirectoryBox directoryBox = new FlatDirectoryBox();
        directoryBox.read(inputStream);
        return directoryBox;
    }

    public Entry addDir(String name, BoxPointer pointer) {
        Entry entry = new Entry(name, pointer, false);
        put(name, entry);
        return entry;
    }

    public Entry addFile(String name, BoxPointer pointer) {
        Entry entry = new Entry(name, pointer, true);
        put(name, entry);
        return entry;
    }

    public void put(String name, Entry entry) {
        entries.put(name, entry);
    }

    public Entry remove(String entryName) {
        return entries.remove(entryName);
    }

    public Collection<Entry> getEntries() {
        return entries.values();
    }

    public Entry getEntry(String name) {
        for (Entry entry : entries.values()) {
            if (entry.getName().equals(name))
                return entry;
        }
        return null;
    }

    public Collection<Entry> getDirs() {
        List<Entry> children = new ArrayList<>();
        for (Entry entry : entries.values()) {
            if (!entry.isFile)
                children.add(entry);
        }
        return children;
    }

    public Collection<Entry> getFiles() {
        List<Entry> children = new ArrayList<>();
        for (Entry entry : entries.values()) {
            if (entry.isFile)
                children.add(entry);
        }
        return children;
    }

    @Override
    protected void readInternal(DataInputStream inputStream) throws IOException {
        long nDirs = inputStream.readLong();
        long nFiles = inputStream.readLong();
        for (long i = 0; i < nDirs; i++) {
            Entry entry = new Entry(false);
            entry.read(inputStream);
            entries.put(entry.getName(), entry);
        }
        for (long i = 0; i < nFiles; i++) {
            Entry entry = new Entry(true);
            entry.read(inputStream);
            entries.put(entry.getName(), entry);
        }
    }

    @Override
    protected void writeInternal(DataOutputStream outputStream) throws IOException {
        Collection<Entry> dirs = getDirs();
        Collection<Entry> files = getFiles();
        outputStream.writeLong(dirs.size());
        outputStream.writeLong(files.size());
        for (Entry entry : dirs)
            entry.write(outputStream);
        for (Entry entry : files)
            entry.write(outputStream);
    }

    public HashValue hash() {
        try {
            MessageDigest messageDigest = CryptoHelper.sha256Hash();
            messageDigest.reset();
            for (Entry entry : entries.values())
                entry.dataHash(messageDigest);

            return new HashValue(messageDigest.digest());
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    @Override
    public String toString() {
        String string = "Directory Entries:";
        for (Entry entry : entries.values())
            string += "\n" + entry.getName() + " (dir " + !entry.isFile + ")" + entry.getDataPointer();
        return string;
    }
}
