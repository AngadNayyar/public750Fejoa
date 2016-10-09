/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.gui;

import org.fejoa.library.support.WeakListenable;


public class JobStatus extends WeakListenable<JobStatus.IListener> {
    public interface IListener {
        void onUpdated(JobStatus that);
    }

    private boolean isCanceled;
    private boolean isFailed;
    private boolean isDone;

    private String status;

    public boolean isCanceled() {
        return isCanceled;
    }

    public void setCanceled() {
        isCanceled = true;
        setDone();
    }

    public void cancel() {
        setCanceled();
    }

    public boolean isFailed() {
        return isFailed;
    }

    public void setFailed() {
        isFailed = true;
        setDone();
    }

    public void setFailed(String status) {
        setStatus(status);
        setFailed();
    }

    public boolean isDone() {
        return isDone;
    }

    public void setDone() {
        isDone = true;
        notifyUpdated();
    }

    public void setDone(String status) {
        setStatus(status);
        setDone();
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
        notifyUpdated();
    }

    private void notifyUpdated() {
        for (IListener listener : getListeners())
            listener.onUpdated(this);
    }

    @Override
    public String toString() {
        return status;
    }
}
