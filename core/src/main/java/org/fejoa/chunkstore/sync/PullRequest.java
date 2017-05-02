/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.chunkstore.sync;

import org.fejoa.chunkstore.*;
import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.database.ICommitSignature;
import org.fejoa.library.remote.IRemotePipe;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static org.fejoa.chunkstore.sync.Request.GET_CHUNKS;


public class PullRequest {
    final private Repository requestRepo;
    final private ICommitSignature commitSignature;

    public PullRequest(Repository requestRepo, ICommitSignature commitSignature) {
        this.requestRepo = requestRepo;
        this.commitSignature = commitSignature;
    }

    static public ChunkFetcher createRemotePipeFetcher(ChunkStore.Transaction transaction,
                                                       final IRemotePipe remotePipe) {
        return new ChunkFetcher(transaction, new ChunkFetcher.IFetcherBackend() {
            @Override
            public void fetch(ChunkStore.Transaction transaction, List<HashValue> requestedChunks) throws IOException {
                DataOutputStream outputStream = new DataOutputStream(remotePipe.getOutputStream());
                Request.writeRequestHeader(outputStream, GET_CHUNKS);
                outputStream.writeLong(requestedChunks.size());
                for (HashValue hashValue : requestedChunks)
                    outputStream.write(hashValue.getBytes());

                DataInputStream inputStream = new DataInputStream(remotePipe.getInputStream());
                Request.receiveHeader(inputStream, GET_CHUNKS);
                long chunkCount = inputStream.readLong();
                if (chunkCount != requestedChunks.size()) {
                    throw new IOException("Received chunk count is: " + chunkCount + " but " + requestedChunks.size()
                            + " expected.");
                }

                for (int i = 0; i < chunkCount; i++) {
                    HashValue hashValue = Config.newBoxHash();
                    inputStream.readFully(hashValue.getBytes());
                    int size = inputStream.readInt();

                    byte[] buffer = new byte[size];
                    inputStream.readFully(buffer);
                    PutResult<HashValue> result = transaction.put(buffer);
                    if (!result.key.equals(hashValue))
                        throw new IOException("Hash miss match. Expected:" + hashValue + ", Got: " + result.key);
                }
            }
        });
    }

    /**
     * returns the remote tip
     */
    public ChunkContainerRef pull(IRemotePipe remotePipe, String branch) throws IOException, CryptoException {
        String remoteTipMessage = LogEntryRequest.getRemoteTip(remotePipe, branch).getMessage();
        if (remoteTipMessage.equals(""))
            return new ChunkContainerRef();
        ChunkContainerRef remoteTip = requestRepo.getCommitCallback().commitPointerFromLog(remoteTipMessage);
        IRepoChunkAccessors.ITransaction transaction = requestRepo.getCurrentTransaction();

        // up to date?
        HashValue localTip = requestRepo.getTip();
        if (localTip.equals(remoteTip.getDataHash())) {
            return remoteTip;
        }

        GetCommitJob getCommitJob = new GetCommitJob(null, transaction, remoteTip);
        ChunkFetcher chunkFetcher = createRemotePipeFetcher(transaction.getRawAccessor(), remotePipe);
        chunkFetcher.enqueueJob(getCommitJob);
        chunkFetcher.fetch();

        MergeResult merged = requestRepo.merge(transaction, getCommitJob.getCommitBox());
        switch (merged) {
            case MERGED:
                requestRepo.commitInternal("Merge after pull.", commitSignature,
                        Collections.singleton(getCommitJob.getCommitBox().getRef()));
                break;
            case FAST_FORWARD:
                requestRepo.commitInternal("Merge after pull.", commitSignature);
                break;

            case UNCOMMITTED_CHANGES:
                return null;
        }

        return remoteTip;
    }
}
