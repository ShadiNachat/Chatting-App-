package org.kontalk.service.gcm;

import java.util.Random;

import org.kontalk.Kontalk;
import org.kontalk.service.msgcenter.MessageCenterService;
import org.kontalk.service.msgcenter.PushServiceManager;
import org.kontalk.util.Preferences;

import android.app.IntentService;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import com.google.android.gms.gcm.GoogleCloudMessaging;


/**
 * Intent service for GCM broadcasts.
 * @author Daniele Ricci
 */
public class GcmIntentService extends IntentService {
    private static final String TAG = Kontalk.TAG;

    /** GCM message received from server. */
    private static final String ACTION_CHECK_MESSAGES = "org.kontalk.CHECK_MESSAGES";

    /** Internal action for handling back-off. */
    private static final String ACTION_RETRY = "org.kontalk.gcm.RETRY";

    // token used to check intent origin
    private static final String TOKEN =
            Long.toBinaryString(new Random().nextLong());
    private static final String EXTRA_TOKEN = "token";

    public GcmIntentService() {
        super("GcmIntentService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {

        if (ACTION_RETRY.equals(intent.getAction())) {

            String token = intent.getStringExtra(EXTRA_TOKEN);
            if (!TOKEN.equals(token)) {
                // make sure intent was generated by this class, not by a
                // malicious app.
                Log.e(TAG, "Received invalid token: " + token);
                return;
            }

            PushServiceManager.getInstance(this).retry();
        }

        else {
            Bundle extras = intent.getExtras();
            GoogleCloudMessaging gcm = GoogleCloudMessaging.getInstance(this);
            // The getMessageType() intent parameter must be the intent you received
            // in your BroadcastReceiver.
            String messageType = gcm.getMessageType(intent);

            if (!extras.isEmpty()) {  // has effect of unparcelling Bundle

                if (GoogleCloudMessaging.
                        MESSAGE_TYPE_MESSAGE.equals(messageType)) {

                    String dataAction = intent.getStringExtra("action");
                    Log.v(TAG, "cloud message received: " + dataAction);

                    // new messages - start message center
                    if (ACTION_CHECK_MESSAGES.equals(dataAction)) {
                        // remember we just received a push notifications
                        // this means that there are really messages waiting for us
                        Preferences.setLastPushNotification(this,
                            System.currentTimeMillis());

                        // start message center
                        MessageCenterService.start(getApplicationContext());
                    }

                }
            }
        }

        // Release the wake lock provided by the WakefulBroadcastReceiver
        GcmBroadcastReceiver.completeWakefulIntent(intent);
    }

    static PendingIntent getRetryIntent(Context context) {
        Intent retryIntent = new Intent(context.getApplicationContext(), GcmIntentService.class);
        retryIntent.setAction(ACTION_RETRY);
        retryIntent.putExtra(EXTRA_TOKEN, TOKEN);

        return PendingIntent.getService(context,
                0, retryIntent, 0);

    }

}
