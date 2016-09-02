/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.remote;

import org.fejoa.chunkstore.BoxPointer;
import org.fejoa.chunkstore.Repository;
import org.fejoa.chunkstore.sync.PullRequest;
import org.fejoa.library.database.ICommitSignature;
import org.fejoa.server.Portal;


public class ChunkStorePullJob extends JsonRemoteJob<ChunkStorePullJob.Result> {
    public static class Result extends RemoteJob.Result {
        final public BoxPointer pulledRev;

        public Result(int status, String message, BoxPointer pulledRev) {
            super(status, message);

            this.pulledRev = pulledRev;
        }
    }

    static final public String METHOD = "csRequest";

    final private Repository repository;
    final private ICommitSignature commitSignature;
    final private String serverUser;
    final private String branch;

    public ChunkStorePullJob(Repository repository, ICommitSignature commitSignature, String serverUser,
                             String branch) {
        this.repository = repository;
        this.commitSignature = commitSignature;
        this.serverUser = serverUser;
        this.branch = branch;
    }

    @Override
    public ChunkStorePullJob.Result run(IRemoteRequest remoteRequest) throws Exception {
        super.run(remoteRequest);

        JsonRPC.Argument serverUserArg = new JsonRPC.Argument(org.fejoa.library.Constants.SERVER_USER_KEY, serverUser);
        JsonRPC.Argument branchArg = new JsonRPC.Argument(org.fejoa.library.Constants.BRANCH_KEY, branch);

        PullRequest pullRequest = new PullRequest(repository, commitSignature);
        String header = jsonRPC.call(ChunkStorePullJob.METHOD, serverUserArg, branchArg);

        RemotePipe pipe = new RemotePipe(header, remoteRequest, null);
        BoxPointer remoteTip = pullRequest.pull(pipe, branch);

        return new Result(Portal.Errors.DONE, "ok", remoteTip);
    }
}

