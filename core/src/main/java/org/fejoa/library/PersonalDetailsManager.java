/*
 * Copyright 2017.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library;

import java8.util.concurrent.CompletableFuture;
import java8.util.concurrent.CompletionStage;
import java8.util.function.BiConsumer;
import java8.util.function.Consumer;
import java8.util.function.Function;
import org.fejoa.library.command.AccessCommandHandler;
import org.fejoa.library.database.*;

import java.io.IOException;


/**
 * Manage a list of personal detail entries.
 *
 * Each entry is a separate branch so that it can selectively be shared with different contacts.
 */
public class PersonalDetailsManager extends DBObjectContainer {
    final static public String PERSONAL_DETAILS_CONTEXT = "org.fejoa.personaldetails";
    final static public String DEFAULT_ENTRY = "default";

    final private UserData userData;
    final private Contact contact;
    final private DBObjectList<PersonalDetailsManager.Entry> entryList;

    public class Entry extends DBObjectContainer {
        final private String identifier;
        final private DBString branch = new DBString(Constants.BRANCH_KEY);

        public Entry(String identifier) {
            this.identifier = identifier;

            add(branch);
        }

        public CompletableFuture<PersonalDetailsBranch> getDetailsBranch() {
            return this.branch.get().thenComposeAsync(new Function<String, CompletionStage<PersonalDetailsBranch>>() {
                @Override
                public CompletionStage<PersonalDetailsBranch> apply(String branch) {
                    try {

                        BranchInfo branchInfo = contact.getBranchList().get(branch, PERSONAL_DETAILS_CONTEXT);
                        StorageDir detailsBranch = userData.getStorageDir(branchInfo);
                        return CompletableFuture.completedStage(new PersonalDetailsBranch(detailsBranch));
                    } catch (Exception e) {
                        return CompletableFuture.failedStage(e);
                    }
                }
            }, userData.getContext().getContextExecutor());
        }

        public CompletableFuture<PersonalDetailsBranch> getOrCreate() {
            final CompletableFuture<PersonalDetailsBranch> result = new CompletableFuture<>();
            CompletableFuture<PersonalDetailsBranch> entry = getDetailsBranch();
            entry.whenCompleteAsync(new BiConsumer<PersonalDetailsBranch, Throwable>() {
                @Override
                public void accept(PersonalDetailsBranch detailsBranch, Throwable throwable) {
                    if (throwable == null) {
                        result.complete(detailsBranch);
                        return;
                    }
                    try {
                        FejoaContext context = userData.getContext();
                        BranchInfo branchInfo = userData.createNewEncryptedStorage(PERSONAL_DETAILS_CONTEXT,
                                "Personal Details (" + identifier + ")");
                        Remote remote = userData.getGateway();
                        branchInfo.addLocation(remote.getId(), context.getRootAuthInfo(remote));

                        final StorageDir detailsBranchStorage = userData.getStorageDir(branchInfo);
                        branch.set(branchInfo.getBranch());
                        entryList.flush().thenAcceptAsync(new Consumer<Void>() {
                            @Override
                            public void accept(Void aVoid) {
                                try {
                                    userData.commit();
                                    userData.getKeyStore().commit();
                                    result.complete(new PersonalDetailsBranch(detailsBranchStorage));
                                } catch (IOException e) {
                                    result.completeExceptionally(e);
                                }
                            }
                        }, context.getContextExecutor());
                    } catch (Exception e) {
                        result.completeExceptionally(e);
                    }

                }
            }, userData.getContext().getContextExecutor());
            return result;
        }
    }


    PersonalDetailsManager(final Client client, Contact contact) {
        entryList = new DBObjectList<>(true, new DBObjectList.IValueCreator() {
            @Override
            public Entry create(String entryName) {
                return new Entry(entryName);
            }
        });
        add(entryList);

        this.userData = client.getUserData();
        this.contact = contact;

        AppContext appContext = userData.getConfigStore().getAppContext(PERSONAL_DETAILS_CONTEXT);
        appContext.addAccessGrantedHandler(client.getIncomingCommandManager(), new AccessCommandHandler.IContextHandler() {
            @Override
            public boolean handle(String senderId, BranchInfo branchInfo) throws Exception {
                Contact sender = userData.getContact(senderId);
                if (sender == null)
                    return false;
                PersonalDetailsManager senderDetails = sender.getPersonalDetails(client);
                Entry entry = senderDetails.addEntry(DEFAULT_ENTRY, branchInfo.getBranch());
                entry.flush();
                return true;
            }
        });
    }

    public Entry get(String identifier) {
        return entryList.get(identifier);
    }

    public Entry addEntry(String identifier, String branch) {
        Entry entry = get(identifier);
        entry.branch.set(branch);
        return entry;
    }
}

