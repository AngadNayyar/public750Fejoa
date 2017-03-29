/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library;

import org.json.JSONException;
import org.json.JSONObject;


public class UserDataSettings {
    final public KeyStore.Settings keyStoreSettings;
    final public String accessStore;
    final public String inQueue;
    final public String outQueue;

    final static private String KEY_STORE_CONFIG_KEY = "keyStoreConfig";
    final static private String ACCESS_STORE_KEY = "accessStore";
    final static private String IN_QUEUE_KEY = "inQueue";
    final static private String OUT_QUEUE_KEY = "outQueue";

    public UserDataSettings(KeyStore.Settings keyStoreSettings, String accessStore, String inQueue, String outQueue) {
        this.keyStoreSettings = keyStoreSettings;
        this.accessStore = accessStore;
        this.inQueue = inQueue;
        this.outQueue = outQueue;
    }

    public UserDataSettings(JSONObject config) throws JSONException {
        this.keyStoreSettings = new KeyStore.Settings(config.getJSONObject(KEY_STORE_CONFIG_KEY));
        this.accessStore = config.getString(ACCESS_STORE_KEY);
        this.inQueue = config.getString(IN_QUEUE_KEY);
        this.outQueue = config.getString(OUT_QUEUE_KEY);
    }

    public JSONObject toJson() {
        JSONObject config = new JSONObject();
        try {
            config.put(KEY_STORE_CONFIG_KEY, keyStoreSettings.toJson());
            config.put(ACCESS_STORE_KEY, accessStore);
            config.put(IN_QUEUE_KEY, inQueue);
            config.put(OUT_QUEUE_KEY, outQueue);
        } catch (JSONException e) {
            e.printStackTrace();
            throw new RuntimeException("Unexpected");
        }
        return config;
    }

}
