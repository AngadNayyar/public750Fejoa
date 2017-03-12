/*
 * Copyright 2014.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.support;


import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;

/**
 * Collection of storage related operations.
 *
 * Note: these function may take a significant amount of time depending where the files are located. For example,
 * moving a file on the same storage is faster than moving it somewhere to a different storage because this would
 * involve a copy and a delete operation.
 *
 * TODO: add listener interfaces to monitor copy, move, delete... progress
 */
public class StorageLib {

    static public String appendDir(String baseDir, String dir) {
        String newDir = baseDir;
        if (dir.equals(""))
            return baseDir;
        if (!newDir.equals(""))
            newDir += "/";
        newDir += dir;
        return newDir;
    }

    static public String fileName(String path) {
        int lastSlash = path.lastIndexOf("/");
        if (lastSlash < 0)
            return path;
        return path.substring(lastSlash + 1);
    }

    static public String dirName(String path) {
        int lastSlash = path.lastIndexOf("/");
        if (lastSlash < 0)
            return "";
        return path.substring(0, lastSlash);
    }

    /**
     * I file is a directory it deletes it recursively. If it is just a file it just deletes this file.
     * @param file
     * @return false on the first file that can't be deleted
     */
    static public boolean recursiveDeleteFile(File file) {
        if (file.isDirectory()) {
            String[] children = file.list();
            for (String child : children) {
                if (!recursiveDeleteFile(new File(file, child)))
                    return false;
            }
        }
        return file.delete();
    }

    static public boolean moveFile(File source, File destination) {
        if (source.isDirectory())
            return false;
        // first try to just rename the file
        if (source.renameTo(destination))
            return true;
        // this could have failed because the file is on different storage cards so to a hard copy and then delete it
        if (!copyFile(source, destination))
            return false;

        return source.delete();
    }

    /**
     * Copies a file (not a directory) from source to destination
     * @param source
     * @param destination
     * @return
     */
    static public boolean copyFile(File source, File destination) {
        try {
            FileInputStream inStream = new FileInputStream(source);
            FileOutputStream outStream = new FileOutputStream(destination);
            FileChannel inChannel = inStream.getChannel();
            inChannel.transferTo(0, inChannel.size(), outStream.getChannel());
            inStream.close();
            outStream.close();
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    static public boolean copyDir(File sourceDir, File destinationDir) {
        destinationDir.mkdirs();
        File[] files = sourceDir.listFiles();
        if (files == null)
            return true;
        for (File sub : files) {
            boolean ok;
            if (sub.isFile())
                ok = copyFile(sub, new File(destinationDir, sub.getName()));
            else
                ok = copyDir(sub, new File(destinationDir, sub.getName()));
            if (!ok)
                return false;
        }
        return true;
    }
}
