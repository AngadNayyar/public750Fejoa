/*
 * Copyright 2017.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.chunkstore;

import org.fejoa.library.crypto.CryptoHelper;
import org.fejoa.library.crypto.IMessageDigestFactory;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;


public class ChunkContainerRef {
    static public class Data {
        private HashValue dataHash = Config.newDataHash();
        private ChunkContainerHeader containerHeader = new ChunkContainerHeader();

        @Override
        protected Data clone() {
            Data data = new Data();
            data.dataHash = new HashValue(dataHash);
            data.containerHeader = containerHeader.clone();
            return data;
        }

        public void write(OutputStream outputStream) throws IOException {
            outputStream.write(dataHash.getBytes());
            ChunkContainerHeaderIO.write(containerHeader, outputStream);
        }

        public void read(InputStream inputStream) throws IOException {
            dataHash = Config.newDataHash();
            new DataInputStream(inputStream).readFully(dataHash.getBytes());
            containerHeader = new ChunkContainerHeader();
            ChunkContainerHeaderIO.read(containerHeader, inputStream);
        }

        public ChunkContainerHeader getContainerHeader() {
            return containerHeader;
        }

        public HashValue getDataHash() {
            return dataHash;
        }

        public void setDataHash(HashValue dataHash) {
            this.dataHash = dataHash;
        }
    }

    static public class Box {
        private HashValue boxHash = Config.newBoxHash();
        private byte[] iv;
        private BoxHeader boxHeader = new BoxHeader();

        @Override
        protected Box clone() {
            Box box = new Box();
            box.boxHash = new HashValue(boxHash);
            if (iv != null)
                box.iv = Arrays.copyOf(iv, iv.length);
            box.boxHeader = boxHeader.clone();
            return box;
        }

        public HashValue getBoxHash() {
            return boxHash;
        }

        public void setBoxHash(HashValue boxHash) {
            this.boxHash = boxHash;
        }

        public byte[] getIV() {
            return iv;
        }

        public void setIv(byte[] iv) {
            this.iv = iv;
        }

        public BoxHeader getBoxHeader() {
            return boxHeader;
        }

        public void write(OutputStream outputStream) throws IOException {
            outputStream.write(boxHash.getBytes());
            VarInt.write(outputStream, iv.length);
            outputStream.write(iv);

            BoxHeaderIO.write(boxHeader, outputStream);
        }

        public void read(InputStream inputStream) throws IOException {
            boxHash = Config.newBoxHash();
            new DataInputStream(inputStream).readFully(boxHash.getBytes());
            int ivLength = (int)VarInt.read(inputStream);
            iv = new byte[ivLength];
            new DataInputStream(inputStream).readFully(iv);

            BoxHeaderIO.read(boxHeader, inputStream);
        }
    }

    final private Data data;
    final private Box box;

    public ChunkContainerRef() {
        this.data = new Data();
        this.box = new Box();
    }

    public ChunkContainerRef(Data data, Box box) {
        this.data = data;
        this.box = box;
    }

    public Data getData() {
        return data;
    }

    public Box getBox() {
        return box;
    }

    public ChunkPointer getBoxPointer() {
        return new ChunkPointer(data.dataHash, box.boxHash, box.iv, getData().getContainerHeader().getDataLength());
    }

    public HashValue getBoxHash() {
        return box.getBoxHash();
    }

    public BoxHeader getBoxHeader() {
        return box.getBoxHeader();
    }

    public byte[] getIV() {
        return box.iv;
    }

    public HashValue getDataHash() {
        return data.getDataHash();
    }

    public void setBoxHash(HashValue hash) {
        box.setBoxHash(hash);
    }

    public void setIV(byte[] iv) {
        box.iv = iv;
    }

    public void setDataHash(HashValue hash) {
        data.setDataHash(hash);
    }

    public ChunkContainerHeader getContainerHeader() {
        return data.getContainerHeader();
    }

    public IMessageDigestFactory getDataMessageDigestFactory() throws IOException {
        ChunkContainerHeader.HashType hashType = data.getContainerHeader().getHashType();
        switch (hashType) {
            case SHA_3:
                return new IMessageDigestFactory() {
                    @Override
                    public MessageDigest create() throws NoSuchAlgorithmException {
                        return CryptoHelper.sha3_256Hash();
                    }
                };
        }
        throw new IOException("Unsupported hash type");
    }

    public MessageDigest getDataMessageDigest() throws IOException {
        try {
            return getDataMessageDigestFactory().create();
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ChunkContainerRef))
            return false;
        ChunkContainerRef other = (ChunkContainerRef)o;
        if (!getDataHash().equals(other.getDataHash()))
            return false;
        if (!Arrays.equals(getIV(), other.getIV()))
            return false;
        return getBoxHash().equals(other.getBoxHash());
    }

    @Override
    protected ChunkContainerRef clone() {
        return new ChunkContainerRef(data.clone(), box.clone());
    }
}
