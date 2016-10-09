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
import org.fejoa.library.remote.IRemotePipe;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;


public class PullRepoRequest {
    final private Repository requestRepo;

    public PullRepoRequest(Repository requestRepo) {
        this.requestRepo = requestRepo;
    }

    public void pull(IRemotePipe remotePipe, String branch) throws IOException, CryptoException {
        String header = LogEntryRequest.getRemoteTip(remotePipe, branch).getHeader();
        if (header.equals(""))
            return;

        DataOutputStream outputStream = new DataOutputStream(remotePipe.getOutputStream());
        Request.writeRequestHeader(outputStream, Request.GET_ALL_CHUNKS);

        DataInputStream inputStream = new DataInputStream(remotePipe.getInputStream());
        Request.receiveHeader(inputStream, Request.GET_ALL_CHUNKS);
        ChunkStore.Transaction transaction = requestRepo.getCurrentTransaction().getRawAccessor();
        long chunkCount = inputStream.readLong();
        for (int i = 0; i < chunkCount; i++) {
            HashValue hashValue = Config.newBoxHash();
            inputStream.readFully(hashValue.getBytes());
            int size = inputStream.readInt();
            byte[] buffer = new byte[size];
            inputStream.readFully(buffer);
            PutResult<HashValue> result = transaction.put(buffer);
            if (!result.key.equals(hashValue))
                throw new IOException("Hash miss match.");
        }
        transaction.commit();

        ChunkStoreBranchLog log = requestRepo.getBranchLog();
        log.add(ChunkStoreBranchLog.Entry.fromHeader(header));
    }
}
