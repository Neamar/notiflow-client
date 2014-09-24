/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package fr.neamar.notiflow;

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.text.Html;
import android.util.Log;

import com.google.android.gms.gcm.GoogleCloudMessaging;

import java.util.ArrayList;
import java.util.Date;

/**
 * This {@code IntentService} does the actual handling of the GCM message.
 * {@code GcmBroadcastReceiver} (a {@code WakefulBroadcastReceiver}) holds a
 * partial wake lock for this service while the service does its work. When the
 * service is finished, it calls {@code completeWakefulIntent()} to release the
 * wake lock.
 */
public class GcmIntentService extends IntentService {
	public static final String TAG = "Notiflow";
	NotificationCompat.Builder builder;
	private NotificationManager mNotificationManager;

	public GcmIntentService() {
		super("GcmIntentService");
	}

	@Override
	protected void onHandleIntent(Intent intent) {
		Bundle extras = intent.getExtras();
		GoogleCloudMessaging gcm = GoogleCloudMessaging.getInstance(this);
		// The getMessageType() intent parameter must be the intent you received
		// in your BroadcastReceiver.
		String messageType = gcm.getMessageType(intent);

		if (!extras.isEmpty()) { // has effect of unparcelling Bundle
			/*
			 * Filter messages based on message type. Since it is likely that
			 * GCM will be extended in the future with new message types, just
			 * ignore any message types you're not interested in, or that you
			 * don't recognize.
			 */
			if (GoogleCloudMessaging.MESSAGE_TYPE_SEND_ERROR.equals(messageType)) {
				sendNotification("Notiflow", "Send error: " + extras.toString(), extras);
			} else if (GoogleCloudMessaging.MESSAGE_TYPE_DELETED.equals(messageType)) {
				sendNotification("Notiflow", "Deleted messages on server: " + extras.toString(), extras);
				// If it's a regular GCM message, do some work.
			} else if (GoogleCloudMessaging.MESSAGE_TYPE_MESSAGE.equals(messageType)) {
				// Post notification of received message.
				if (extras.containsKey("special")) {
					// Wrap content in <em> tag
					sendNotification(extras.getString("flow"), "<b>" + extras.getString("author", "???") + "</b>: <em>" + extras.getString("content") + "</em>", extras);
				} else if (extras.getString("content").startsWith("    ")) {
					sendNotification(extras.getString("flow"), "<b>" + extras.getString("author", "???") + "</b>: <tt>" + extras.getString("content") + "</tt>", extras);
				} else {
					sendNotification(extras.getString("flow"), "<b>" + extras.getString("author", "???") + "</b>: " + extras.getString("content"), extras);
				}
			}
		}
		// Release the wake lock provided by the WakefulBroadcastReceiver.
		GcmBroadcastReceiver.completeWakefulIntent(intent);
	}

	// Put the message into a notification and post it.
	// This is just one simple example of what you might choose to do with
	// a GCM message.
	private void sendNotification(String flow, String msg, Bundle extras) {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

		if (!prefs.getBoolean("prefNotifyOwnMessages", false) && extras.getString("own", "false").equals("true")) {
			Log.i(TAG, "Skipping message (user sent): " + extras.toString());
			mNotificationManager = (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE);
			mNotificationManager.cancel(NotificationHelper.getFlowId(extras.getString("flow")));
			NotificationHelper.cleanNotifications(extras.getString("flow"));
			return;
		}

		mNotificationManager = (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE);
		Intent intent = new Intent(this, DismissNotification.class);
		intent.setAction("notification_clicked");
		intent.putExtra("flow", flow);
		if (extras.containsKey("flow_url")) {
			intent.putExtra("flow_url", extras.getString("flow_url"));
		}

		PendingIntent clickedIntent = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT);

		intent = new Intent(this, DismissNotification.class);
		intent.setAction("notification_cancelled");
		intent.putExtra("flow", flow);
		PendingIntent dismissIntent = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT);

		NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(this);
		mBuilder.setSmallIcon(R.drawable.notification);
		mBuilder.setContentTitle(flow);

		// Retrieve last modification date for this flow
		Date lastNotification = NotificationHelper.getLastNotificationDate(flow);
		// Overwrite previous messages
		NotificationHelper.addNotification(flow, msg);

		// We have a pending notification. We'll need to update it.
		if (NotificationHelper.getNotifications(flow).size() > 1) {
			NotificationCompat.InboxStyle style = new NotificationCompat.InboxStyle();

			// Read messages
			ArrayList<String> prevMessages = NotificationHelper.getNotifications(flow);

			for (int i = 0; i < Math.min(prevMessages.size(), 5); i++) {
				style.addLine(Html.fromHtml(prevMessages.get(i)));
			}

			mBuilder.setStyle(style);
			mBuilder.setContentInfo(Integer.toString(NotificationHelper.getNotifications(flow).size()) + " messages");
			mBuilder.setNumber(NotificationHelper.getNotifications(flow).size());
		}
		else {
			mBuilder.setStyle(new NotificationCompat.BigTextStyle().bigText(Html.fromHtml(msg)));
		}

		mBuilder.setContentText(Html.fromHtml(msg));
		mBuilder.setAutoCancel(true);
		mBuilder.setContentIntent(clickedIntent);
		mBuilder.setDeleteIntent(dismissIntent);
		mBuilder.setTicker(Html.fromHtml(msg));

		if (!prefs.getBoolean("prefNotifySilent", false)) {
			Date now = new Date();
			if (now.getTime() - lastNotification.getTime() > Integer.parseInt(prefs.getString("prefNotifyVibrationFrequency", "15")) * 1000) {
				if (!prefs.getBoolean("prefNotifyWhenActive", false) && extras.getString("active", "false").equals("true")) {
					Log.i(TAG, "Skipping vibration -- user already active");
				} else {
					// Make it vibrate!
					mBuilder.setDefaults(Notification.DEFAULT_VIBRATE | Notification.DEFAULT_LIGHTS);
				}
			} else {
				Log.i(TAG, "Skipping vibration -- cooldown in effect");
			}
		}

		Notification notification = mBuilder.build();

		mNotificationManager.notify(NotificationHelper.getFlowId(flow), notification);
		Log.i(TAG, "Displaying message: " + extras.toString());
	}
}
