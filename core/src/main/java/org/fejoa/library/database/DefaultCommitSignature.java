/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.database;

import org.fejoa.chunkstore.HashValue;
import org.fejoa.library.ContactPrivate;
import org.fejoa.library.crypto.CryptoSettings;
import org.fejoa.library.crypto.JsonCryptoSettings;
import org.fejoa.library.Constants;
import org.fejoa.library.FejoaContext;
import org.fejoa.library.SigningKeyPair;
import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.crypto.CryptoHelper;
import org.fejoa.library.support.ProtocolHashHelper;
import org.json.JSONException;
import org.json.JSONObject;

import javax.xml.bind.DatatypeConverter;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;


public class DefaultCommitSignature implements ICommitSignature {
    final private FejoaContext context;
    final private SigningKeyPair signingKeyPair;

    public DefaultCommitSignature(FejoaContext context, SigningKeyPair signingKeyPair) {
        this.context = context;
        this.signingKeyPair = signingKeyPair;
    }

    @Override
    public String signMessage(String message, HashValue rootHashValue, Collection<HashValue> parents)
            throws CryptoException {
        try {
            final String hashAlgo = ProtocolHashHelper.HASH_SHA3_256;
            MessageDigest digest = ProtocolHashHelper.getMessageDigest(hashAlgo);
            digest.update(message.getBytes());
            digest.update(rootHashValue.getBytes());
            for (HashValue parent : parents)
                digest.update(parent.getBytes());
            HashValue hashValue = new HashValue(digest.digest());

            // sign the hash
            CryptoSettings.Signature signatureSettings = signingKeyPair.getSignatureSettings();
            String signature = DatatypeConverter.printBase64Binary(ContactPrivate.sign(context, signingKeyPair,
                    hashValue.getBytes()));

            JSONObject object = new JSONObject();
            object.put(Constants.KEY_ID_KEY, signingKeyPair.getKeyId());
            object.put(Constants.SIGNATURE_KEY, signature);
            object.put(Constants.SIGNATURE_SETTINGS_KEY, JsonCryptoSettings.toJson(signatureSettings));
            object.put(Constants.MESSAGE_KEY, message);
            object.put(Constants.HASH_ALGO_KEY, hashAlgo);

            return object.toString();
        } catch (JSONException e) {
            e.printStackTrace();
            throw new RuntimeException("Should not happen (?)");
        }
    }

    @Override
    public boolean verifySignedMessage(String signedMessage, HashValue rootHashValue, Collection<HashValue> parents) {
        throw new RuntimeException("Implement");
    }
}
