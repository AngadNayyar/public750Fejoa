/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.database;

import java.lang.ref.WeakReference;
import java.util.*;


public class DBObjectList<T extends IDBContainerEntry> extends DBObjectContainer {
    public interface IValueCreator<T extends IDBContainerEntry> {
        T create(String entryName);
    }

    final private IValueCreator<T> valueCreator;
    final private DBReadableObject<Collection<String>> dirContent;
    final private Map<String, WeakReference<T>> loadedEntries = new HashMap<>();

    /**
     *
     * @param dirObjects true if the objects are sub dirs otherwise they are files
     * @param creator
     */
    public DBObjectList(boolean dirObjects, IValueCreator creator) {
        this.valueCreator = creator;
        if (dirObjects)
            this.dirContent = new DBDirReader();
        else
            this.dirContent = new DBFileReader();
        add(dirContent, "");
    }

    public DBReadableObject<Collection<String>> getDirContent() {
        return dirContent;
    }

    public T get(String id) {
        WeakReference<T> weakReference = loadedEntries.get(id);
        if (weakReference != null) {
            T value = weakReference.get();
            if (value != null)
                return value;
        }

        T value = valueCreator.create(id);
        addEntry(value, id);
        return value;
    }

    public void addEntry(T entry, String id) {
        add(entry, id);
        loadedEntries.put(id, new WeakReference<>(entry));
    }

    @Override
    public void invalidate() {
        super.invalidate();

        List<String> invalid = new ArrayList<>();
        for (Map.Entry<String, WeakReference<T>> entry : loadedEntries.entrySet()) {
            if (entry.getValue().get() == null)
                invalid.add(entry.getKey());
        }
        for (String key : invalid)
            loadedEntries.remove(key);
    }
}
