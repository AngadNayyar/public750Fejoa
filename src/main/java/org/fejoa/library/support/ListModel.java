/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.support;

import java.util.Iterator;


public abstract class ListModel<T> extends WeakListenable<ListModel.IListener<T>> implements Iterable<T> {
    public interface IListener<T> {
        void onAdded(int i, T item);
        void onRemoved(int i, T item);
        void onUpdated(int i);
    }

    public abstract int size();
    public abstract T get(int i);

    protected void notifyAdded(int i, T item) {
        for (IListener<T> listener : getListeners())
            listener.onAdded(i, item);
    }

    protected void notifyRemoved(int i, T item) {
        for (IListener<T> listener : getListeners())
            listener.onRemoved(i, item);
    }

    protected void notifyUpdated(int i) {
        for (IListener<T> listener : getListeners())
            listener.onUpdated(i);
    }

    @Override
    public Iterator<T> iterator() {
        return new Iterator<T>() {
            int i = 0;

            @Override
            public void remove() {

            }

            @Override
            public boolean hasNext() {
                return i < size();
            }

            @Override
            public T next() {
                T item = get(i);
                i++;
                return item;
            }
        };
    }
}
