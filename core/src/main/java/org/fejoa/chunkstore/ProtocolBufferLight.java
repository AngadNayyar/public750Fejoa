/*
 * Copyright 2017.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.chunkstore;

import java.io.*;
import java.util.HashMap;
import java.util.Map;


/* used for chunking, container and encryption details
Loosely based on: https://developers.google.com/protocol-buffers/docs/encoding#types
but no need for an extra compiler.
 */
public class ProtocolBufferLight {
    enum DataType {
        VAR_INT(0),
        SIZE_64(1),
        LENGTH_DELIMITED(2),
        START_GROUP(3),
        END_GROUP(4),
        SIZE_32(5);

        final private int value;

        DataType(int value) {
            this.value = value;
        }
    }

    static class Key {
        final public DataType type;
        final public long tag;

        public Key(DataType type, long tag) {
            this.type = type;
            this.tag = tag;
        }
    }


    interface IValue {
        void write(OutputStream outputStream) throws IOException;
        void read(InputStream inputStream) throws IOException;
    }

    static class KeyValue {
        final private Key key;
        final private IValue value;

        public KeyValue(Key key, IValue value) {
            this.key = key;
            this.value = value;
        }
    }

    static class VarIntValue implements IValue {
        private long number;

        public VarIntValue(InputStream inputStream) throws IOException {
            read(inputStream);
        }

        public VarIntValue(long number) {
            this.number = number;
        }

        @Override
        public void write(OutputStream outputStream) throws IOException {
            VarInt.write(outputStream, number);
        }

        @Override
        public void read(InputStream inputStream) throws IOException {
            this.number = VarInt.read(inputStream);
        }
    }

    static class ByteValue implements IValue {
        private byte[] bytes;

        public ByteValue(InputStream inputStream) throws IOException {
            read(inputStream);
        }

        public ByteValue(byte[] bytes) {
            this.bytes = bytes;
        }

        @Override
        public void write(OutputStream outputStream) throws IOException {
            VarInt.write(outputStream, bytes.length);
            outputStream.write(bytes);
        }

        @Override
        public void read(InputStream inputStream) throws IOException {
            int length = (int)VarInt.read(inputStream);
            this.bytes = new byte[length];
            new DataInputStream(inputStream).readFully(bytes);
        }
    }

    final private Map<Integer, KeyValue> map = new HashMap<>();

    final private long DATA_TYPE_MASK = 0x7;
    final private long TAG_SHIFT = 3;

    public ProtocolBufferLight() {

    }

    public ProtocolBufferLight(byte[] bytes) throws IOException {
        read(new ByteArrayInputStream(bytes));
    }

    public ProtocolBufferLight(InputStream inputStream) throws IOException {
        read(inputStream);
    }

    private Key readKey(InputStream inputStream) throws IOException {
        long number = VarInt.read(inputStream);
        int dataType = (int)(number & DATA_TYPE_MASK);
        long tag = number >> TAG_SHIFT;
        if (dataType == DataType.VAR_INT.value)
            return new Key(DataType.VAR_INT, tag);
        if (dataType == DataType.LENGTH_DELIMITED.value)
            return new Key(DataType.LENGTH_DELIMITED, tag);

        throw new IOException("Unknown data type: " + dataType);
    }

    public void clear() {
        map.clear();
    }

    public void put(int tag, byte[] bytes) {
        map.put(tag, new KeyValue(new Key(DataType.LENGTH_DELIMITED, tag), new ByteValue(bytes)));
    }

    public void put(int tag, String string) {
        put(tag, string.getBytes());
    }

    public void put(int tag, long value) {
        map.put(tag, new KeyValue(new Key(DataType.VAR_INT, tag), new VarIntValue(value)));
    }

    public void put(int tag, int value) {
        map.put(tag, new KeyValue(new Key(DataType.VAR_INT, tag), new VarIntValue(value)));
    }

    public byte[] getBytes(int tag) {
        KeyValue keyValue = map.get(tag);
        if (keyValue == null)
            return null;
        assert keyValue.key.tag == tag;

        if (keyValue.key.type != DataType.LENGTH_DELIMITED)
            return null;
        return ((ByteValue)keyValue.value).bytes;
    }

    public String getString(int tag) {
        return new String(getBytes(tag));
    }

    public Long getLong(int tag) {
        KeyValue keyValue = map.get(tag);
        if (keyValue == null)
            return null;
        assert keyValue.key.tag == tag;

        if (keyValue.key.type != DataType.VAR_INT)
            return null;
        return ((VarIntValue)keyValue.value).number;
    }

    private void writeKey(OutputStream outputStream, Key key) throws IOException {
        long outValue = key.tag << TAG_SHIFT;
        outValue |= key.type.value;
        VarInt.write(outputStream, outValue);
    }

    public byte[] toByteArray() throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        write(outputStream);
        return outputStream.toByteArray();
    }

    public void write(OutputStream outputStream) throws IOException {
        // write number of elements
        VarInt.write(outputStream, map.size());

        for (Map.Entry<Integer, KeyValue> entry : map.entrySet()) {
            KeyValue keyValue = entry.getValue();
            writeKey(outputStream, keyValue.key);
            keyValue.value.write(outputStream);
        }
    }

    private KeyValue readKeyValue(InputStream inputStream) throws IOException {
        Key key = readKey(inputStream);
        if (key.type == DataType.VAR_INT) {
            VarIntValue varIntValue = new VarIntValue(inputStream);
            return new KeyValue(key, varIntValue);
        }
        if (key.type == DataType.LENGTH_DELIMITED) {
            ByteValue byteValue = new ByteValue(inputStream);
            return new KeyValue(key, byteValue);
        }
        throw new IOException("Unknown data type: " + key.type.value);
    }

    /**
     * Read key value pairs till the end of stream is reached.
     *
     * @param inputStream
     * @throws IOException
     */
    public void read(InputStream inputStream) throws IOException {
        map.clear();
        int size = (int)VarInt.read(inputStream);
        for (int i = 0; i < size; i++) {
            KeyValue keyValue = readKeyValue(inputStream);
            map.put((int)keyValue.key.tag, keyValue);
        }
    }

}
