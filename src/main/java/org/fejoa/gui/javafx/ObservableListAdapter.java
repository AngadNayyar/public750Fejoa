/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.gui.javafx;

import javafx.collections.ObservableListBase;
import org.fejoa.library.support.ListModel;


public class ObservableListAdapter<T> extends ObservableListBase<T> {
    final private ListModel<T> listModel;
    final private ListModel.IListener<T> listener;

    public ObservableListAdapter(ListModel<T> listModel) {
        this.listModel = listModel;
        notifyAdded(0, size());

        this.listener = new ListModel.IListener<T>() {
            @Override
            public void onAdded(int i, T item) {
                notifyAdded(i, i + 1);
            }

            @Override
            public void onRemoved(int i, T item) {
                try {
                    beginChange();
                    nextRemove(i, item);
                } finally {
                    endChange();
                }
            }

            @Override
            public void onUpdated(int i) {
                try {
                    beginChange();
                    nextUpdate(i);
                } finally {
                    endChange();
                }
            }
        };
        listModel.addListener(listener);
    }

    private void notifyAdded(int start, int end) {
        try {
            beginChange();
            nextAdd(start, end);
        } finally {
            endChange();
        }
    }

    @Override
    public T get(int i) {
        return listModel.get(i);
    }

    @Override
    public int size() {
        return listModel.size();
    }
}
