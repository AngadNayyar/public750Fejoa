/*
 * Copyright 2017.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.crypto;

import junit.framework.TestCase;


public class AuthProtocolEKE2Test extends TestCase {
    public void testBasics() throws Exception {
        byte[] secret = CryptoHelper.sha3_256Hash("3123489723412341324780867621345".getBytes());

        AuthProtocolEKE2_SHA3_256_CTR.ProverState0 proverState0 = AuthProtocolEKE2_SHA3_256_CTR.createProver(
                AuthProtocolEKE2_SHA3_256_CTR.RFC5114_2048_256, secret);
        AuthProtocolEKE2_SHA3_256_CTR.Verifier verifier = AuthProtocolEKE2_SHA3_256_CTR.createVerifier(
                AuthProtocolEKE2_SHA3_256_CTR.RFC5114_2048_256, secret, proverState0.getEncGX());

        AuthProtocolEKE2_SHA3_256_CTR.ProverState1 proverState1 = proverState0.setVerifierResponse(verifier.getEncGy(),
                verifier.getAuthToken());
        if (proverState1 == null || !verifier.verify(proverState1.getAuthToken()))
            throw new Exception();
    }
}