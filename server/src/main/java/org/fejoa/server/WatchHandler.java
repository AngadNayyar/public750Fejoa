/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.server;


import org.fejoa.chunkstore.ChunkStoreBranchLog;
import org.fejoa.chunkstore.Config;
import org.fejoa.chunkstore.HashValue;
import org.fejoa.library.BranchAccessRight;
import org.fejoa.library.database.JGitInterface;
import org.fejoa.library.Constants;
import org.fejoa.library.remote.*;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WatchHandler extends JsonRequestHandler {
    public WatchHandler() {
        super(WatchJob.METHOD);
    }

    public enum Status {
        UPDATE,
        ACCESS_DENIED
    }

    static private class WatchEntry {
        final public String user;
        final public String branch;
        final public String branchTip;

        public WatchEntry(String user, String branch, String branchTip) {
            this.user = user;
            this.branch = branch;
            this.branchTip = branchTip;
        }
    }

    @Override
    public void handle(Portal.ResponseHandler responseHandler, JsonRPCHandler jsonRPCHandler, InputStream data,
                       Session session) throws Exception {
        JSONObject params = jsonRPCHandler.getParams();
        Boolean peek = false;
        if (params.has(WatchJob.PEEK_KEY))
            peek = params.getBoolean(WatchJob.PEEK_KEY);
        JSONArray branches = params.getJSONArray(WatchJob.BRANCHES_KEY);

        List<WatchEntry> branchList = new ArrayList<>();
        for (int i = 0; i < branches.length(); i++) {
            JSONObject branch = branches.getJSONObject(i);
            branchList.add(new WatchEntry(branch.getString(Constants.SERVER_USER_KEY),
                    branch.getString(WatchJob.BRANCH_KEY), branch.getString(WatchJob.BRANCH_TIP_KEY)));
        }

        Map<String, Status> statusMap = watch(session, peek, branchList);

        if (statusMap.isEmpty() && !peek) {
            // timeout
            String response = jsonRPCHandler.makeResult(Errors.OK, "timeout");
            responseHandler.setResponseHeader(response);
            return;
        }

        List<JsonRPC.ArgumentSet> deniedReturn = new ArrayList<>();
        List<JsonRPC.ArgumentSet> statusReturn = new ArrayList<>();
        for (Map.Entry<String, Status> entry : statusMap.entrySet()) {
            if (entry.getValue() == Status.ACCESS_DENIED) {
                JsonRPC.ArgumentSet argumentSet = new JsonRPC.ArgumentSet(
                        new JsonRPC.Argument(WatchJob.BRANCH_KEY, entry.getKey()),
                        new JsonRPC.Argument(WatchJob.STATUS_KEY, WatchJob.STATUS_ACCESS_DENIED)
                );
                deniedReturn.add(argumentSet);
            } else if (entry.getValue() == Status.UPDATE) {
                JsonRPC.ArgumentSet argumentSet = new JsonRPC.ArgumentSet(
                        new JsonRPC.Argument(WatchJob.BRANCH_KEY, entry.getKey()),
                        new JsonRPC.Argument(WatchJob.STATUS_KEY, WatchJob.STATUS_UPDATE)
                );
                statusReturn.add(argumentSet);
            }
        }
        if (deniedReturn.size() != 0) {
            responseHandler.setResponseHeader(jsonRPCHandler.makeResult(Errors.ACCESS_DENIED, "watch results"));
            return;
        }
        String response = jsonRPCHandler.makeResult(Errors.OK, "watch results",
                new JsonRPC.Argument(WatchJob.WATCH_RESULT_KEY, statusReturn),
                new JsonRPC.Argument(JsonRemoteJob.ACCESS_DENIED_KEY, deniedReturn));
        responseHandler.setResponseHeader(response);
    }

    private Map<String, Status> watch(Session session, boolean peek, List<WatchEntry> branches) {
        Map<String, Status> status = new HashMap<>();

        //TODO: use a file monitor instead of polling
        final long TIME_OUT = 60 * 1000;
        long time = System.currentTimeMillis();
        while (status.isEmpty()) {
            for (WatchEntry entry : branches) {
                AccessControl accessControl = new AccessControl(session, entry.user);
                String branch = entry.branch;
                String remoteMessageHashString = entry.branchTip;
                HashValue remoteMessageHash = Config.newBoxHash();
                if (!remoteMessageHashString.equals(""))
                    remoteMessageHash = HashValue.fromHex(remoteMessageHashString);
                ChunkStoreBranchLog branchLog;
                try {
                    branchLog = accessControl.getChunkStoreBranchLog(branch, BranchAccessRight.PULL);
                } catch (IOException e) {
                    continue;
                }
                if (branchLog == null) {
                    status.put(branch, Status.ACCESS_DENIED);
                    continue;
                }
                ChunkStoreBranchLog.Entry latest = branchLog.getLatest();
                HashValue localMessageHash = Config.newBoxHash();
                if (latest != null)
                    localMessageHash = latest.getEntryId();
                if (!remoteMessageHash.equals(localMessageHash))
                    status.put(branch, Status.UPDATE);
            }
            if (System.currentTimeMillis() - time > TIME_OUT)
                break;
            if (peek)
                break;
            if (status.isEmpty()) {
                try {
                    Thread.sleep(500L);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
        return status;
    }
}
