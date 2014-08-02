/*
 * Kontalk Android client
 * Copyright (C) 2014 Kontalk Devteam <devteam@kontalk.org>

 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.kontalk.service.msgcenter;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import android.widget.Toast;

import org.jivesoftware.smack.filter.PacketIDFilter;
import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smack.packet.Registration;
import org.jivesoftware.smackx.xdata.Form;
import org.jivesoftware.smackx.xdata.FormField;
import org.jivesoftware.smackx.xdata.packet.DataForm;
import org.kontalk.R;
import org.kontalk.authenticator.Authenticator;
import org.kontalk.crypto.PGP.PGPKeyPairRing;
import org.kontalk.crypto.PersonalKey;
import org.kontalk.crypto.X509Bridge;
import org.spongycastle.openpgp.PGPException;
import org.spongycastle.openpgp.PGPPublicKey;

import java.io.IOException;
import java.security.NoSuchProviderException;
import java.security.SignatureException;
import java.security.cert.CertificateException;
import java.util.List;

import static org.kontalk.service.msgcenter.MessageCenterService.ACTION_CONNECTED;


/** Abstract listener and manager for a key pair registration cycle. */
abstract class RegisterKeyPairListener extends MessageCenterPacketListener {
    private BroadcastReceiver mConnReceiver;
    protected PGPKeyPairRing mKeyRing;
    protected PGPPublicKey mRevoked;

    /** This is the passphrase that will be used for the new key. */
    protected String mPassphrase;

    public RegisterKeyPairListener(MessageCenterService instance, String passphrase) {
        super(instance);
        mPassphrase = passphrase;
    }

    public void run() throws CertificateException, SignatureException,
            PGPException, IOException, NoSuchProviderException {
        revokeCurrentKey();
        setupConnectedReceiver();
    }

    public void abort() {
        if (mConnReceiver != null) {
            unregisterReceiver(mConnReceiver);
            mConnReceiver = null;
        }
    }

    private Packet prepareKeyPacket() {
        if (mKeyRing != null) {
            try {
                String publicKey = Base64.encodeToString(mKeyRing.publicKey.getEncoded(), Base64.NO_WRAP);

                Registration iq = new Registration();
                iq.setType(IQ.Type.SET);
                iq.setTo(getConnection().getServiceName());
                Form form = new Form(Form.TYPE_SUBMIT);

                // form type: register#key
                FormField type = new FormField("FORM_TYPE");
                type.setType(FormField.TYPE_HIDDEN);
                type.addValue("http://kontalk.org/protocol/register#key");
                form.addField(type);

                // new (to-be-signed) public key
                FormField fieldKey = new FormField("publickey");
                fieldKey.setLabel("Public key");
                fieldKey.setType(FormField.TYPE_TEXT_SINGLE);
                fieldKey.addValue(publicKey);
                form.addField(fieldKey);

                // old (revoked) public key
                if (mRevoked != null) {
                    String revokedKey = Base64.encodeToString(mRevoked.getEncoded(), Base64.NO_WRAP);

                    FormField fieldRevoked = new FormField("revoked");
                    fieldRevoked.setLabel("Revoked public key");
                    fieldRevoked.setType(FormField.TYPE_TEXT_SINGLE);
                    fieldRevoked.addValue(revokedKey);
                    form.addField(fieldRevoked);
                }

                iq.addExtension(form.getDataFormToSend());
                return iq;
            }
            catch (IOException e) {
                Log.v(MessageCenterService.TAG, "error encoding key", e);
            }
        }

        return null;
    }

    protected void setupConnectedReceiver() {
        if (mConnReceiver == null) {
            mConnReceiver = new BroadcastReceiver() {
                public void onReceive(Context context, Intent intent) {
                    // unregister the broadcast receiver
                    unregisterReceiver(mConnReceiver);
                    mConnReceiver = null;

                    // prepare public key packet
                    Packet iq = prepareKeyPacket();

                    if (iq != null) {

                        // setup packet filter for response
                        PacketIDFilter filter = new PacketIDFilter(iq.getPacketID());
                        getConnection().addPacketListener(RegisterKeyPairListener.this, filter);

                        // send the key out
                        sendPacket(iq);

                        // now wait for a response
                    }

                    // TODO else?
                }
            };

            IntentFilter filter = new IntentFilter(ACTION_CONNECTED);
            registerReceiver(mConnReceiver, filter);
        }
    }

    /** We do this here so if something goes wrong the old key is still valid. */
    private void revokeCurrentKey()
            throws CertificateException, PGPException, IOException, SignatureException {

        PersonalKey oldKey = getApplication().getPersonalKey();
        if (oldKey != null)
            mRevoked = oldKey.revoke(false);
    }

    @Override
    public void processPacket(Packet packet) {
        IQ iq = (IQ) packet;
        if (iq.getType() == IQ.Type.RESULT) {
            DataForm response = (DataForm) iq.getExtension("x", "jabber:x:data");
            if (response != null) {
                String publicKey = null;

                // ok! message will be sent
                List<FormField> fields = response.getFields();
                for (FormField field : fields) {
                    if ("publickey".equals(field.getVariable())) {
                        publicKey = field.getValues().get(0);
                        break;
                    }
                }

                if (!TextUtils.isEmpty(publicKey)) {
                    byte[] publicKeyData;
                    byte[] privateKeyData;
                    byte[] bridgeCertData;
                    try {
                        publicKeyData = Base64.decode(publicKey, Base64.DEFAULT);
                        privateKeyData = mKeyRing.secretKey.getEncoded();

                        // TODO subjectAltName?
                        bridgeCertData = X509Bridge.createCertificate(publicKeyData,
                            mKeyRing.secretKey.getSecretKey(), mPassphrase, null).getEncoded();
                    }
                    catch (Exception e) {
                        Log.e(MessageCenterService.TAG, "error decoding key data", e);
                        publicKeyData = null;
                        privateKeyData = null;
                        bridgeCertData = null;
                    }

                    if (publicKeyData != null && privateKeyData != null && bridgeCertData != null) {

                        // store key data in AccountManager
                        Authenticator.setDefaultPersonalKey(getContext(),
                            publicKeyData, privateKeyData, bridgeCertData,
                            mPassphrase);
                        // invalidate cached personal key
                        getApplication().invalidatePersonalKey();

                        finish();

                        // restart message center
                        MessageCenterService.restart(getApplication());
                    }

                    // TODO else?
                }
            }
        }
    }

    protected abstract void finish();

}
