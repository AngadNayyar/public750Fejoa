/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package gui;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.fejoa.gui.javafx.MainWindow;
import org.fejoa.gui.javafx.Resources;
import org.fejoa.library.support.StorageLib;
import org.fejoa.server.CookiePerPortManager;
import org.fejoa.server.JettyServer;

import java.io.File;
import java.net.CookieHandler;
import java.net.CookiePolicy;
import java.util.Scanner;


public class ClientGui extends Application {
    private final static String MAIN_DIR = "guiTest";

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) throws Exception {
        startServer();

        File homeDir = new File(MAIN_DIR, "client");

        // Create scene with MainWindow
        Scene scene = new Scene(new MainWindow(homeDir));

        // Attach the css file for styling the JAvaFX
        File f = new File ("javafxgui/src/test/java/gui/style.css");
        scene.getStylesheets().clear();
        scene.getStylesheets().add("file:///" + f.getAbsolutePath().replace("\\", "/"));

        // Set the default width and height of the client gui on opening
        stage.setScene(scene);
        stage.setHeight(600);
        stage.setWidth(800);
        stage.setResizable(true);

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

        server = new JettyServer(StorageLib.appendDir(MAIN_DIR, "server"), JettyServer.DEFAULT_PORT);
        server.start();
    }
}
