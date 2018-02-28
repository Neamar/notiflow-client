package fr.neamar.notiflow;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Build;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.text.Html;
import android.util.Log;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.nostra13.universalimageloader.core.DisplayImageOptions;
import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.ImageLoaderConfiguration;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import fr.neamar.notiflow.db.NotificationHelper;

public class NotificationService extends FirebaseMessagingService {
    public static final String TAG = "FCM_LISTENER_SERVICE";
    NotificationCompat.Builder builder;
    private NotificationManager mNotificationManager;

    private String getOrDefault(Map<String, String> map, String key, String d) {
        if (map.containsKey(key))
            return map.get(key);
        return d;
    }

    private void initialiseImageLoader() {
        ImageLoader imageLoader = ImageLoader.getInstance();
        if (imageLoader.isInited()) {
            return;
        }

        DisplayImageOptions defaultOptions = new DisplayImageOptions.Builder()
                .cacheInMemory(true)    // defaults to LruMemoryCache
                .cacheOnDisk(true)        // defaults to UnlimitedDiscCache
                .build();

        ImageLoaderConfiguration config = new ImageLoaderConfiguration.Builder(getApplicationContext())
                .defaultDisplayImageOptions(defaultOptions)
                .build();

        imageLoader.init(config);
    }

    @Override
    public void onMessageReceived(RemoteMessage message) {
        String from = message.getFrom();
        Map<String, String> data = message.getData();

        // Post notification of received message.
        Boolean isSpecial = data.containsKey("special");
        Boolean isCleaner = data.containsKey("seen");
        String flow = getOrDefault(data, "flow", "");
        String author = getOrDefault(data, "author", "");
        String content = getOrDefault(data, "content", "");

        if (isCleaner) {
            cleanNotification(flow);
            return;
        }

        if (author.isEmpty()) {
            // Empty author.
            // This can be used to create new kind of messages: leav "author" empty and the app will automatically drop it.
            return;
        }

        if (isSpecial) {
            // Wrap content in <em> tag
            sendNotification(flow, "<b>" + author + "</b>: <em>" + content + "</em>", data);
        } else if (content.startsWith("    ")) {
            sendNotification(flow, "<b>" + author + "</b>: <tt>" + content + "</tt>", data);
        } else {
            sendNotification(flow, "<b>" + author + "</b>: " + content, data);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();

        initialiseImageLoader();

        mNotificationManager = (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    private PendingIntent createClickedIntent(String flow, String flowUrl) {
        Intent intent = new Intent(this, DismissNotification.class);
        intent.setAction("notification_clicked");
        intent.putExtra("flow", flow);

        int requestCode = 0;
        if (flowUrl != null && !flowUrl.isEmpty()) {
            intent.putExtra("flow_url", flowUrl);
            requestCode = flowUrl.hashCode();
        }

        return PendingIntent.getBroadcast(this, requestCode, intent, PendingIntent.FLAG_CANCEL_CURRENT);
    }

    private PendingIntent createDismissedIntent(String flow) {
        Intent intent = new Intent(this, DismissNotification.class);
        intent.setAction("notification_cancelled");
        intent.putExtra("flow", flow);
        int requestCode = flow.hashCode();

        return PendingIntent.getBroadcast(this, requestCode, intent, PendingIntent.FLAG_CANCEL_CURRENT);
    }

    private void cleanNotification(String flow) {
        Log.v(TAG, "Canceling notification (seen on app): " + flow);
        mNotificationManager.cancel(flow, 0);
        NotificationHelper.cleanNotifications(getApplicationContext(), flow);

    }

    // Put the message into a notification and post it.
    private void sendNotification(String flow, String msg, Map<String, String> extras) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        Boolean notifyOwnMessages = prefs.getBoolean("prefNotifyOwnMessages", false);
        Boolean isOwnMessage = getOrDefault(extras, "own", "false").equals("true");

        String notifyType = prefs.getString("prefNotifyType", "all"); // all | mentions | private
        Boolean isMentioned = getOrDefault(extras, "mentioned", "false").equals("true");
        Boolean isPrivate = getOrDefault(extras, "private", "false").equals("true");

        Boolean vibratorOnCooldown = false;

        Log.d(TAG, "New message, type " + notifyType + ", mentioned: " + isMentioned + ", private: " + isPrivate);

        Set<String> mutedFlows = prefs.getStringSet("mutedFlows", new HashSet<String>());
        if (isOwnMessage && !notifyOwnMessages) {
            Log.i(TAG, "Canceling notification (user sent): " + extras.toString());
            mNotificationManager.cancel(flow, 0);
            NotificationHelper.cleanNotifications(getApplicationContext(), flow);
            return;

        } else if (notifyType.equals("mentions") && !isMentioned && !isPrivate) {
            Log.i(TAG, "Skipping message (not mentioned): " + extras.toString());
            return;

        } else if (notifyType.equals("private") && !isPrivate) {
            Log.i(TAG, "Skipping message (not private): " + extras.toString());
            return;
        } else if (mutedFlows.contains(flow)) {
            Log.i(TAG, "Skipping message (muted flow): " + extras.toString());
            return;
        }

        Date lastNotification = NotificationHelper.getLastNotificationDate(getApplicationContext(), flow);
        NotificationHelper.addNotification(getApplicationContext(), flow, msg);

        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(this, flow);

        Boolean silentMode = prefs.getBoolean("prefNotifySilent", false);

        if (silentMode) {
            Log.i(TAG, "Silent mode, no vibration.");
            vibratorOnCooldown = true;
            mBuilder.setDefaults(NotificationCompat.DEFAULT_LIGHTS);
        } else {
            Date now = new Date();
            Long timeSinceLastNotification = now.getTime() - lastNotification.getTime();

            Integer frequency = Integer.parseInt(prefs.getString("prefNotifyVibrationFrequency", "15")) * 1000;
            Boolean notifyWhenActive = prefs.getBoolean("prefNotifyWhenActive", false);
            Boolean isActive = getOrDefault(extras, "active", "false").equals("true");

            if (timeSinceLastNotification < frequency) {
                Log.i(TAG, "Skipping vibration -- cooldown in effect");
                vibratorOnCooldown = true;

            } else if (isActive && !notifyWhenActive) {
                Log.i(TAG, "Skipping vibration -- user already active");
                vibratorOnCooldown = true;

            } else {
                mBuilder.setDefaults(NotificationCompat.DEFAULT_VIBRATE | NotificationCompat.DEFAULT_LIGHTS);
            }
        }

        ArrayList<String> prevMessages = NotificationHelper.getNotifications(getApplicationContext(), flow);
        Integer pendingCount = prevMessages.size();

        if (pendingCount == 1) {
            // Only one notification : display using BigTextStyle for multiline.
            NotificationCompat.BigTextStyle style = new NotificationCompat.BigTextStyle()
                    .bigText(Html.fromHtml(msg));

            mBuilder.setStyle(style);
        } else {
            // More than one notification: use inbox style, displaying up to 5 messages
            NotificationCompat.InboxStyle style = new NotificationCompat.InboxStyle();

            for (int i = 0; i < pendingCount; i++) {
                style.addLine(Html.fromHtml(prevMessages.get(i)));
            }

            mBuilder
                    .setStyle(style)
                    .setContentInfo(Integer.toString(pendingCount))
                    .setNumber(pendingCount);
        }

        // Set large icon, which gets used for wearable background as well
        String avatar = getOrDefault(extras, "avatar", "");
        if (!avatar.equals("")) {

            String sizeExpr = "(/\\d+/?)$";
            Boolean isCloudFront = avatar.contains("cloudfront");
            Boolean hasSize = avatar.matches(".*" + sizeExpr);

            if (isCloudFront) {
                if (!hasSize) {
                    avatar += "/400";
                } else {
                    avatar = avatar.replaceFirst(sizeExpr, "/400");
                }
            }

            ImageLoader imageLoader = ImageLoader.getInstance();
            Bitmap image = imageLoader.loadImageSync(avatar);

            // scale for notification tray
            int height = (int) getResources().getDimension(android.R.dimen.notification_large_icon_height);
            int width = (int) getResources().getDimension(android.R.dimen.notification_large_icon_width);
            Bitmap scaledImage = Bitmap.createScaledBitmap(image, width, height, false);

            mBuilder.setLargeIcon(scaledImage);
        }

        // Increase priority only for mentions and 1-1 conversations
        if (isMentioned || isPrivate) {
            mBuilder.setPriority(NotificationCompat.PRIORITY_HIGH);
        }

        // Retrieve color
        // Default to 0x7BD3FB
        int color = Integer.parseInt(getOrDefault(extras, "color", "8115195"));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(flow,
                    String.format(getString(R.string.messages_from), flow),
                    NotificationManager.IMPORTANCE_DEFAULT);
            mNotificationManager.createNotificationChannel(channel);

            NotificationChannel cooldownChannel = new NotificationChannel(flow + "_cooldown",
                    String.format(getString(R.string.messages_from_cooldown), flow),
                    NotificationManager.IMPORTANCE_LOW);
            cooldownChannel.setVibrationPattern(null);
            cooldownChannel.setSound(null, null);
            mNotificationManager.createNotificationChannel(cooldownChannel);

            if(vibratorOnCooldown) {
                Log.i(TAG, "Using cooldown channel.");
                mBuilder.setChannelId(cooldownChannel.getId());
            }
            else {
                mBuilder.setChannelId(channel.getId());
            }
        }

        Notification notification = mBuilder
                .setSmallIcon(R.drawable.notification)
                .setColor(color)
                .setContentTitle(flow)
                .setContentText(Html.fromHtml(msg))
                .setAutoCancel(true)
                .setContentIntent(createClickedIntent(flow, getOrDefault(extras, "flow_url", "")))
                .setDeleteIntent(createDismissedIntent(flow))
                .setTicker(Html.fromHtml(msg))
                .setCategory(NotificationCompat.CATEGORY_SOCIAL)
                .setChannelId(flow)
                .build();

        mNotificationManager.notify(flow, 0, notification);

        Log.i(TAG, "Displaying message: " + extras.toString());

        // Add flow to list of currently known flows
        Set<String> knownFlows = prefs.getStringSet("knownFlows", new HashSet<String>());
        if (!knownFlows.contains(flow)) {
            knownFlows.add(flow);
            Log.i(TAG, "Added " + flow + " to known flows.");
            prefs.edit().putStringSet("knownFlows", knownFlows).apply();
        }
    }
}