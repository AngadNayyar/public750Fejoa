/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library;

import org.fejoa.library.crypto.JsonCryptoSettings;
import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.crypto.CryptoHelper;
import org.fejoa.library.crypto.CryptoSettings;
import org.json.JSONException;
import org.json.JSONObject;

import javax.xml.bind.DatatypeConverter;
import java.io.IOException;
import java.security.PrivateKey;

/**
 * Contact side of the access token.
 */
public class AccessTokenContact {
    final private FejoaContext context;
    final private JSONObject accessToken;

    final private String id;
    final private CryptoSettings.Signature contactAuthKeySettings;
    final private PrivateKey contactAuthKey;
    final private byte[] accessEntrySignature;
    final private String accessEntry;

    final private BranchAccessRight accessRights;

    public AccessTokenContact(FejoaContext context, String rawAccessToken) throws CryptoException, IOException {
        this(context, new JSONObject(rawAccessToken));
    }

    public AccessTokenContact(FejoaContext context, JSONObject accessToken) throws CryptoException, IOException {
        this.context = context;
        this.accessToken = accessToken;

        byte[] rawKey;
        try {
            id = accessToken.getString(Constants.ID_KEY);
            accessEntrySignature = DatatypeConverter.parseBase64Binary(accessToken.getString(
                    AccessToken.ACCESS_ENTRY_SIGNATURE_KEY));
            accessEntry = accessToken.getString(AccessToken.ACCESS_ENTRY_KEY);
            contactAuthKeySettings = JsonCryptoSettings.signatureFromJson(accessToken.getJSONObject(
                    AccessToken.CONTACT_AUTH_KEY_SETTINGS_JSON_KEY));
            rawKey = DatatypeConverter.parseBase64Binary(
                    accessToken.getString(AccessToken.CONTACT_AUTH_PRIVATE_KEY_KEY));
            contactAuthKey = CryptoHelper.privateKeyFromRaw(rawKey, contactAuthKeySettings.keyType);

            // access rights
            accessRights = new BranchAccessRight(new JSONObject(accessEntry));
        } catch (JSONException e) {
            throw new IOException(e);
        }
    }

    public String getId() {
        return id;
    }

    public JSONObject toJson() throws JSONException {
        return accessToken;
    }

    public String getAccessEntry() {
        return accessEntry;
    }

    public BranchAccessRight getAccessRights() {
        return accessRights;
    }

    public byte[] getAccessEntrySignature() {
        return accessEntrySignature;
    }

    public byte[] signAuthToken(String token) throws CryptoException {
        return context.getCrypto().sign(token.getBytes(), contactAuthKey, contactAuthKeySettings);
    }
}
