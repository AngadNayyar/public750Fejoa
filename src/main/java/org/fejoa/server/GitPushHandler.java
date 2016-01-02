/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.server;

import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.PacketLineOut;
import org.eclipse.jgit.transport.ReceivePack;
import org.eclipse.jgit.transport.RefAdvertiser;
import org.fejoa.library.database.JGitInterface;
import org.fejoa.library2.remote.GitPushJob;
import org.fejoa.library2.remote.JsonRPCHandler;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;


public class GitPushHandler extends JsonRequestHandler {
    public GitPushHandler() {
        super(GitPushJob.METHOD);
    }

    @Override
    public void handle(Portal.ResponseHandler responseHandler, JsonRPCHandler jsonRPCHandler, InputStream data,
                       Session session) throws Exception {
        JSONObject params = jsonRPCHandler.getParams();
        String request = params.getString("request");
        String user = params.getString("serverUser");
        String branch = params.getString("branch");
        JGitInterface gitInterface = AccessControl.getDatabase(session, user, branch);
        Repository repository = gitInterface.getRepository();
        boolean allowNonFastForwards = false;

        if (request.equals(GitPushJob.METHOD_REQUEST_ADVERTISEMENT)) {
            responseHandler.setResponseHeader(jsonRPCHandler.makeResult(Portal.Errors.OK, "advertisement attached"));

            ReceivePack receivePack = new ReceivePack(repository);
            receivePack.setAllowNonFastForwards(allowNonFastForwards);
            OutputStream rawOut = responseHandler.addData();
            PacketLineOut pckOut = new PacketLineOut(rawOut);
            pckOut.setFlushOnEnd(false);
            receivePack.sendAdvertisedRefs(new RefAdvertiser.PacketLineOutRefAdvertiser(pckOut));
        } else if (request.equals(GitPushJob.METHOD_REQUEST_PUSH_DATA)) {
            ServerPipe pipe = new ServerPipe(jsonRPCHandler.makeResult(Portal.Errors.OK, "data pipe ok"),
                    responseHandler, data);

            ReceivePack receivePack = new ReceivePack(repository);
            receivePack.setAllowNonFastForwards(allowNonFastForwards);
            receivePack.setBiDirectionalPipe(false);
            OutputStream rawOut = responseHandler.addData();
            PacketLineOut pckOut = new PacketLineOut(rawOut);
            pckOut.setFlushOnEnd(false);
            receivePack.receive(pipe.getInputStream(), pipe.getOutputStream(), null);
        } else
            throw new Exception("Invalid push request: " + request);
    }
}
