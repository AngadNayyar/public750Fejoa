/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.filestorage;

import java8.util.concurrent.CompletableFuture;
import java8.util.concurrent.CompletionStage;
import java8.util.function.BiFunction;
import java8.util.function.Function;
import org.fejoa.library.*;
import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.database.DBObjectList;
import org.fejoa.library.database.DBString;
import org.fejoa.library.database.DBObjectContainer;
import org.fejoa.library.database.IDBContainerEntry;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.*;


/**
 * List of ContactStorage s
 *
 * ContactStorage: list of Stores associated with a contact
 *
 * Store: contains a Json string that encodes a CheckoutProfileList. Each CheckoutProfile contains a list of
 * CheckoutEntry s.
 */
public class ContactStorageList extends DBObjectList<ContactStorageList.ContactStorage> {
    static public class Store extends DBObjectContainer {
        final private String branch;
        final private ContactStorage contactStorage;
        final private DBString checkOuts = new DBString("checkOuts");

        public Store(String branch, ContactStorage contactStorage) {
            this.branch = branch;
            this.contactStorage = contactStorage;

            add(checkOuts);
        }

        public ContactStorage getContactStorage() {
            return contactStorage;
        }

        public CompletableFuture<CheckoutProfiles> getCheckOutProfiles() {
            return checkOuts.get().handle(new BiFunction<String, Throwable, CompletableFuture<CheckoutProfiles>>() {
                @Override
                public CompletableFuture<CheckoutProfiles> apply(String s, Throwable throwable) {
                    if (throwable != null)
                        return CompletableFuture.completedFuture(new CheckoutProfiles());

                    try {
                        return CompletableFuture.completedFuture(new CheckoutProfiles(s));
                    } catch (JSONException e) {
                        return CompletableFuture.failedFuture(e);
                    }
                }
            }).thenCompose(new Function<CompletableFuture<CheckoutProfiles>, CompletionStage<CheckoutProfiles>>() {
                @Override
                public CompletionStage<CheckoutProfiles> apply(
                        CompletableFuture<CheckoutProfiles> checkOutProfilesCompletableFuture) {
                    return checkOutProfilesCompletableFuture;
                }
            });
        }

        public void setCheckOutProfile(CheckoutProfiles checkoutProfiles) throws JSONException {
            checkOuts.set(checkoutProfiles.toJson());
        }

        public String getBranch() {
            return branch;
        }

        public BranchInfo getBranchInfo() throws IOException, CryptoException {
            return contactStorage.getContactPublic().getBranchList().get(branch, FileStorageManager.STORAGE_CONTEXT);
        }
    }

    static public class CheckoutEntry {
        private String checkoutPath = "";
        private List<String> remoteIds = new ArrayList<>();

        public CheckoutEntry() {

        }

        public CheckoutEntry(String jsonString) throws JSONException {
            fromJson(jsonString);
        }

        public String getCheckoutPath() {
            return checkoutPath;
        }

        public void setCheckoutPath(String checkoutPath) {
            this.checkoutPath = checkoutPath;
        }

        public List<String> getRemoteIds() {
            return remoteIds;
        }

        public String toJson() throws JSONException {
            JSONObject object = new JSONObject();
            object.put("path", checkoutPath);
            object.put("remotes", remoteIds);
            return object.toString();
        }

        private void fromJson(String jsonString) throws JSONException {
            JSONObject object = new JSONObject(jsonString);
            checkoutPath = object.getString("path");
            remoteIds.clear();
            JSONArray array = object.getJSONArray("remotes");
            for (int i = 0; i < array.length(); i++)
                remoteIds.add(array.getString(i));
        }
    }

    static public class CheckoutProfile {
        private List<CheckoutEntry> checkoutEntries = new ArrayList<>();

        public CheckoutProfile() {

        }

        public CheckoutProfile(String jsonString) throws JSONException {
            fromJson(jsonString);
        }

        public List<CheckoutEntry> getCheckoutEntries() {
            return checkoutEntries;
        }

        public String toJson() throws JSONException {
            JSONObject object = new JSONObject();
            JSONArray list = new JSONArray();
            for (CheckoutEntry entry : checkoutEntries)
                list.put(entry.toJson());
            object.put("entries", list);
            return object.toString();
        }

        private void fromJson(String jsonString) throws JSONException {
            checkoutEntries.clear();
            JSONArray array = new JSONObject(jsonString).getJSONArray("entries");
            for (int i = 0; i < array.length(); i++)
                checkoutEntries.add(new CheckoutEntry(array.getString(i)));
        }
    }

    static public class CheckoutProfiles {
        final private Map<String, CheckoutProfile> profileMap = new HashMap<>();

        public CheckoutProfiles() {

        }

        public CheckoutProfiles(String jsonString) throws JSONException {
            fromJson(jsonString);
        }

        public CheckoutProfile getCheckout(String profile) {
            return profileMap.get(profile);
        }

        public CheckoutProfile ensureCheckout(String profile) {
            CheckoutProfile checkOut = getCheckout(profile);
            if (checkOut != null)
                return checkOut;
            checkOut = new CheckoutProfile();
            profileMap.put(profile, checkOut);
            return checkOut;
        }

        public String toJson() throws JSONException {
            JSONObject object = new JSONObject();
            for (Map.Entry<String, CheckoutProfile> entry : profileMap.entrySet())
                object.put(entry.getKey(), entry.getValue().toJson());
            return object.toString();
        }

        private void fromJson(String jsonString) throws JSONException {
            profileMap.clear();

            JSONObject jsonObject = new JSONObject(jsonString);
            Iterator<String> it = jsonObject.keys();
            while (it.hasNext()) {
                String key = it.next();
                String checkOutString = jsonObject.getString(key);
                profileMap.put(key, new CheckoutProfile(checkOutString));
            }
        }
    }

    static public class ContactStorage extends DBObjectContainer {
        final private DBObjectList<Store> stores;

        final private String contactId;
        final private UserData userData;
        final private IContactPublic contactPublic;

        public ContactStorage(String contactId, UserData userData) {
            this.contactId = contactId;
            this.userData = userData;
            this.contactPublic = find(contactId);

            stores = new DBObjectList<>(true, new IValueCreator() {
                @Override
                public Store create(String entryName) {
                    return new Store(entryName, ContactStorage.this);
                }
            });

            add(stores, "stores");
        }

        public DBObjectList<Store> getStores() {
            return stores;
        }

        public String getContactId() {
            return contactId;
        }

        public IContactPublic getContactPublic() {
            return contactPublic;
        }

        private IContactPublic find(String contactId) {
            if (userData.getMyself().getId().equals(contactId))
                return userData.getMyself();
            for (ContactPublic contactPublic : userData.getContactStore().getContactList().getEntries()) {
                if (contactPublic.getId().equals(contactId))
                    return contactPublic;
            }
            return null;
        }
    }

    public ContactStorageList(final UserData userData) {
        super(true, new IValueCreator() {
            @Override
            public IDBContainerEntry create(String contactId) {
                return new ContactStorage(contactId, userData);
            }
        });
    }
}
