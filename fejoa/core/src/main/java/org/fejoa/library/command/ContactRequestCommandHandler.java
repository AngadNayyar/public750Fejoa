/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.command;

import org.fejoa.library.crypto.CryptoHelper;
import org.fejoa.library.crypto.CryptoSettings;
import org.fejoa.library.crypto.ICryptoInterface;
import org.fejoa.library.crypto.JsonCryptoSettings;
import org.fejoa.library.*;
import org.fejoa.library.support.ProtocolHashHelper;
import org.json.JSONObject;

import javax.xml.bind.DatatypeConverter;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.fejoa.library.command.ContactRequestCommand.REMOTE_ID_KEY;


public class ContactRequestCommandHandler extends EnvelopeCommandHandler {
    final private ContactStore contactStore;
    private IListener listener;
    final private List<ContactRequest> contactRequestList = new ArrayList<>();

    public interface IListener extends IncomingCommandManager.IListener {
        void onContactRequest(ContactRequest contactRequest);
        void onContactRequestReply(ContactRequest contactRequest);
        void onContactRequestFinished(ContactPublic contactPublic);
    }

    static public class AutoAccept implements IListener {
        @Override
        public void onContactRequest(ContactRequest contactRequest) {
            contactRequest.accept();
        }

        @Override
        public void onContactRequestReply(ContactRequest contactRequest) {
            contactRequest.accept();
        }

        @Override
        public void onContactRequestFinished(ContactPublic contactPublic) {

        }

        @Override
        public void onError(Exception exception) {

        }
    }

    public class ContactRequest {
        final private IncomingCommandManager.HandlerResponse response;
        final private String state;
        final public ContactPublic contact;

        private ContactRequest(IncomingCommandManager.HandlerResponse response, String state, ContactPublic contact) {
            this.response = response;
            this.state = state;
            this.contact = contact;
        }

        public void accept() {
            if (state.equals(ContactRequestCommand.INITIAL_STATE))
                acceptContactRequest(this);
            else if (state.equals(ContactRequestCommand.REPLY_STATE))
                acceptContactRequestReply(this);
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
            ContactPublic contactPublic = contactStore.getContactList().get(id);
            listener.onContactRequestFinished(contactPublic);
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

        String remoteId = command.getString(REMOTE_ID_KEY);
        String serverUser = command.getString(Constants.USER_KEY);
        String server = command.getString(Constants.SERVER_KEY);
        Remote remote = new Remote(remoteId, serverUser, server);

        PublicKeyItem signingKeyItem = new PublicKeyItem(signingKey, signingKeySettings);
        PublicKeyItem publicKeyItem = new PublicKeyItem(publicKey, publicKeySettings);

        String hash = ProtocolHashHelper.hashHex(id + signingKeyBase64 + publicKeyBase64, command);
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
        contactPublic.getRemotes().add(remote, true);

        ContactRequest contactRequest = new ContactRequest(response, state, contactPublic);
        contactRequestList.add(contactRequest);
        if (state.equals(ContactRequestCommand.INITIAL_STATE))
            listener.onContactRequest(contactRequest);
        else if (state.equals(ContactRequestCommand.REPLY_STATE))
            listener.onContactRequestReply(contactRequest);
        else
            throw new Exception("Unknown state: " + state);

        return true;
    }

    public void acceptContactRequest(ContactRequest contactRequest) {
        assert contactRequestList.contains(contactRequest);

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

        contactRequestList.remove(contactRequest);
        contactRequest.response.setHandled();
    }

    private ContactPublic getRequestedContact(ContactPublic contactPublic) {
        Remote remote = contactPublic.getRemotes().getDefault();
        Collection<ContactPublic> requestedContacts = userData.getContactStore().getRequestedContacts().getEntries();
        for (ContactPublic requestedContact : requestedContacts) {
            Remote requestedRemote = requestedContact.getRemotes().getDefault();
            if (remote.getUser().equals(requestedRemote.getUser())
                    && remote.getServer().equals(requestedRemote.getServer())) {
                return requestedContact;
            }
        }
        return null;
    }

    public void acceptContactRequestReply(ContactRequest contactRequest) {
        assert contactRequestList.contains(contactRequest);

        Remote remote = contactRequest.contact.getRemotes().getDefault();
        ContactPublic requestContact = getRequestedContact(contactRequest.contact);
        if (requestContact == null) {
            Exception exception = new Exception("Contact has not been requested: " + remote.getUser() + "@"
                    + remote.getServer());
            contactRequest.response.setError(exception);
            listener.onError(exception);
            return;
        }

        try {
            contactStore.getRequestedContacts().remove(requestContact.getId());
            contactStore.addContact(contactRequest.contact);
            contactStore.commit();

            userData.getOutgoingCommandQueue().post(ContactRequestCommand.makeFinish(
                    userData.getContext(),
                    userData.getMyself(), contactRequest.contact),
                    remote.getUser(), remote.getServer());
        } catch (Exception e) {
           listener.onError(e);
        }

        contactRequestList.remove(contactRequest);
        contactRequest.response.setHandled();
    }
}
