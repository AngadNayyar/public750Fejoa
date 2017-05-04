/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.gui.javafx;

import javafx.scene.image.*;


public class Resources {
    final static public String ICON_CONTACT_32 = "icon_contact_32.png";
    final static public String ICON_CONTACT_REQUEST_32 =  "icon_contact_request_32.png";
    final static public String ICON_REQUESTED_32 = "icon_contact_requested_32.png";
    final static public String ICON_FOLDER_32 = "icon_folder_32.png";
    final static public String ICON_REMOTE_32 = "icon_remote_32.png";
    final static public String ICON_NEW_USER = "newuser.png";
    final static public String ICON_ADD_CONTACT = "addcontact.png";
    final static public String ICON_SEND_MESSAGE = "send-message.png";
    final static public String ICON_ATTACHMENT = "attachment.png";
    final static public String ICON_LOGO = "logo.png";
    final static public String ICON_CLOUD = "cloud.png";

    static private Resources singleton;

    static private Resources get() {
        if (singleton != null)
            return singleton;
        singleton = new Resources();
        return singleton;
    }

    private Resources() {

    }

    static public Image getIcon(String icon) {
        return new Image(get().getClass().getClassLoader().getResourceAsStream(icon));
    }
}
