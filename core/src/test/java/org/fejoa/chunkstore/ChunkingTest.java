/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.chunkstore;

import junit.framework.TestCase;
import org.fejoa.library.support.StorageLib;
import org.rabinfingerprint.fingerprint.RabinFingerprintLongWindowed;
import org.rabinfingerprint.polynomial.Polynomial;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


public class ChunkingTest extends TestCase {
    final List<String> cleanUpFiles = new ArrayList<String>();

    @Override
    public void tearDown() throws Exception {
        super.tearDown();

        for (String dir : cleanUpFiles)
            StorageLib.recursiveDeleteFile(new File(dir));
    }

    private IChunkAccessor getAccessor(final ChunkStore chunkStore) {
        return new IChunkAccessor() {
            @Override
            public DataInputStream getChunk(ChunkPointer hash) throws IOException {
                return new DataInputStream(new ByteArrayInputStream(chunkStore.getChunk(hash.getBoxHash().getBytes())));
            }

            @Override
            public PutResult<HashValue> putChunk(byte[] data, HashValue ivHash) throws IOException {
                return chunkStore.openTransaction().put(data);
            }

            @Override
            public void releaseChunk(HashValue data) {

            }
        };
    }

    public void testRabin() throws Exception {
        Polynomial irreduciblePolynomial = Polynomial.createFromLong(9256118209264353l);
        RabinFingerprintLongWindowed window = new RabinFingerprintLongWindowed(irreduciblePolynomial, 48);

        long MASK = 0x1FFF;

        long prevMatch = 0;
        long sumMatchSizes = 0;
        long nMatches = 0;
        for (long value = 0; value < 1024 * 8 * 10000; value++) {
            byte random = (byte)(256 * Math.random());
            window.pushByte(random);
            if ((~window.getFingerprintLong() & MASK) == 0) {
                long matchSize = value - prevMatch;
                sumMatchSizes += matchSize;
                nMatches++;
                prevMatch = value;
                //System.out.println(matchSize);
            }
        }
        System.out.println("Average match size: " + sumMatchSizes / nMatches);
    }

    public void testRabin2() throws Exception {
        Polynomial irreduciblePolynomial = Polynomial.createFromLong(9256118209264353l);
        RabinFingerprintLongWindowed window = new RabinFingerprintLongWindowed(irreduciblePolynomial, 48);

        long MASK = 0xFFFFFFFFL;
        long prevMatch = 0;
        long sumMatchSizes = 0;
        long nMatches = 0;
        for (long value = 0; value < 1024 * 1000; value++) {
            byte random = (byte)(256 * Math.random());
            window.pushByte(random);
            if ((window.getFingerprintLong() & MASK) < MASK / (8 * 1024)) {
                long matchSize = value - prevMatch;
                sumMatchSizes += matchSize;
                nMatches++;
                prevMatch = value;
                System.out.println(matchSize);
            }
        }
        System.out.println("Average match size: " + sumMatchSizes / nMatches);
    }

    public void testRabin3() throws Exception {
        RabinSplitter rabinSplitter = new RabinSplitter(5000, 32, 100000000);

        long prevMatch = 0;
        long sumMatchSizes = 0;
        long nMatches = 0;
        for (long value = 0; value < 1024 * 1000 * 100; value++) {
            byte random = (byte)(256 * Math.random());
            rabinSplitter.update(random);
            if (rabinSplitter.isTriggered()) {
                long matchSize = value - prevMatch;
                sumMatchSizes += matchSize;
                nMatches++;
                prevMatch = value;
                rabinSplitter.reset();
                //System.out.println(matchSize);
            }
        }
        System.out.println("Average match size: " + sumMatchSizes / nMatches);
    }
}
