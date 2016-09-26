/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.tests.gui;

import javafx.application.Application;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.EventHandler;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import org.fejoa.gui.javafx.MainWindow;
import org.fejoa.server.JettyServer;
import org.fejoa.tests.CookiePerPortManager;

import java.io.File;
import java.net.CookieHandler;
import java.net.CookiePolicy;


public class ClientGui extends Application {
    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) throws Exception {
        startServer();

        File homeDir = new File("guiTest");
        stage.setScene(new Scene(new MainWindow(homeDir)));
        stage.show();
    }

    @Override
    public void stop() throws Exception {
        super.stop();

        server.stop();
    }

    private JettyServer server;

    private void startServer() throws Exception {
        // allow cookies per port number in order so run multiple servers on localhost
        CookieHandler.setDefault(new CookiePerPortManager(null, CookiePolicy.ACCEPT_ALL));

        server = new JettyServer("guiTestServer", 8080);
        server.start();
    }
}
