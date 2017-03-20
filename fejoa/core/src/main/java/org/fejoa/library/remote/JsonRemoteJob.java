/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.remote;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.logging.Logger;


public class JsonRemoteJob<T extends RemoteJob.Result> extends RemoteJob<T> {
    final static private Logger LOG = Logger.getLogger(JsonRemoteJob.class.getName());

    final static public String ACCESS_DENIED_KEY = "access_denied";

    protected JsonRPC jsonRPC;
    private JsonRemoteJob<T> followUpJob;

    protected JsonRPC startNewJsonRPC() {
        this.jsonRPC = new JsonRPC();
        return jsonRPC;
    }

    protected JSONObject getReturnValue(String message) throws IOException, JSONException {
        return jsonRPC.getReturnValue(message);
    }

    protected Result getResult(JSONObject returnValue) {
        int status = Errors.ERROR;
        String message;
        try {
            status = returnValue.getInt("status");
            message = returnValue.getString("message");
        } catch (Exception e) {
            e.printStackTrace();
            message = e.getMessage();
        }
        // don't allow the server to set follow up jobs
        if (status == Errors.FOLLOW_UP_JOB)
            status = Errors.ERROR;
        return new Result(status, message);
    }

    protected void setFollowUpJob(JsonRemoteJob<T> followUpJob) {
        this.followUpJob = followUpJob;
    }

    public JsonRemoteJob<T> getFollowUpJob() {
        return followUpJob;
    }

    @Override
    public T run(IRemoteRequest remoteRequest) throws Exception {
        super.run(remoteRequest);
        startNewJsonRPC();
        return null;
    }

    static public <T extends Result> T run(JsonRemoteJob<T> job, IRemoteRequest remoteRequest)
            throws Exception {
        T result = job.run(remoteRequest);
        if (result.status == Errors.FOLLOW_UP_JOB) {
            LOG.info("Start follow up job (" + job.getFollowUpJob().getClass().getSimpleName() + ") after: "
                    + result.message);
            return run(job.getFollowUpJob(), remoteRequest);
        }
        return result;
    }
}
