/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.gui.javafx;

import javafx.scene.control.ListView;
import javafx.scene.control.SplitPane;
import org.fejoa.gui.IStatusManager;
import org.fejoa.library.Client;


public class MessengerView extends SplitPane {
    final private Client client;
    final private IStatusManager statusManager;

    public MessengerView(Client client, IStatusManager statusManager) {
        this.client = client;
        this.statusManager = statusManager;

        ListView<String> branchListView = new ListView<>();
        getChildren().add(branchListView);

    }


}
