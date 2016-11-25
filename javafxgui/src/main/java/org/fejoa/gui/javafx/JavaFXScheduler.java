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

import java.util.concurrent.Executor;


public class JavaFXScheduler implements Executor {
    @Override
    public void execute(final Runnable runnable) {
        Platform.runLater(runnable);
    }
}
