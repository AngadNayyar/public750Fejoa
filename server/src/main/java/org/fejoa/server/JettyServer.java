/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.server;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.session.HashSessionIdManager;
import org.eclipse.jetty.server.session.HashSessionManager;
import org.eclipse.jetty.server.session.SessionHandler;


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
    final Server server;

    public static void main(String[] args) throws Exception {
        JettyServer server = new JettyServer("");
        server.start();
    }

    public JettyServer(String baseDir) {
        this(baseDir, 8080);
    }

    public JettyServer(String baseDir, int port) {
        server = new Server(port);

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

