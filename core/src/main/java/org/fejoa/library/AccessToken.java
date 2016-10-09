/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library;

import org.fejoa.library.crypto.*;
import org.fejoa.library.database.IOStorageDir;
import org.json.JSONException;
import org.json.JSONObject;

import javax.xml.bind.DatatypeConverter;
import java.io.IOException;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;


public class AccessToken implements IStorageDirBundle {
    final static public String CONTACT_AUTH_KEY_SETTINGS_JSON_KEY = "contactAuthKeySettings";
    final static public String CONTACT_AUTH_KEY_SETTINGS_KEY = "contactAuthKey";
    final static public String CONTACT_AUTH_PUBLIC_KEY_KEY = "contactAuthPublicKey";
    final static public String CONTACT_AUTH_PRIVATE_KEY_KEY = "contactAuthPrivateKey";
    final static public String SIGNATURE_KEY_SETTINGS_KEY = "accessSignatureKey";
    final static public String ACCESS_VERIFICATION_KEY_KEY = "accessVerificationKey";
    final static private String ACCESS_SIGNING_KEY_KEY = "accessSigningKey";
    final static public String ACCESS_KEY_SETTINGS_JSON_KEY = "accessKeySettings";
    final static public String ACCESS_ENTRY_KEY = "accessEntry";
    final static public String ACCESS_ENTRY_SIGNATURE_KEY = "accessEntrySignature";

    final private FejoaContext context;
    private CryptoSettings.Signature contactAuthKeySettings;
    private KeyPair contactAuthKey;
    private CryptoSettings.Signature accessSignatureKeySettings;
    private KeyPair accessSignatureKey;
    private String accessEntry;

    final private List<AccessContact> contacts = new ArrayList<>();

    static public AccessToken create(FejoaContext context) throws CryptoException {
        return new AccessToken(context);
    }

    static public AccessToken open(FejoaContext context, IOStorageDir storageDir) throws IOException, CryptoException {
        return new AccessToken(context, storageDir);
    }

    private AccessToken(FejoaContext context) throws CryptoException {
        this.context = context;

        ICryptoInterface crypto = context.getCrypto();
        contactAuthKeySettings = context.getCryptoSettings().signature;
        contactAuthKey = crypto.generateKeyPair(contactAuthKeySettings);

        accessSignatureKeySettings = context.getCryptoSettings().signature;
        accessSignatureKey = crypto.generateKeyPair(accessSignatureKeySettings);
    }

    private AccessToken(FejoaContext context, IOStorageDir storageDir) throws IOException, CryptoException {
        this.context = context;

        contactAuthKeySettings = new CryptoSettings.Signature();
        accessSignatureKeySettings = new CryptoSettings.Signature();

        read(storageDir);
    }

    public String getAccessEntry() {
        return accessEntry;
    }

    public void setAccessEntry(String accessEntry) {
        this.accessEntry = accessEntry;
    }

    public byte[] getAccessEntrySignature() throws CryptoException {
        return context.getCrypto().sign(accessEntry.getBytes(), accessSignatureKey.getPrivate(),
                accessSignatureKeySettings);
    }

    public String getId() {
        return getId(contactAuthKey.getPublic());
    }

    static String getId(PublicKey contactAuthKey) {
        return CryptoHelper.sha1HashHex(contactAuthKey.getEncoded());
    }

    public AccessTokenServer toServerToken() {
        return new AccessTokenServer(context, contactAuthKey.getPublic(), contactAuthKeySettings,
                accessSignatureKey.getPublic(), accessSignatureKeySettings);
    }

    public AccessTokenContact toContactToken() throws Exception {
        return new AccessTokenContact(context, getContactToken().toString());
    }

    public JSONObject getContactToken() throws JSONException, CryptoException {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put(Constants.ID_KEY, getId());
        jsonObject.put(ACCESS_ENTRY_SIGNATURE_KEY, DatatypeConverter.printBase64Binary(getAccessEntrySignature()));
        jsonObject.put(ACCESS_ENTRY_KEY, accessEntry);
        jsonObject.put(CONTACT_AUTH_KEY_SETTINGS_JSON_KEY, JsonCryptoSettings.toJson(contactAuthKeySettings));
        jsonObject.put(CONTACT_AUTH_PRIVATE_KEY_KEY, DatatypeConverter.printBase64Binary(
                contactAuthKey.getPrivate().getEncoded()));
        return jsonObject;
    }

    @Override
    public void write(IOStorageDir dir) throws IOException, CryptoException {
        // the public keys must be readable by the server

        CryptoSettingsIO.write(contactAuthKeySettings, dir, CONTACT_AUTH_KEY_SETTINGS_KEY);
        dir.writeBytes(CONTACT_AUTH_PUBLIC_KEY_KEY, contactAuthKey.getPublic().getEncoded());
        dir.writeBytes(CONTACT_AUTH_PRIVATE_KEY_KEY, contactAuthKey.getPrivate().getEncoded());

        CryptoSettingsIO.write(accessSignatureKeySettings, dir, SIGNATURE_KEY_SETTINGS_KEY);
        dir.writeBytes(ACCESS_VERIFICATION_KEY_KEY, accessSignatureKey.getPublic().getEncoded());
        dir.writeBytes(ACCESS_SIGNING_KEY_KEY, accessSignatureKey.getPrivate().getEncoded());

        dir.writeString(ACCESS_ENTRY_KEY, accessEntry);

        // write contacts
        IOStorageDir contactBaseDir = new IOStorageDir(dir, "contacts");
        for (AccessContact contact : contacts) {
            IOStorageDir contactDir = new IOStorageDir(contactBaseDir, contact.getContact());
            contact.write(contactDir);
        }
    }

    @Override
    public void read(IOStorageDir dir) throws IOException, CryptoException {
        CryptoSettingsIO.read(contactAuthKeySettings, dir, CONTACT_AUTH_KEY_SETTINGS_KEY);
        PrivateKey privateKey;
        PublicKey publicKey;
        try {
            publicKey = CryptoHelper.publicKeyFromRaw(dir.readBytes(CONTACT_AUTH_PUBLIC_KEY_KEY),
                    contactAuthKeySettings.keyType);
            privateKey = CryptoHelper.privateKeyFromRaw(dir.readBytes(CONTACT_AUTH_PRIVATE_KEY_KEY),
                    contactAuthKeySettings.keyType);
        } catch (Exception e) {
            throw new IOException(e.getMessage());
        }
        contactAuthKey = new KeyPair(publicKey, privateKey);

        CryptoSettingsIO.read(accessSignatureKeySettings, dir, SIGNATURE_KEY_SETTINGS_KEY);
        try {
            publicKey = CryptoHelper.publicKeyFromRaw(dir.readBytes(ACCESS_VERIFICATION_KEY_KEY),
                    accessSignatureKeySettings.keyType);
            privateKey = CryptoHelper.privateKeyFromRaw(dir.readBytes(ACCESS_SIGNING_KEY_KEY),
                    accessSignatureKeySettings.keyType);
        } catch (Exception e) {
            throw new IOException(e.getMessage());
        }
        accessSignatureKey = new KeyPair(publicKey, privateKey);

        accessEntry = dir.readString(ACCESS_ENTRY_KEY);

        // read contacts
        Collection<String> dirs = dir.listDirectories("contacts");
        for (String subDir : dirs) {
            IOStorageDir contactDir = new IOStorageDir(dir, "contacts/" + subDir);
            AccessContact accessContact = new AccessContact();
            accessContact.read(contactDir);
            contacts.add(accessContact);
        }
    }
}
