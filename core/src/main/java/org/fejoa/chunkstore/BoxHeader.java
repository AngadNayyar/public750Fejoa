/*
 * Copyright 2017.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.chunkstore;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import static org.fejoa.chunkstore.BoxHeader.BoxType.CONTAINER_BOX;
import static org.fejoa.chunkstore.BoxHeader.CompressionType.ZLIB_COMPRESSION;
import static org.fejoa.chunkstore.BoxHeader.EncType.PARENT;


class BoxHeaderIO {
    // [container type]{container type data (optional)}[enc type]{enc details(optional)}[compression]{extension}
    static public void write(BoxHeader boxHeader, OutputStream outputStream) throws IOException {
        BoxHeader.BoxType boxType = boxHeader.getBoxType();
        long containerTypeOut = boxHeader.getBoxType().value << 1;
        if (boxType.hasExtention) {
            containerTypeOut &= 1;
        }
        VarInt.write(outputStream, containerTypeOut);


        BoxHeader.EncType encType = boxHeader.getEncType();
        int encValue = encType.value << 1;
        if (encType.hasExtention) {
            encValue &= 1;
        }
        VarInt.write(outputStream, encValue);

        BoxHeader.CompressionType compressionType = boxHeader.getCompressionType();
        int compressionValue = compressionType.value << 1;
        // extension: not supported
        if (false) {
            compressionValue &= 1;
        }
        VarInt.write(outputStream, compressionValue);
    }

    static public void read(BoxHeader boxHeader, InputStream inputStream) throws IOException {
        // box type
        long boxTypeRaw = VarInt.read(inputStream);
        int boxTypeValue = (int)(boxTypeRaw >> 1);
        BoxHeader.BoxType boxType = BoxHeader.BoxType.fromInt(boxTypeValue);
        if (boxType == null)
            throw new IOException("Unknown box type: " + boxTypeValue);
        if ((boxTypeRaw & 1) != 0) {
            // not supported yet
            new ProtocolBufferLight(inputStream);
        }
        boxHeader.setBoxType(boxType);

        // enc type
        long encTypeRaw = VarInt.read(inputStream);
        int encTypeValue = (int)(encTypeRaw >> 1);
        BoxHeader.EncType encType = BoxHeader.EncType.fromInt(encTypeValue);
        if (encType == null)
            throw new IOException("Unknown enc type: " + encTypeValue);

        if ((encTypeRaw & 1) != 0) {
            // not supported
            new ProtocolBufferLight(inputStream);
        }
        boxHeader.setEncType(encType);

        // compression and extension
        long compressionTypeRaw = VarInt.read(inputStream);
        int compressionTypeValue = (int)(compressionTypeRaw >> 1);
        BoxHeader.CompressionType compressionType = BoxHeader.CompressionType.fromInt(compressionTypeValue);
        if (compressionType == null)
            throw new IOException("Unknown enc type: " + compressionTypeValue);

        if ((compressionTypeRaw & 1) != 0) {
            // not supported
            new ProtocolBufferLight(inputStream);
        }
        boxHeader.setCompressionType(compressionType);
    }
}


public class BoxHeader {
    // compression
    public enum CompressionType {
        NO_COMPRESSION(0),
        ZLIB_COMPRESSION(1);

        final public int value;
        CompressionType(int value) {
            this.value = value;
        }

        public static CompressionType fromInt(int value) {
            for (CompressionType type : CompressionType.values()) {
                if (type.value == value)
                    return type;
            }
            return null;
        }
    }

    // box type
    public enum BoxType {
        CONTAINER_BOX(0, false),
        CONTAINER_DELTA_BOX(1, true);

        final public int value;
        final public boolean hasExtention;
        BoxType(int value, boolean hasExtention) {
            this.value = value;
            this.hasExtention = hasExtention;
        }

        public static BoxType fromInt(int value) {
            for (BoxType type : BoxType.values()) {
                if (type.value == value)
                    return type;
            }
            return null;
        }
    }

    // enc type
    public enum EncType {
        PARENT(0, false); // use encryption settings from parent

        final public int value;
        final public boolean hasExtention;
        EncType(int value, boolean hasExtention) {
            this.value = value;
            this.hasExtention = hasExtention;
        }

        public static EncType fromInt(int value) {
            for (EncType type : EncType.values()) {
                if (type.value == value)
                    return type;
            }
            return null;
        }
    }

    private BoxType boxType = CONTAINER_BOX;
    private CompressionType compressionType = ZLIB_COMPRESSION;
    private EncType encType = PARENT;

    @Override
    protected BoxHeader clone() {
        BoxHeader boxHeader = new BoxHeader();
        boxHeader.boxType = boxType;
        boxHeader.compressionType = compressionType;
        boxHeader.encType = encType;
        return boxHeader;
    }

    public BoxType getBoxType() {
        return boxType;
    }

    public void setBoxType(BoxType boxType) {
        this.boxType = boxType;
    }

    public CompressionType getCompressionType() {
        return compressionType;
    }

    public void setCompressionType(CompressionType compressionType) {
        this.compressionType = compressionType;
    }

    public EncType getEncType() {
        return encType;
    }

    public void setEncType(EncType encType) {
        this.encType = encType;
    }

    public void setZlibCompression() {
        this.compressionType = ZLIB_COMPRESSION;
    }
}
