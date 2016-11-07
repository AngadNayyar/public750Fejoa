/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.server;

import org.fejoa.chunkstore.*;
import org.fejoa.chunkstore.sync.Request;
import org.fejoa.chunkstore.sync.RequestHandler;
import org.fejoa.library.BranchAccessRight;
import org.fejoa.library.remote.Errors;
import org.fejoa.library.remote.JsonRPCHandler;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;


public class ChunkStoreRequestHandler extends JsonRequestHandler {
    public ChunkStoreRequestHandler() {
        super(Request.CS_REQUEST_METHOD);
    }

    @Override
    public void handle(Portal.ResponseHandler responseHandler, JsonRPCHandler jsonRPCHandler, InputStream data,
                       Session session) throws Exception {
        JSONObject params = jsonRPCHandler.getParams();
        String user = params.getString("serverUser");
        final String branch = params.getString("branch");
        AccessControl accessControl = new AccessControl(session, user);
        int branchAccessRights = accessControl.getBranchAccessRights(branch);
        ChunkStore chunkStore = accessControl.getChunkStore(branch, branchAccessRights);
        if (chunkStore == null) {
            responseHandler.setResponseHeader(jsonRPCHandler.makeResult(Errors.ACCESS_DENIED,
                    "branch access denied"));
            return;
        }
        final ChunkStoreBranchLog branchLog = accessControl.getChunkStoreBranchLog(branch, BranchAccessRight.PUSH);

        ChunkStore.Transaction transaction = chunkStore.openTransaction();
        final RequestHandler handler = new RequestHandler(transaction,
                new RequestHandler.IBranchLogGetter() {
                    @Override
                    public ChunkStoreBranchLog get(String b) throws IOException {
                        if (!branch.equals(b))
                            throw new IOException("Branch miss match.");
                        return branchLog;
                    }
                });

        ServerPipe pipe = new ServerPipe(jsonRPCHandler.makeResult(Errors.OK, "data pipe ok"),
                responseHandler, data);
        RequestHandler.Result result = handler.handle(pipe, branchAccessRights);
        if (result != RequestHandler.Result.OK && !responseHandler.isHandled())
            responseHandler.setResponseHeader(jsonRPCHandler.makeResult(Errors.ERROR, result.getDescription()));
    }
}
