/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.gui.javafx;

import javafx.collections.ListChangeListener;
import javafx.scene.control.ListView;
import org.fejoa.gui.JobStatus;
import org.fejoa.gui.IStatusManager;


public class StatusView extends ListView<JobStatus> implements IStatusManager {
    private JobStatus.IListener listener = new JobStatus.IListener() {
        @Override
        public void onUpdated(JobStatus that) {
            int i = getItems().indexOf(that);
            getItems().set(i, that);
            //EventType<? extends ListView.EditEvent<JobStatus>> type = (EventType<? extends EditEvent<JobStatus>>) StatusView.editCommitEvent();
            //fireEvent(new ListView.EditEvent<>(StatusView.this, type, getItems().get(i), i));
        }
    };

    public StatusView() {
        getItems().addListener(new ListChangeListener<JobStatus>() {
            @Override
            public void onChanged(Change<? extends JobStatus> change) {
                while (change.next()) {
                    for (JobStatus job : change.getAddedSubList())
                        job.addListener(listener);
                }
            }
        });
    }

    @Override
    public void addJobStatus(JobStatus jobStatus) {
        getItems().add(0, jobStatus);
    }
}
