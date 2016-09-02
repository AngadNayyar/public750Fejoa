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
import org.fejoa.server.Portal;


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

    static final public String METHOD = "csRequest";

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
            return new Result(Portal.Errors.DONE, "ok", true);

        JsonRPC.Argument serverUserArg = new JsonRPC.Argument(org.fejoa.library.Constants.SERVER_USER_KEY, serverUser);
        JsonRPC.Argument branchArg = new JsonRPC.Argument(org.fejoa.library.Constants.BRANCH_KEY, branch);

        PushRequest pushRequest = new PushRequest(repository);
        String header = jsonRPC.call(ChunkStorePushJob.METHOD, serverUserArg, branchArg);

        RemotePipe pipe = new RemotePipe(header, remoteRequest, null);
        PushRequest.Result result = pushRequest.push(pipe, repository.getCurrentTransaction(), branch);

        return new Result(Portal.Errors.DONE, "ok", result == PushRequest.Result.PULL_REQUIRED);
    }
}
