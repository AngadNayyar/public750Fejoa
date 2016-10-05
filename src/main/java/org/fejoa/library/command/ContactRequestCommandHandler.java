/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.command;

import org.fejoa.library.crypto.*;
import org.fejoa.library.*;
import org.json.JSONException;
import org.json.JSONObject;

import javax.xml.bind.DatatypeConverter;
import java.io.IOException;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;


public class ContactRequestCommandHandler extends EnvelopeCommandHandler {
    final private ContactStore contactStore;
    private IListener listener;
    final private List<ContactRequest> contactRequestList = new ArrayList<>();

    public interface IListener extends IncomingCommandManager.IListener {
        void onContactRequest(ContactRequestCommandHandler handler, ContactRequest contactRequest);
        void onContactRequestReply(ContactRequestCommandHandler handler, ContactRequest contactRequest);
        void onContactRequestFinished();
    }

    static public class AutoAccept implements IListener {
        @Override
        public void onContactRequest(ContactRequestCommandHandler handler, ContactRequest contactRequest) {
            handler.acceptContactRequest(contactRequest);
        }

        @Override
        public void onContactRequestReply(ContactRequestCommandHandler handler, ContactRequest contactRequest) {
            handler.acceptContactRequestReply(contactRequest);
        }

        @Override
        public void onContactRequestFinished() {

        }

        @Override
        public void onError(Exception exception) {

        }
    }

    public static class ContactRequest {
        final private IncomingCommandManager.HandlerResponse response;
        final private String state;
        final public ContactPublic contact;

        private ContactRequest(IncomingCommandManager.HandlerResponse response, String state, ContactPublic contact) {
            this.response = response;
            this.state = state;
            this.contact = contact;
        }
    }

    public ContactRequestCommandHandler(UserData userData) {
        super(userData, ContactRequestCommand.COMMAND_NAME);
        this.contactStore = userData.getContactStore();
    }

    public void setListener(IListener listener) {
        this.listener = listener;
    }

    @Override
    public IListener getListener() {
        return listener;
    }

    @Override
    protected boolean handle(JSONObject command, IncomingCommandManager.HandlerResponse response) throws Exception {
        if (listener == null) {
            response.setRetryLater();
            return true;
        }

        String state = command.getString(ContactRequestCommand.STATE);
        String id = command.getString(Constants.SENDER_ID_KEY);
        if (id.equals(""))
            throw new Exception("invalid contact id");

        if (state.equals(ContactRequestCommand.FINISH_STATE)) {
            response.setHandled();
            listener.onContactRequestFinished();
            return true;
        }

        String signingKeyBase64 = command.getString(ContactRequestCommand.SIGNING_KEY_KEY);
        CryptoSettings.KeyTypeSettings signingKeySettings = JsonCryptoSettings.keyTypeFromJson(command.getJSONObject(
                ContactRequestCommand.SIGNATURE_SETTINGS_KEY));
        String publicKeyBase64 = command.getString(ContactRequestCommand.PUBLIC_KEY_KEY);
        CryptoSettings.KeyTypeSettings publicKeySettings = JsonCryptoSettings.keyTypeFromJson(command.getJSONObject(
                ContactRequestCommand.PUBLIC_KEY_SETTINGS_KEY));

        byte[] signingKeyRaw = DatatypeConverter.parseBase64Binary(signingKeyBase64);
        PublicKey signingKey = CryptoHelper.publicKeyFromRaw(signingKeyRaw, signingKeySettings.keyType);
        byte[] publicKeyRaw = DatatypeConverter.parseBase64Binary(publicKeyBase64);
        PublicKey publicKey = CryptoHelper.publicKeyFromRaw(publicKeyRaw, publicKeySettings.keyType);

        String serverUser = command.getString(Constants.USER_KEY);
        String server = command.getString(Constants.SERVER_KEY);
        Remote remote = new Remote(serverUser, server);

        PublicKeyItem signingKeyItem = new PublicKeyItem(signingKey, signingKeySettings);
        PublicKeyItem publicKeyItem = new PublicKeyItem(publicKey, publicKeySettings);

        String hash = CryptoHelper.sha256HashHex(id + signingKeyBase64 + publicKeyBase64);
        byte[] signature = DatatypeConverter.parseBase64Binary(command.getString(ContactRequestCommand.SIGNATURE_KEY));
        CryptoSettings.Signature signatureSettings = JsonCryptoSettings.signatureFromJson(command.getJSONObject(
                ContactRequestCommand.SIGNATURE_SETTINGS_KEY));
        ICryptoInterface crypto = context.getCrypto();
        if (!crypto.verifySignature(hash.getBytes(), signature, signingKey, signatureSettings))
            throw new Exception("Contact request with invalid signature!");

        ContactPublic contactPublic = new ContactPublic(contactStore.getContext(), null);
        contactPublic.setId(id);
        contactPublic.addSignatureKey(signingKeyItem);
        contactPublic.getSignatureKeys().setDefault(signingKeyItem);
        contactPublic.addEncryptionKey(publicKeyItem);
        contactPublic.getEncryptionKeys().setDefault(publicKeyItem);
        contactPublic.getRemotes().add(remote);
        contactPublic.getRemotes().setDefault(remote);

        ContactRequest contactRequest = new ContactRequest(response, state, contactPublic);
        contactRequestList.add(contactRequest);
        if (state.equals(ContactRequestCommand.INITIAL_STATE))
            listener.onContactRequest(this, contactRequest);
        else if (state.equals(ContactRequestCommand.REPLY_STATE))
            listener.onContactRequestReply(this, contactRequest);
        else
            throw new Exception("Unknown state: " + state);

        return true;
    }

    public void acceptContactRequest(ContactRequest contactRequest) {
        assert contactRequestList.contains(contactRequest);
        contactRequestList.remove(contactRequest);
        contactRequest.response.setHandled();

        Remote remote = contactRequest.contact.getRemotes().getDefault();
        try {
            contactStore.addContact(contactRequest.contact);
            contactStore.commit();

            userData.getOutgoingCommandQueue().post(ContactRequestCommand.makeReplyRequest(
                    userData.getContext(),
                    userData.getMyself(),
                    userData.getGateway(), contactRequest.contact),
                    remote.getUser(), remote.getServer());
        } catch (Exception e) {
            listener.onError(e);
        }
    }

    public void acceptContactRequestReply(ContactRequest contactRequest) {
        //TODO: verify that we started this request!
        assert contactRequestList.contains(contactRequest);
        contactRequestList.remove(contactRequest);
        contactRequest.response.setHandled();

        Remote remote = contactRequest.contact.getRemotes().getDefault();
        try {
            contactStore.addContact(contactRequest.contact);
            contactStore.commit();

            userData.getOutgoingCommandQueue().post(ContactRequestCommand.makeFinish(
                    userData.getContext(),
                    userData.getMyself(), contactRequest.contact),
                    remote.getUser(), remote.getServer());
        } catch (Exception e) {
           listener.onError(e);
        }
    }
}
