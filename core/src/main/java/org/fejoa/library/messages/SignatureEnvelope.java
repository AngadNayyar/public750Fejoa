/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.messages;

import org.fejoa.library.*;
import org.fejoa.library.crypto.JsonCryptoSettings;
import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.crypto.CryptoHelper;
import org.fejoa.library.crypto.CryptoSettings;
import org.fejoa.library.support.ProtocolHashHelper;
import org.fejoa.library.support.StreamHelper;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;


public class SignatureEnvelope {
    static final public String SIGNATURE_TYPE = "signature";
    static final private String SIGNATURE_KEY = "signature";
    static final private String SENDER_ID_KEY = "senderId";
    static final private String KEY_ID_KEY = "keyId";
    static final private String SETTINGS_KEY = "settings";

    static public class ReturnValue {
        final public InputStream inputStream;
        final public String senderId;

        private ReturnValue(InputStream inputStream, String senderId) {
            this.inputStream = inputStream;
            this.senderId = senderId;
        }
    }

    static public InputStream signStream(byte[] data, boolean isRawData, IContactPrivate contactPrivate,
                                         SigningKeyPair signingKeyPair)
            throws IOException, CryptoException, JSONException {
        final String hashAlgo = ProtocolHashHelper.HASH_SHA3_256;
        byte[] hash = ProtocolHashHelper.hash(data, hashAlgo);
        String signature = CryptoHelper.toHex(contactPrivate.sign(signingKeyPair, hash));
        JSONObject object = new JSONObject();
        object.put(Envelope.PACK_TYPE_KEY, SIGNATURE_TYPE);
        if (isRawData)
            object.put(Envelope.CONTAINS_DATA_KEY, 1);
        object.put(KEY_ID_KEY, signingKeyPair.getKeyId().toString());
        object.put(SENDER_ID_KEY, contactPrivate.getId());
        object.put(Constants.HASH_ALGO_KEY, hashAlgo);
        object.put(SIGNATURE_KEY, signature);
        object.put(SETTINGS_KEY, JsonCryptoSettings.toJson(signingKeyPair.getSignatureSettings()));
        String header = object.toString() + "\n";

        return new SequenceInputStream(new ByteArrayInputStream(header.getBytes()), new ByteArrayInputStream(data));
    }

    /**
     * Verifies the signature of the input stream
     *
     * @param header
     * @param inputStream
     * @param contactFinder
     * @return
     * @throws IOException
     * @throws JSONException
     * @throws CryptoException
     */
    static ReturnValue verifyStream(JSONObject header, InputStream inputStream,
                                    IContactFinder<IContactPublic> contactFinder)
            throws IOException, JSONException, CryptoException {
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        StreamHelper.copy(inputStream, outStream);
        byte[] data = outStream.toByteArray();

        // verify
        String senderId = header.getString(SENDER_ID_KEY);
        IContactPublic contact = contactFinder.get(senderId);
        if (contact == null)
            throw new IOException("Unknown sender!");
        KeyId keyId = new KeyId(header.getString(KEY_ID_KEY));
        byte[] signature = CryptoHelper.fromHex(header.getString(SIGNATURE_KEY));
        CryptoSettings.Signature settings = JsonCryptoSettings.signatureFromJson(header.getJSONObject(SETTINGS_KEY));
        byte[] hash = ProtocolHashHelper.hash(data, header);
        if (!contact.verify(keyId, hash, signature, settings))
            throw new IOException("can't verify signature!");

        return new ReturnValue(new ByteArrayInputStream(data), senderId);
    }

    static public byte[] sign(byte[] data, boolean isRawData, IContactPrivate contactPrivate,
                              SigningKeyPair signingKeyPair) throws CryptoException, JSONException, IOException {
        final String hashAlgo = ProtocolHashHelper.HASH_SHA3_256;
        byte[] hash = ProtocolHashHelper.hash(data, hashAlgo);
        String signature = CryptoHelper.toHex(contactPrivate.sign(signingKeyPair, hash));
        JSONObject object = new JSONObject();
        object.put(Envelope.PACK_TYPE_KEY, SIGNATURE_TYPE);
        if (isRawData)
            object.put(Envelope.CONTAINS_DATA_KEY, 1);
        object.put(KEY_ID_KEY, signingKeyPair.getId().toString());
        object.put(SENDER_ID_KEY, contactPrivate.getId());
        object.put(Constants.HASH_ALGO_KEY, hashAlgo);
        object.put(SIGNATURE_KEY, signature);
        object.put(SETTINGS_KEY, JsonCryptoSettings.toJson(signingKeyPair.getSignatureSettings()));
        String header = object.toString() + "\n";

        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        outStream.write(header.getBytes());
        outStream.write(data);
        return outStream.toByteArray();
    }

    static public byte[] verify(JSONObject header, InputStream inputStream,
                                IContactFinder<IContactPublic> contactFinder)
            throws IOException, JSONException, CryptoException {
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        StreamHelper.copy(inputStream, outStream);
        byte[] data = outStream.toByteArray();

        // verify
        String senderId = header.getString(SENDER_ID_KEY);
        IContactPublic contact = contactFinder.get(senderId);
        if (contact == null)
            throw new IOException("Unknown sender!");
        KeyId keyId = new KeyId(header.getString(KEY_ID_KEY));
        byte[] signature = CryptoHelper.fromHex(header.getString(SIGNATURE_KEY));
        CryptoSettings.Signature settings = JsonCryptoSettings.signatureFromJson(header.getJSONObject(SETTINGS_KEY));
        byte[] hash = ProtocolHashHelper.hash(data, header);
        if (!contact.verify(keyId, hash, signature, settings))
            throw new IOException("can't verify signature!");

        return data;
    }
}
