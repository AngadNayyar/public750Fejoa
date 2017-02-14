/*
 * Copyright 2017.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.crypto;

import org.bouncycastle.crypto.agreement.DHStandardGroups;
import org.bouncycastle.crypto.params.DHParameters;

import java.math.BigInteger;
import java.security.SecureRandom;

/**
 * Zero knowledge proof that the prover knows a secret. The prover does not reveal the secret if the verifier does not
 * know the secret. Moreover, the verifier can't brute force the prover's secret from the exchanged data.
 *
 * Schnorr Protocol:
 *
 * 1) ProverState0 generates random value r and sends h = g.modPow(r, p) to the Verifier
 * ProverState0 -> h -> Verifier
 * 2) The verifier generates random value b and sends it to the prover which changes to state1
 * Verifier -> b -> ProverState1
 * 3) ProverState1 calculates s = r + b * secret and sends s to the verifier
 * 4) The verifier checks that:
 * g.modPow(s, p) == (h * B.modPow(b, p)) % p with B = g.modPow(secret, p)
 */
public class ZeroKnowledgeCompare {
    final static public String RFC5114_2048_256 = "RFC5114_2048_256";

    final private BigInteger g;
    final private BigInteger p;

    final private SecureRandom secureRandom = new SecureRandom();

    public class ProverState0 {
        final private BigInteger r;

        protected ProverState0() {
            r = new BigInteger(256, secureRandom);
        }

        public BigInteger getH() {
            return g.modPow(r, p);
        }

        public ProverState1 setVerifierChallenge(BigInteger b) {
            return new ProverState1(r, b);
        }
    }

    public class ProverState1 {
        final private BigInteger r;
        final private BigInteger b;

        protected ProverState1(BigInteger r, BigInteger b) {
            this.r = r;
            this.b = b;
        }

        public BigInteger getS(byte[] secret) {
            return r.add(b.multiply(toPositiveBigInteger(secret)));
        }
    }

    public class Verifier {
        final private BigInteger secret;
        final private BigInteger h;
        final private BigInteger b;

        public Verifier(byte[] secret, BigInteger h) {
            this.secret = toPositiveBigInteger(secret);
            this.h = h;
            this.b = new BigInteger(256, secureRandom);
        }

        public BigInteger getB() {
            return b;
        }

        public boolean verify(BigInteger s) {
            BigInteger B = g.modPow(secret, p);
            return g.modPow(s, p).equals(h.multiply(B.modPow(b, p)).mod(p));
        }
    }

    private ZeroKnowledgeCompare(String encGroup) throws CryptoException {
        DHParameters parameters;
        switch (encGroup) {
            case RFC5114_2048_256: {
                parameters = DHStandardGroups.rfc5114_2048_256;
                break;
            }

            default:
                throw new CryptoException("Unsupported group: " + encGroup);
        }
        g = parameters.getG();
        p = parameters.getP();
    }

    /**
     * Its important that the secret is a positive value otherwise s could become negative which would leak one byte.
     */
    private BigInteger toPositiveBigInteger(byte[] value) {
        return new BigInteger(1, value);
    }

    private ProverState0 createProver() {
        return new ProverState0();
    }

    private Verifier createVerifier(byte[] secret, BigInteger h) {
        return new Verifier(secret, h);
    }

    static public ProverState0 createProver(String encGroup) throws CryptoException {
        return new ZeroKnowledgeCompare(encGroup).createProver();
    }

    static public Verifier createVerifier(String encGroup, byte[] secret, BigInteger commitment)
            throws CryptoException {
        return new ZeroKnowledgeCompare(encGroup).createVerifier(secret, commitment);
    }
}
