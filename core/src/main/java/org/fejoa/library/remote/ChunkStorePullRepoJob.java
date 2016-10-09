/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.remote;

import org.fejoa.chunkstore.Repository;
import org.fejoa.chunkstore.sync.PullRepoRequest;
import org.fejoa.library.Constants;


public class ChunkStorePullRepoJob extends JsonRemoteJob<RemoteJob.Result> {
    final private Repository repository;
    final private String serverUser;
    final private String branch;

    public ChunkStorePullRepoJob(Repository repository, String serverUser, String branch) {
        this.repository = repository;
        this.serverUser = serverUser;
        this.branch = branch;
    }

    @Override
    public RemoteJob.Result run(IRemoteRequest remoteRequest) throws Exception {
        super.run(remoteRequest);


        JsonRPC.Argument serverUserArg = new JsonRPC.Argument(Constants.SERVER_USER_KEY, serverUser);
        JsonRPC.Argument branchArg = new JsonRPC.Argument(Constants.BRANCH_KEY, branch);

        PullRepoRequest pullRequest = new PullRepoRequest(repository);
        String header = jsonRPC.call(ChunkStorePullJob.METHOD, serverUserArg, branchArg);

        RemotePipe pipe = new RemotePipe(header, remoteRequest, null);
        pullRequest.pull(pipe, branch);

        return new RemoteJob.Result(Errors.DONE, "ok");
    }
}
