/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.server;

import org.apache.commons.cli.*;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.session.HashSessionIdManager;
import org.eclipse.jetty.server.session.HashSessionManager;
import org.eclipse.jetty.server.session.SessionHandler;

import java.io.File;
import java.net.InetSocketAddress;


class DebugSingleton {
    private boolean noAccessControl = false;

    static private DebugSingleton INSTANCE = null;

    static public DebugSingleton get() {
        if (INSTANCE != null)
            return INSTANCE;
        INSTANCE = new DebugSingleton();
        return INSTANCE;
    }

    public void setNoAccessControl(boolean noAccessControl) {
        this.noAccessControl = noAccessControl;
    }

    public boolean isNoAccessControl() {
        return noAccessControl;
    }
}

public class JettyServer {
    final static public int DEFAULT_PORT = 8180;
    final private Server server;

    public static void main(String[] args) throws Exception {
        Options options = new Options();

        Option input = new Option("h", "host", true, "Host ip address");
        input.setRequired(true);
        options.addOption(input);

        Option output = new Option("p", "port", true, "Host port");
        output.setRequired(true);
        options.addOption(output);

        Option dirOption = new Option("d", "directory", true, "Storage directory");
        dirOption.setRequired(false);
        options.addOption(dirOption);

        CommandLine cmd;
        try {
            cmd = new DefaultParser().parse(options, args);
        } catch (ParseException e) {
            System.out.println(e.getMessage());
            new HelpFormatter().printHelp("Fejoa Server", options);

            System.exit(1);
            return;
        }

        String host = cmd.getOptionValue("host");
        Integer port;
        try {
            port = Integer.parseInt(cmd.getOptionValue("port"));
        } catch (NumberFormatException e) {
            System.out.println("Port must be a number");
            System.exit(1);
            return;
        }

        String directory = ".";
        if (cmd.hasOption("directory"))
            directory = cmd.getOptionValue("directory");

        JettyServer server = new JettyServer(directory, host, port);
        server.start();
    }

    public JettyServer(String baseDir) {
        this(baseDir, null, DEFAULT_PORT);
    }

    public JettyServer(String baseDir, int port) {
        this(baseDir, null, port);
    }

    public JettyServer(String baseDir, String host, int port) {
        System.out.println(new File(baseDir).getAbsolutePath());
        if (host == null)
            server = new Server(port);
        else
            server = new Server(new InetSocketAddress(host, port));

        server.setSessionIdManager(new HashSessionIdManager());

        // Sessions are bound to a context.
        ContextHandler context = new ContextHandler("/");
        server.setHandler(context);

        // Create the SessionHandler (wrapper) to handle the sessions
        HashSessionManager manager = new HashSessionManager();
        SessionHandler sessions = new SessionHandler(manager);
        context.setHandler(sessions);

        sessions.setHandler(new Portal(baseDir));
    }

    public void start() throws Exception {
        server.start();
    }

    public void stop() throws Exception {
        server.stop();
        server.join();
    }

    public void setDebugNoAccessControl(boolean noAccessControl) {
        DebugSingleton.get().setNoAccessControl(noAccessControl);
    }
}

