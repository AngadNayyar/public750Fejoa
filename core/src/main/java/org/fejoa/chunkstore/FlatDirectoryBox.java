/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.chunkstore;

import org.fejoa.library.support.StreamHelper;
import org.fejoa.library.crypto.CryptoException;

import java.io.*;
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
        void read(DataInputStream inputStream, List<ChunkContainerRef> readRefs) throws IOException;
        void write(DataOutputStream outputStream, List<ChunkContainerRef> writtenRefs) throws IOException;
    }

    class BasicFileAttrsIO implements AttrIO {
        @Override
        public void read(DataInputStream inputStream, List<ChunkContainerRef> readRefs) {
            throw new RuntimeException("BasicFileAttrsIO not implemented");
        }

        @Override
        public void write(DataOutputStream outputStream, List<ChunkContainerRef> writtenRefs) {
            throw new RuntimeException("BasicFileAttrsIO not implemented");
        }
    }

    class DataIO implements AttrIO {
        @Override
        public void read(DataInputStream inputStream, List<ChunkContainerRef> readRefs) throws IOException {
            dataPointer = new ChunkContainerRef();
            dataPointer.getData().read(inputStream);
            readRefs.add(dataPointer);
        }

        @Override
        public void write(DataOutputStream outputStream, List<ChunkContainerRef> writtenRefs) throws IOException {
            dataPointer.getData().write(outputStream);
            writtenRefs.add(dataPointer);
        }
    }

    class EncAttrsIO implements AttrIO {
        @Override
        public void read(DataInputStream inputStream, List<ChunkContainerRef> readRefs) throws IOException {
            encAttrsDir = new ChunkContainerRef();
            encAttrsDir.getData().read(inputStream);
            readRefs.add(encAttrsDir);
        }

        @Override
        public void write(DataOutputStream outputStream, List<ChunkContainerRef> writtenRefs) throws IOException {
            encAttrsDir.getData().write(outputStream);
            writtenRefs.add(encAttrsDir);
        }
    }

    class AttrsDirIO implements AttrIO {
        @Override
        public void read(DataInputStream inputStream, List<ChunkContainerRef> readRefs) throws IOException {
            attrsDir = new ChunkContainerRef();
            attrsDir.getData().read(inputStream);
            readRefs.add(attrsDir);
        }

        @Override
        public void write(DataOutputStream outputStream, List<ChunkContainerRef> writtenRefs) throws IOException {
            attrsDir.getData().write(outputStream);
            writtenRefs.add(attrsDir);
        }
    }

    static public int MAX_NAME_LENGTH = 1024 * 5;

    private String name;
    private ChunkContainerRef dataPointer;
    private ChunkContainerRef encAttrsDir;
    private ChunkContainerRef attrsDir;
    private Object object;

    public DirectoryEntry(String name, ChunkContainerRef dataPointer) {
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

    public ChunkContainerRef getDataPointer() {
        return dataPointer;
    }

    public void setDataPointer(ChunkContainerRef dataPointer) {
        this.dataPointer = dataPointer;
    }

    public ChunkContainerRef getAttrsDir() {
        return attrsDir;
    }

    public void setAttrsDir(ChunkContainerRef attrsDir) {
        this.attrsDir = attrsDir;
    }

    public void write(DataOutputStream outputStream, List<ChunkContainerRef> writtenRefs) throws IOException {
        StreamHelper.writeString(outputStream, name);

        byte attrs = getAttrIOs();
        outputStream.writeByte(attrs);
        for (AttrIO attrIO : getAttrIOs(attrs))
            attrIO.write(outputStream, writtenRefs);
    }

    public void read(DataInputStream inputStream, List<ChunkContainerRef> readRefs) throws IOException {
        name = StreamHelper.readString(inputStream, MAX_NAME_LENGTH);
        byte attrs = inputStream.readByte();
        for (AttrIO attrIO : getAttrIOs(attrs))
            attrIO.read(inputStream, readRefs);
    }
}

public class FlatDirectoryBox extends ChunkContainerRefBox {
    public static class Entry extends DirectoryEntry {
        boolean isFile;

        public Entry(String name, ChunkContainerRef dataPointer, boolean isFile) {
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
        FlatDirectoryBox box = new FlatDirectoryBox();
        box.setRef(new ChunkContainerRef());
        return box;
    }

    static public FlatDirectoryBox read(IChunkAccessor accessor, ChunkContainerRef ref)
            throws IOException, CryptoException {
        ChunkContainer chunkContainer = ChunkContainer.read(accessor, ref);
        return read(chunkContainer);
    }

    static public FlatDirectoryBox read(ChunkContainer chunkContainer)
            throws IOException, CryptoException {
        return read(BlobTypes.FLAT_DIRECTORY, new DataInputStream(new ChunkContainerInputStream(chunkContainer)),
                chunkContainer.getRef());
    }

    static private FlatDirectoryBox read(short type, DataInputStream inputStream, ChunkContainerRef ref)
            throws IOException {
        assert type == BlobTypes.FLAT_DIRECTORY;
        FlatDirectoryBox directoryBox = new FlatDirectoryBox();
        directoryBox.read(inputStream, ref);
        return directoryBox;
    }

    public Entry addDir(String name, ChunkContainerRef ref) {
        Entry entry = new Entry(name, ref, false);
        put(name, entry);
        return entry;
    }

    public Entry addFile(String name, ChunkContainerRef ref) {
        Entry entry = new Entry(name, ref, true);
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
    protected void writePlain(DataOutputStream outputStream, List<ChunkContainerRef> writtenRefs) throws IOException {
        Collection<Entry> dirs = getDirs();
        Collection<Entry> files = getFiles();
        VarInt.write(outputStream, dirs.size());
        VarInt.write(outputStream, files.size());
        for (Entry entry : dirs)
            entry.write(outputStream, writtenRefs);
        for (Entry entry : files)
            entry.write(outputStream, writtenRefs);
    }


    @Override
    protected void readPlain(DataInputStream inputStream, List<ChunkContainerRef> readRefs) throws IOException {
        long nDirs = VarInt.read(inputStream);
        long nFiles = VarInt.read(inputStream);
        for (long i = 0; i < nDirs; i++) {
            Entry entry = new Entry(false);
            entry.read(inputStream, readRefs);
            entries.put(entry.getName(), entry);
        }
        for (long i = 0; i < nFiles; i++) {
            Entry entry = new Entry(true);
            entry.read(inputStream, readRefs);
            entries.put(entry.getName(), entry);
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
