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

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;
import android.widget.Toast;

import org.jivesoftware.smack.packet.Packet;
import org.kontalk.R;
import org.kontalk.authenticator.Authenticator;
import org.kontalk.crypto.PersonalKey;
import org.kontalk.service.KeyPairGeneratorService;
import org.kontalk.service.KeyPairGeneratorService.KeyGeneratorReceiver;
import org.kontalk.service.KeyPairGeneratorService.PersonalKeyRunnable;
import org.kontalk.util.MessageUtils;
import org.spongycastle.openpgp.PGPException;

import java.io.IOException;
import java.security.NoSuchProviderException;
import java.security.SignatureException;
import java.security.cert.CertificateException;


/** Listener and manager for a key pair regeneration cycle. */
class RegenerateKeyPairListener extends RegisterKeyPairListener {
    private BroadcastReceiver mKeyReceiver;

    public RegenerateKeyPairListener(MessageCenterService instance) {
        super(instance, null);
        mPassphrase = getApplication().getCachedPassphrase();
    }

    public void run() throws CertificateException, SignatureException,
            PGPException, IOException, NoSuchProviderException {
        super.run();

        setupKeyPairReceiver();

        // begin key pair generation
        generateKeyPair();
    }

    public void abort() {
        super.abort();

        if (mKeyReceiver != null) {
            unregisterReceiver(mKeyReceiver);
            mKeyReceiver = null;
        }
    }

    private void generateKeyPair() {
        Context ctx = getApplication();
        Intent i = new Intent(ctx, KeyPairGeneratorService.class);
        i.setAction(KeyPairGeneratorService.ACTION_GENERATE);
        i.putExtra(KeyPairGeneratorService.EXTRA_FOREGROUND, true);
        ctx.startService(i);
    }

    private void setupKeyPairReceiver() {
        if (mKeyReceiver == null) {

            PersonalKeyRunnable action = new PersonalKeyRunnable() {
                public void run(PersonalKey key) {
                    Log.d(MessageCenterService.TAG, "keypair generation complete.");
                    // unregister the broadcast receiver
                    unregisterReceiver(mKeyReceiver);
                    mKeyReceiver = null;

                    // store the key
                    try {
                        Context context = getContext();
                        AccountManager am = (AccountManager) context.getSystemService(Context.ACCOUNT_SERVICE);
                        Account acc = Authenticator.getDefaultAccount(am);
                        String name = am.getUserData(acc, Authenticator.DATA_NAME);

                        String userId = MessageUtils.sha1(acc.name);
                        mKeyRing = key.storeNetwork(userId, getServer().getNetwork(), name,
                            // TODO should we ask passphrase to the user?
                            getApplication().getCachedPassphrase());

                        // listen for connection events
                        setupConnectedReceiver();
                        // request connection status
                        MessageCenterService.requestConnectionStatus(context);

                        // CONNECTED listener will do the rest
                    }
                    catch (Exception e) {
                        // TODO notify user
                        Log.v(MessageCenterService.TAG, "error saving key", e);
                    }
                }
            };

            mKeyReceiver = new KeyGeneratorReceiver(getIdleHandler(), action);

            IntentFilter filter = new IntentFilter(KeyPairGeneratorService.ACTION_GENERATE);
            registerReceiver(mKeyReceiver, filter);
        }
    }

    @Override
    public void processPacket(Packet packet) {
        super.processPacket(packet);

        // we are done here
        endKeyPairRegeneration();
    }

    @Override
    protected void finish() {
        runOnUiThread(new Runnable() {
            public void run() {
                Toast.makeText(getApplication(),
                    R.string.msg_gen_keypair_complete,
                    Toast.LENGTH_LONG).show();
            }
        });
    }

}
