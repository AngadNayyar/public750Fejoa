/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.remote;

import org.fejoa.chunkstore.Repository;
import org.fejoa.chunkstore.sync.PushRequest;
import org.fejoa.chunkstore.sync.Request;
import org.fejoa.library.Constants;


public class ChunkStorePushJob extends JsonRemoteJob<ChunkStorePushJob.Result> {
    public static class Result extends RemoteJob.Result {
        final public boolean pullRequired;

        public Result(int status, String message) {
            super(status, message);

            this.pullRequired = false;
        }

        public Result(int status, String message, boolean pullRequired) {
            super(status, message);

            this.pullRequired = pullRequired;
        }
    }

    final private Repository repository;
    final private String serverUser;
    final private String branch;

    public ChunkStorePushJob(Repository repository, String serverUser, String branch) {
        this.repository = repository;
        this.serverUser = serverUser;
        this.branch = branch;
    }

    @Override
    public Result run(IRemoteRequest remoteRequest) throws Exception {
        super.run(remoteRequest);

        if (repository.getHeadCommit() == null)
            return new Result(Errors.DONE, "ok", true);

        JsonRPC.Argument serverUserArg = new JsonRPC.Argument(Constants.SERVER_USER_KEY, serverUser);
        JsonRPC.Argument branchArg = new JsonRPC.Argument(Constants.BRANCH_KEY, branch);

        PushRequest pushRequest = new PushRequest(repository);
        String header = jsonRPC.call(Request.CS_REQUEST_METHOD, serverUserArg, branchArg);

        RemotePipe pipe = new RemotePipe(header, remoteRequest, null);
        int result = pushRequest.push(pipe, repository.getCurrentTransaction(), branch);

        return new Result(Errors.DONE, "ok", result == Request.PULL_REQUIRED);
    }
}
