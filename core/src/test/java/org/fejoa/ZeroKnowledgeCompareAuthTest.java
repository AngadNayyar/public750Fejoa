/*
 * Copyright 2017.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa;

import junit.framework.TestCase;
import org.fejoa.library.crypto.ZeroKnowledgeCompare;


public class ZeroKnowledgeCompareAuthTest extends TestCase {

    public void testBasics() throws Exception {
        byte[] secret = "3123489723412341324780867621345".getBytes();

        ZeroKnowledgeCompare.ProverState0 proverState0 = ZeroKnowledgeCompare.createProver(
                ZeroKnowledgeCompare.RFC5114_2048_256);
        ZeroKnowledgeCompare.Verifier verifier = ZeroKnowledgeCompare.createVerifier(
                ZeroKnowledgeCompare.RFC5114_2048_256, secret, proverState0.getH());

        ZeroKnowledgeCompare.ProverState1 proverState1 = proverState0.setVerifierChallenge(verifier.getB());
        if (!verifier.verify(proverState1.getS(secret)))
            throw new Exception();
    }
}
