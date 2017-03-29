/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library;

import org.fejoa.library.crypto.CryptoSettings;
import org.fejoa.library.crypto.JsonCryptoSettings;
import org.fejoa.library.database.IOStorageDir;
import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.crypto.CryptoHelper;
import org.fejoa.library.crypto.CryptoSettingsIO;
import org.json.JSONException;
import org.json.JSONObject;

import javax.xml.bind.DatatypeConverter;
import java.io.IOException;
import java.security.PublicKey;

import static org.fejoa.library.AccessToken.*;

/**
 * Public part of the token that is readable by the server.
 */
public class AccessTokenServer implements IStorageDirBundle {
    final private FejoaContext context;

    private PublicKey contactAuthKey;
    private CryptoSettings.Signature contactAuthKeySettings;
    private PublicKey accessSignatureKey;
    private CryptoSettings.Signature accessSignatureKeySettings;

    AccessTokenServer(FejoaContext context, PublicKey contactAuthKey,
                      CryptoSettings.Signature contactAuthKeySettings,
                      PublicKey accessSignatureKey, CryptoSettings.Signature accessSignatureKeySettings) {
        this.context = context;
        this.contactAuthKey = contactAuthKey;
        this.contactAuthKeySettings = contactAuthKeySettings;
        this.accessSignatureKey = accessSignatureKey;
        this.accessSignatureKeySettings = accessSignatureKeySettings;
    }

    public static AccessTokenServer open(FejoaContext context, IOStorageDir dir) throws IOException, CryptoException {
        AccessTokenServer accessToken = new AccessTokenServer(context);
        accessToken.read(dir);
        return accessToken;
    }

    private AccessTokenServer(FejoaContext context) {
        this.context = context;
    }

    public AccessTokenServer(FejoaContext context, JSONObject jsonObject) throws Exception {
        this.context = context;

        contactAuthKeySettings = JsonCryptoSettings.signatureFromJson(jsonObject.getJSONObject(
                AccessToken.CONTACT_AUTH_KEY_SETTINGS_JSON_KEY));
        byte[] rawKey = DatatypeConverter.parseBase64Binary(
                jsonObject.getString(CONTACT_AUTH_PUBLIC_KEY_KEY));
        contactAuthKey = CryptoHelper.publicKeyFromRaw(rawKey, contactAuthKeySettings.keyType);

        accessSignatureKeySettings = JsonCryptoSettings.signatureFromJson(jsonObject.getJSONObject(
                AccessToken.ACCESS_KEY_SETTINGS_JSON_KEY));
        rawKey = DatatypeConverter.parseBase64Binary(
                jsonObject.getString(ACCESS_VERIFICATION_KEY_KEY));
        accessSignatureKey = CryptoHelper.publicKeyFromRaw(rawKey, contactAuthKeySettings.keyType);
    }

    public String getId() {
        return AccessToken.getId(contactAuthKey);
    }

    public boolean auth(String authToken, byte[] signature) throws CryptoException {
        return context.getCrypto().verifySignature(authToken.getBytes(), signature, contactAuthKey,
                contactAuthKeySettings);
    }

    public boolean verify(String accessEntry, byte[] signature) throws CryptoException {
        return context.getCrypto().verifySignature(accessEntry.getBytes(), signature, accessSignatureKey,
                accessSignatureKeySettings);
    }

    public JSONObject toJson() throws JSONException {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put(CONTACT_AUTH_PUBLIC_KEY_KEY, DatatypeConverter.printBase64Binary(
                contactAuthKey.getEncoded()));
        jsonObject.put(AccessToken.CONTACT_AUTH_KEY_SETTINGS_JSON_KEY, JsonCryptoSettings.toJson(
                contactAuthKeySettings));
        jsonObject.put(ACCESS_VERIFICATION_KEY_KEY, DatatypeConverter.printBase64Binary(
                accessSignatureKey.getEncoded()));
        jsonObject.put(AccessToken.ACCESS_KEY_SETTINGS_JSON_KEY, JsonCryptoSettings.toJson(
                accessSignatureKeySettings));
        return jsonObject;
    }

    @Override
    public void write(IOStorageDir dir) throws IOException, CryptoException {
        CryptoSettingsIO.write(contactAuthKeySettings, dir, CONTACT_AUTH_KEY_SETTINGS_KEY);
        dir.putBytes(CONTACT_AUTH_PUBLIC_KEY_KEY, contactAuthKey.getEncoded());

        CryptoSettingsIO.write(accessSignatureKeySettings, dir, SIGNATURE_KEY_SETTINGS_KEY);
        dir.putBytes(ACCESS_VERIFICATION_KEY_KEY, accessSignatureKey.getEncoded());
    }

    @Override
    public void read(IOStorageDir dir) throws IOException, CryptoException {
        contactAuthKeySettings = new CryptoSettings.Signature();
        accessSignatureKeySettings = new CryptoSettings.Signature();

        CryptoSettingsIO.read(contactAuthKeySettings, dir, CONTACT_AUTH_KEY_SETTINGS_KEY);
        try {
            contactAuthKey = CryptoHelper.publicKeyFromRaw(dir.readBytes(CONTACT_AUTH_PUBLIC_KEY_KEY),
                    contactAuthKeySettings.keyType);
        } catch (Exception e) {
            throw new IOException(e.getMessage());
        }

        CryptoSettingsIO.read(accessSignatureKeySettings, dir, SIGNATURE_KEY_SETTINGS_KEY);
        try {
            accessSignatureKey = CryptoHelper.publicKeyFromRaw(dir.readBytes(ACCESS_VERIFICATION_KEY_KEY),
                    accessSignatureKeySettings.keyType);
        } catch (Exception e) {
            throw new IOException(e.getMessage());
        }
    }
}
