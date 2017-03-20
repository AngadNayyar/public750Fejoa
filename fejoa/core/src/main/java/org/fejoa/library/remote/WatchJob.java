/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.remote;

import org.fejoa.chunkstore.Config;
import org.fejoa.chunkstore.HashValue;
import org.fejoa.library.Constants;
import org.fejoa.library.FejoaContext;
import org.fejoa.library.BranchInfo;
import org.fejoa.library.Remote;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;


public class WatchJob extends SimpleJsonRemoteJob<WatchJob.Result> {
    static final public String METHOD = "watch";
    static final public String BRANCHES_KEY = "branches";
    static final public String BRANCH_KEY = "branch";
    static final public String BRANCH_TIP_KEY = "tip";
    static final public String STATUS_KEY = "tip";
    static final public String STATUS_ACCESS_DENIED = "denied";
    static final public String STATUS_UPDATE = "update";
    static final public String BRANCH_LOG_TIP = "logTip";
    static final public String BRANCH_LOG_MESSAGE = "logMessage";
    static final public String WATCH_RESULT_KEY = "watchResults";
    static final public String PEEK_KEY = "peek";

    public static class BranchLogTip {
        final public String branch;
        final public HashValue logTip;
        final public String logMessage;

        public BranchLogTip(String branch, HashValue logTip, String logMessage) {
            this.branch = branch;
            this.logTip = logTip;
            this.logMessage = logMessage;
        }
    }

    public static class Result extends RemoteJob.Result {
        final public List<BranchLogTip> updated;
        public Result(int status, String message, List<BranchLogTip> updated) {
            super(status, message);

            this.updated = updated;
        }
    }

    final private FejoaContext context;
    final private Collection<BranchInfo.Location> branchInfoList;
    final private boolean peek;

    public WatchJob(FejoaContext context, Collection<BranchInfo.Location> branchInfoList) {
        this(context, branchInfoList, false);
    }

    public WatchJob(FejoaContext context, Collection<BranchInfo.Location> branchInfoList, boolean peek) {
        super(false);

        this.context = context;
        this.branchInfoList = branchInfoList;
        this.peek = peek;
    }

    @Override
    public String getJsonHeader(JsonRPC jsonRPC) throws IOException {
        List<JsonRPC.ArgumentSet> branches = new ArrayList<>();
        for (BranchInfo.Location branchInfo : branchInfoList) {
            Remote remote = branchInfo.getRemote();
            String branch = branchInfo.getBranchInfo().getBranch();
            HashValue tip = context.getStorageLogTip(branch);
            JsonRPC.ArgumentSet argumentSet = new JsonRPC.ArgumentSet(
                    new JsonRPC.Argument(Constants.SERVER_USER_KEY, remote.getUser()),
                    new JsonRPC.Argument(BRANCH_KEY, branch),
                    new JsonRPC.Argument(BRANCH_TIP_KEY, tip.toHex())
            );
            branches.add(argumentSet);
        }

        return jsonRPC.call(METHOD, new JsonRPC.Argument(PEEK_KEY, peek), new JsonRPC.Argument(BRANCHES_KEY, branches));
    }

    @Override
    protected Result handleJson(JSONObject returnValue, InputStream binaryData) {
        int status;
        String message;
        try {
            status = returnValue.getInt("status");
            message = returnValue.getString("message");
        } catch (Exception e) {
            status = Errors.EXCEPTION;
            e.printStackTrace();
            message = e.getMessage();
        }
        if (status != Errors.DONE)
            return new WatchJob.Result(status, message, null);

        List<BranchLogTip> updates = new ArrayList<>();
        try {
            JSONArray statusArray = returnValue.getJSONArray(WATCH_RESULT_KEY);
            for (int i = 0; i < statusArray.length(); i++) {
                JSONObject statusObject = statusArray.getJSONObject(i);
                String branch = statusObject.getString(BRANCH_KEY);
                HashValue hashValue = Config.newBoxHash();
                if (statusObject.has(BRANCH_LOG_TIP))
                    hashValue = HashValue.fromHex(statusObject.getString(BRANCH_LOG_TIP));
                String logMessage = "";
                if (statusObject.has(BRANCH_LOG_MESSAGE))
                    logMessage = statusObject.getString(BRANCH_LOG_MESSAGE);
                updates.add(new BranchLogTip(branch, hashValue, logMessage));
            }
        } catch (JSONException e) {
            return new WatchJob.Result(Errors.EXCEPTION, e.getMessage(), null);
        }

        return new WatchJob.Result(status, message, updates);
    }
}
