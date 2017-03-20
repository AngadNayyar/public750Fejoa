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

import javax.crypto.SecretKey;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;

/**
 * Zero knowledge proof that the prover knows a secret. The prover does not reveal the secret if the verifier does not
 * know the secret. Moreover, the verifier can't brute force the prover's secret from the exchanged data.
 *
 * This is based on EKE2 (Mihir Bellare, David Pointchevaly and Phillip Rogawayz. Authenticated key exchange secure
 * against dictionary attacks)
 *
 * Outline of the protocol:
 * - Enc(), Dec() are encryption/decryption methods using the secret as key
 * - H is a hash function
 * 1) ProverState0 generates random value x and calculates:
 * gx = g.modPow(x, p)
 * encGX = Enc(g.modPow(x, p))
 * The prover sends encGX to the Verifier:
 * ProverState0 -> encGX -> Verifier
 * 2) The verifier generates random value y and calculates:
 * gx = Dec(encGX)
 * gy = g.modPow(y, p)
 * gxy = gx.modPow(Y, p)
 * sk' = gx || gy || gxy
 * authVerifier = H(sk' || 1) (used to by the prover to verify that the verifier knows the secret)
 * The verifier sends Enc(gy) and authVerifier to the prover:
 * Verifier -> Enc(gy),authVerifier -> ProverState1
 * 3) ProverState1 calculates:
 * gy = Dec(encGY)
 * gxy = gy.modPow(x, p)
 * sk' = gx || gy || gxy
 * authVerifier = H(sk' || 1)
 * authProver = H(sk' || 2)
 * The prover checks that authVerifier matches with the received value and sends authProver to the verifier:
 * ProverState1 -> authProver -> Verifier
 * 4) The verifier calculates:
 * authProver = H(sk' || 2)
 * If authProver matches the received value the authentication succeeded.
 */
public class AuthProtocolEKE2_SHA3_256_CTR {
    final static public String RFC5114_2048_256 = "RFC5114_2048_256";

    final private BigInteger g;
    final private BigInteger p;
    final private SecretKey secretKey;

    // the id are not configerable at the moment; they do take any affect
    final String proverId = "prover";
    final String verifierId = "verifier";

    final private SecureRandom secureRandom = new SecureRandom();

    final private CryptoSettings.Symmetric symmetric;
    // using always the same iv is ok because we only encrypt random data
    final private byte[] iv = new byte[16];

    byte[] encrypt256(SecretKey secret, BigInteger data) throws CryptoException {
        ICryptoInterface crypto = Crypto.get();
        return crypto.encryptSymmetric(data.toByteArray(), secret, iv, symmetric);
    }

    BigInteger decrypt256(SecretKey secret, byte[] data) throws CryptoException {
        ICryptoInterface crypto = Crypto.get();
        return toPositiveBigInteger(crypto.decryptSymmetric(data, secret, iv, symmetric));
    }

    static private byte[] hash(byte[] data) {
        return CryptoHelper.sha3_256Hash(data);
    }

    private byte[] getSessionKeyPrime(BigInteger gx, BigInteger gy, BigInteger gxy) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            outputStream.write(proverId.getBytes());
            outputStream.write(verifierId.getBytes());
            outputStream.write(gx.toByteArray());
            outputStream.write(gy.toByteArray());
            outputStream.write(gxy.toByteArray());
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("Should not happen");
        }
        return hash(outputStream.toByteArray());
    }

    private byte[] getVerifierAuthToken(byte[] sessionKeyPrime) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            outputStream.write(sessionKeyPrime);
            outputStream.write("1".getBytes());
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("Should not happen");
        }
        return hash(outputStream.toByteArray());
    }

    private byte[] getProverAuthToken(byte[] sessionKeyPrime) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            outputStream.write(sessionKeyPrime);
            outputStream.write("2".getBytes());
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("Should not happen");
        }
        return hash(outputStream.toByteArray());
    }

    public class ProverState0 {
        final private BigInteger x;
        final private BigInteger gx;

        protected ProverState0() {
            x = new BigInteger(256, secureRandom);
            gx = g.modPow(x, p);
        }

        public byte[] getEncGX() throws CryptoException {
            return encrypt256(secretKey, gx);
        }

        public ProverState1 setVerifierResponse(byte[] encGY, byte[] authToken) throws CryptoException {
            BigInteger gy = decrypt256(secretKey, encGY);
            BigInteger gxy = gy.modPow(x, p);
            byte[] sessionKeyPrime = getSessionKeyPrime(gx, gy, gxy);
            byte[] expectedVerifierToken = getVerifierAuthToken(sessionKeyPrime);
            if (!Arrays.equals(expectedVerifierToken, authToken))
                return null;
            return new ProverState1(sessionKeyPrime);
        }
    }

    public class ProverState1 {
        final byte[] sessionKeyPrime;

        protected ProverState1(byte[] sessionKeyPrime) {
            this.sessionKeyPrime = sessionKeyPrime;
        }

        public byte[] getAuthToken() {
            return getProverAuthToken(sessionKeyPrime);
        }
    }

    public class Verifier {
        final private BigInteger gy;
        final byte[] sessionKeyPrime;

        public Verifier(byte[] encGX) throws CryptoException {
            BigInteger y = new BigInteger(256, secureRandom);
            this.gy = g.modPow(y, p);
            BigInteger gx = decrypt256(secretKey, encGX);
            BigInteger gxy = gx.modPow(y, p);
            sessionKeyPrime = getSessionKeyPrime(gx, gy, gxy);
        }

        public byte[] getEncGy() throws CryptoException {
            return encrypt256(secretKey, gy);
        }

        public byte[] getAuthToken() {
            return getVerifierAuthToken(sessionKeyPrime);
        }

        public boolean verify(byte[] proverToken) {
            byte[] expectedToken = getProverAuthToken(sessionKeyPrime);
            return Arrays.equals(expectedToken, proverToken);
        }
    }

    private AuthProtocolEKE2_SHA3_256_CTR(String encGroup, byte[] secret) throws CryptoException,
            InvalidKeySpecException, NoSuchAlgorithmException {
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

        symmetric = CryptoSettings.getDefault().symmetric;
        // Note: no padding might be important to prevent some attacks(?)
        symmetric.algorithm = "AES/CTR/NoPadding";
        symmetric.keyType = "AES";
        symmetric.keySize = 256;
        symmetric.ivSize = 16 * 8;

        this.secretKey = CryptoHelper.secretKey(secret, symmetric);
    }

    /**
     * Its important that the secret is a positive value otherwise s could become negative which would leak one byte.
     */
    static private BigInteger toPositiveBigInteger(byte[] value) {
        return new BigInteger(1, value);
    }

    private ProverState0 createProver() {
        return new ProverState0();
    }

    private Verifier createVerifier(byte[] encGX) throws CryptoException {
        return new Verifier(encGX);
    }

    static public ProverState0 createProver(String encGroup, byte[] secret)
            throws CryptoException, InvalidKeySpecException, NoSuchAlgorithmException {
        return new AuthProtocolEKE2_SHA3_256_CTR(encGroup, secret).createProver();
    }

    static public Verifier createVerifier(String encGroup, byte[] secret, byte[] encGX)
            throws CryptoException, InvalidKeySpecException, NoSuchAlgorithmException {
        return new AuthProtocolEKE2_SHA3_256_CTR(encGroup, secret).createVerifier(encGX);
    }
}
