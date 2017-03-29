/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.gui;


public class StatusManagerMessenger {
    final private IStatusManager statusManager;

    public StatusManagerMessenger(IStatusManager statusManager) {
        this.statusManager = statusManager;
    }

    public void info(String info) {
        JobStatus jobStatus = new JobStatus();
        jobStatus.setDone(info);
        statusManager.addJobStatus(jobStatus);
    }

    public void error(Exception e) {
        JobStatus jobStatus = new JobStatus();
        jobStatus.setFailed(e.getMessage());
        statusManager.addJobStatus(jobStatus);
    }
}
