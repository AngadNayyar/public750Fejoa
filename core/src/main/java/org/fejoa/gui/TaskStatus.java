/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.gui;

import org.fejoa.library.remote.RemoteJob;
import org.fejoa.library.support.Task;


public class TaskStatus<Progress, T extends RemoteJob.Result> extends JobStatus {
    private Task<Progress, T> task;

    public void setTask(Task<Progress, T> task) {
        this.task = task;
    }

    @Override
    public void cancel() {
        super.cancel();
        task.cancel();
    }
}
