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
import org.fejoa.library.remote.JsonRPC;
import org.fejoa.library.remote.RemoteJob;
import org.fejoa.library.support.StreamHelper;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;


public class RequestHandler {
    public enum Result {
        OK(0, "ok"),
        MISSING_ACCESS_RIGHTS(1, "missing access rights"),
        ERROR(-1, "error");

        private int value;
        private String description;

        Result(int value, String description) {
            this.value = value;
            this.description = description;
        }

        public int getValue() {
            return value;
        }

        public String getDescription() {
            return description;
        }
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
            case Request.GET_REMOTE_TIP:
            case Request.GET_CHUNKS:
                if ((accessRights & BranchAccessRight.PULL) == 0)
                    return false;
                break;
            case Request.PUT_CHUNKS:
            case Request.HAS_CHUNKS:
                if ((accessRights & BranchAccessRight.PUSH) == 0)
                    return false;
                break;
            case Request.GET_ALL_CHUNKS:
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
                case Request.GET_REMOTE_TIP:
                    handleGetRemoteTip(pipe, inputStream);
                    break;
                case Request.GET_CHUNKS:
                    PullHandler.handleGetChunks(chunkStore, pipe, inputStream);
                    break;
                case Request.PUT_CHUNKS:
                    PushHandler.handlePutChunks(chunkStore, logGetter, pipe, inputStream);
                    break;
                case Request.HAS_CHUNKS:
                    HasChunksHandler.handleHasChunks(chunkStore, pipe, inputStream);
                    break;
                case Request.GET_ALL_CHUNKS:
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
        Request.writeResponseHeader(outputStream, request, Request.ERROR);
        StreamHelper.writeString(outputStream, message);
    }

    private void handleGetRemoteTip(IRemotePipe pipe, DataInputStream inputStream) throws IOException {
        String branch = StreamHelper.readString(inputStream, 64);

        DataOutputStream outputStream = new DataOutputStream(pipe.getOutputStream());

        ChunkStoreBranchLog localBranchLog = logGetter.get(branch);
        if (localBranchLog == null) {
            makeError(outputStream, Request.GET_REMOTE_TIP, "No access to branch: " + branch);
            return;
        }
        String header;
        if (localBranchLog.getLatest() == null)
            header = "";
        else
            header = localBranchLog.getLatest().getHeader();
        Request.writeResponseHeader(outputStream, Request.GET_REMOTE_TIP, Request.OK);
        StreamHelper.writeString(outputStream, header);
    }
}
