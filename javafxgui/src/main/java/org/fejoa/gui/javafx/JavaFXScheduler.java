/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.gui.javafx;

import javafx.application.Platform;
import org.fejoa.library.support.Task;


public class JavaFXScheduler implements Task.IScheduler {
    @Override
    public void run(final Runnable runnable) {
        Platform.runLater(runnable);
    }
}
