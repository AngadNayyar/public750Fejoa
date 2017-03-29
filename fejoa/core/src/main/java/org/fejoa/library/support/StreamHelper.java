/*
 * Copyright 2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.support;

import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.database.ISyncRandomDataAccess;

import java.io.*;


public class StreamHelper {
    static public int BUFFER_SIZE = 8 * 1024;

    static public void copyBytes(InputStream inputStream, OutputStream outputStream, int size) throws IOException {
        int bufferLength = BUFFER_SIZE;
        byte[] buf = new byte[bufferLength];
        int bytesRead = 0;
        while (bytesRead < size) {
            int requestedBunchSize = Math.min(size - bytesRead, bufferLength);
            int read = inputStream.read(buf, 0, requestedBunchSize);
            bytesRead += read;
            outputStream.write(buf, 0, read);
        }
    }

    static public void copy(InputStream inputStream, DataOutput outputStream) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        int length;
        while ((length = inputStream.read(buffer)) > 0)
            outputStream.write(buffer, 0, length);
    }

    static public void copy(ISyncRandomDataAccess inputStream, OutputStream outputStream) throws IOException,
            CryptoException {
        byte[] buffer = new byte[BUFFER_SIZE];
        int length;
        while ((length = inputStream.read(buffer)) > 0)
            outputStream.write(buffer, 0, length);
    }

    static public void copy(InputStream inputStream, ISyncRandomDataAccess outputStream) throws IOException,
            CryptoException {
        byte[] buffer = new byte[BUFFER_SIZE];
        int length;
        while ((length = inputStream.read(buffer)) > 0)
            outputStream.write(buffer, 0, length);
    }

    static public void copy(InputStream inputStream, OutputStream outputStream) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        int length;
        while ((length = inputStream.read(buffer)) > 0)
            outputStream.write(buffer, 0, length);
    }

    static public void copy(InputStream inputStream, Writer writer) throws IOException {
        char[] buffer = new char[BUFFER_SIZE];
        int length;
        InputStreamReader reader = new InputStreamReader(inputStream);
        while ((length = reader.read(buffer)) > 0)
            writer.write(buffer, 0, length);
    }

    static public byte[] readAll(ISyncRandomDataAccess inputStream) throws IOException, CryptoException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        copy(inputStream, outputStream);
        return outputStream.toByteArray();
    }

    static public byte[] readAll(InputStream inputStream) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        copy(inputStream, outputStream);
        return outputStream.toByteArray();
    }

    static public String readString0(DataInputStream inputStream) throws IOException {
        int c = inputStream.read();
        StringBuilder builder = new StringBuilder("");
        while (c != -1 && c != 0) {
            builder.append((char) c);
            c = inputStream.read();
        }
        return builder.toString();
    }

    static public void writeString0(DataOutputStream outputStream, String string) throws IOException {
        outputStream.write(string.getBytes());
        outputStream.write(0);
    }

    static public String readString(DataInputStream inputStream, int maxLength) throws IOException {
        int length = inputStream.readInt();
        if (length > maxLength)
            throw new  IOException("String is too long: " + length);
        byte buffer[] = new byte[length];
        inputStream.readFully(buffer);
        return new String(buffer);
    }

    static public void writeString(DataOutputStream outputStream, String string) throws IOException {
        outputStream.writeInt(string.getBytes().length);
        outputStream.write(string.getBytes());
    }
}
