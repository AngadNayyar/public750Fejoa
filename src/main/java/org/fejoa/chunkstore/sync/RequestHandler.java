/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.chunkstore.sync;

import org.fejoa.chunkstore.ChunkStore;
import org.fejoa.chunkstore.ChunkStoreBranchLog;
import org.fejoa.library.BranchAccessRight;
import org.fejoa.library.remote.IRemotePipe;
import org.fejoa.library.support.StreamHelper;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;

import static org.fejoa.chunkstore.sync.Request.*;
import static org.fejoa.library.support.StreamHelper.readString;


public class RequestHandler {
    public enum Result {
        OK,
        MISSING_ACCESS_RIGHTS,
        ERROR
    }

    public interface IBranchLogGetter {
        ChunkStoreBranchLog get(String branch) throws IOException;
    }

    final private ChunkStore.Transaction chunkStore;
    final private IBranchLogGetter logGetter;

    public RequestHandler(ChunkStore.Transaction chunkStore, IBranchLogGetter logGetter) {
        this.chunkStore = chunkStore;
        this.logGetter = logGetter;
    }

    private boolean checkAccessRights(int request, int accessRights) {
        switch (request) {
            case GET_REMOTE_TIP:
            case GET_CHUNKS:
                if ((accessRights & BranchAccessRight.PULL) == 0)
                    return false;
                break;
            case PUT_CHUNKS:
            case HAS_CHUNKS:
                if ((accessRights & BranchAccessRight.PUSH) == 0)
                    return false;
                break;
            case GET_ALL_CHUNKS:
                if ((accessRights & BranchAccessRight.PULL_CHUNK_STORE) == 0)
                    return false;
                break;
        }
        return true;
    }

    public Result handle(IRemotePipe pipe, int accessRights) {
        try {
            DataInputStream inputStream = new DataInputStream(pipe.getInputStream());
            int request = Request.receiveRequest(inputStream);
            if (!checkAccessRights(request, accessRights))
                return Result.MISSING_ACCESS_RIGHTS;
            switch (request) {
                case GET_REMOTE_TIP:
                    handleGetRemoteTip(pipe, inputStream);
                    break;
                case GET_CHUNKS:
                    PullHandler.handleGetChunks(chunkStore, pipe, inputStream);
                    break;
                case PUT_CHUNKS:
                    PushHandler.handlePutChunks(chunkStore, logGetter, pipe, inputStream);
                    break;
                case HAS_CHUNKS:
                    HasChunksHandler.handleHasChunks(chunkStore, pipe, inputStream);
                    break;
                case GET_ALL_CHUNKS:
                    PullHandler.handleGetAllChunks(chunkStore, pipe);
                    break;
                default:
                    makeError(new DataOutputStream(pipe.getOutputStream()), -1, "Unknown request: " + request);
            }
        } catch (IOException e) {
            try {
                makeError(new DataOutputStream(pipe.getOutputStream()),  -1, "Internal error.");
            } catch (IOException e1) {
                e1.printStackTrace();
            }
            return Result.ERROR;
        }
        return Result.OK;
    }

    static public void makeError(DataOutputStream outputStream, int request, String message) throws IOException {
        Request.writeResponseHeader(outputStream, request, ERROR);
        StreamHelper.writeString(outputStream, message);
    }

    private void handleGetRemoteTip(IRemotePipe pipe, DataInputStream inputStream) throws IOException {
        String branch = readString(inputStream, 64);

        DataOutputStream outputStream = new DataOutputStream(pipe.getOutputStream());

        ChunkStoreBranchLog localBranchLog = logGetter.get(branch);
        if (localBranchLog == null) {
            makeError(outputStream, GET_REMOTE_TIP, "No access to branch: " + branch);
            return;
        }
        String header;
        if (localBranchLog.getLatest() == null)
            header = "";
        else
            header = localBranchLog.getLatest().getHeader();
        Request.writeResponseHeader(outputStream, GET_REMOTE_TIP, OK);
        StreamHelper.writeString(outputStream, header);
    }
}
