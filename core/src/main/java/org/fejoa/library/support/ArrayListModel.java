/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.support;

import java.util.ArrayList;


public class ArrayListModel<T> extends ListModel<T> {
    final ArrayList<T> list = new ArrayList<>();

    @Override
    public int size() {
        return list.size();
    }

    @Override
    public T get(int i) {
        return list.get(i);
    }

    public void add(T item) {
        add(size(), item);
    }

    public void add(int index, T item) {
        list.add(index, item);
        notifyAdded(index, item);
    }

    public int indexOf(T item) {
        return list.indexOf(item);
    }

    public T removed(int index) {
        T removed = list.remove(index);
        notifyRemoved(index, removed);
        return removed;
    }
}
