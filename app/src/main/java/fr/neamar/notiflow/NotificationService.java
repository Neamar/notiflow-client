package fr.neamar.notiflow;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.preference.PreferenceManager;
import android.text.Html;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.Person;
import androidx.core.graphics.drawable.IconCompat;

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
        boolean isSpecial = data.containsKey("special");
        boolean isCleaner = data.containsKey("seen");
        String flow = getOrDefault(data, "flow", "");
        String author = getOrDefault(data, "author", "");
        String content = getOrDefault(data, "content", "");

        if (isCleaner) {
            cleanNotification(flow);
            return;
        }

        if (author.isEmpty()) {
            // Empty author.
            // This can be used to create new kind of messages: leave "author" empty and the app will automatically drop it.
            return;
        }

        if (isSpecial) {
            // Wrap content in <em> tag
            sendNotification(flow, author, content, data);
        } else if (content.startsWith("    ")) {
            sendNotification(flow, author, content, data);
        } else {
            sendNotification(flow, author, content, data);
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
    private void sendNotification(String flow, String author, String msg, Map<String, String> extras) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        boolean notifyOwnMessages = prefs.getBoolean("prefNotifyOwnMessages", false);
        boolean isOwnMessage = getOrDefault(extras, "own", "false").equals("true");

        String notifyType = prefs.getString("prefNotifyType", "all"); // all | mentions | private
        boolean isMentioned = getOrDefault(extras, "mentioned", "false").equals("true");
        boolean isPrivate = getOrDefault(extras, "private", "false").equals("true");

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
        NotificationHelper.addNotification(getApplicationContext(), flow, author, msg);

        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(this, flow);

        boolean silentMode = prefs.getBoolean("prefNotifySilent", false);

        if (silentMode) {
            Log.i(TAG, "Silent mode, no vibration.");
            mBuilder.setDefaults(NotificationCompat.DEFAULT_LIGHTS);
        } else {
            Date now = new Date();
            Long timeSinceLastNotification = now.getTime() - lastNotification.getTime();

            Integer frequency = Integer.parseInt(prefs.getString("prefNotifyVibrationFrequency", "15")) * 1000;
            boolean notifyWhenActive = prefs.getBoolean("prefNotifyWhenActive", false);
            boolean isActive = getOrDefault(extras, "active", "false").equals("true");

            if (timeSinceLastNotification < frequency) {
                Log.i(TAG, "Skipping vibration -- cooldown in effect");

            } else if (isActive && !notifyWhenActive) {
                Log.i(TAG, "Skipping vibration -- user already active");

            } else {
                mBuilder.setDefaults(NotificationCompat.DEFAULT_VIBRATE | NotificationCompat.DEFAULT_LIGHTS);
            }
        }

        ArrayList<NotificationHelper.PreviousMessage> prevMessages = NotificationHelper.getNotifications(getApplicationContext(), flow);
        int pendingCount = prevMessages.size();

        NotificationCompat.MessagingStyle style = new NotificationCompat.MessagingStyle(flow);
        style.setConversationTitle(flow);

        for (int i = pendingCount - 1; i >= 0; i--) {
            NotificationHelper.PreviousMessage previousMessage = prevMessages.get(i);
            Person.Builder user = new Person.Builder().setName(previousMessage.author);

            Bitmap avatar = getAvatar(getOrDefault(extras, "avatar", ""));
            if(avatar != null) {
                user.setIcon(IconCompat.createWithBitmap(avatar));
            }
            style.addMessage(previousMessage.message, previousMessage.date, user.build());
        }

        mBuilder
                .setStyle(style)
                .setContentInfo(Integer.toString(pendingCount))
                .setNumber(pendingCount);

        // Increase priority only for mentions and 1-1 conversations
        if (isMentioned || isPrivate) {
            mBuilder.setPriority(NotificationCompat.PRIORITY_HIGH);
        }

        // Retrieve color
        // Default to 0x7BD3FB
        int color = Integer.parseInt(getOrDefault(extras, "color", "8115195"));

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
                .setOnlyAlertOnce(true)
                .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(flow,
                    String.format(getString(R.string.messages_from), flow),
                    NotificationManager.IMPORTANCE_DEFAULT);
            mNotificationManager.createNotificationChannel(channel);
        }
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

    private Bitmap getAvatar(String avatar) {
        if (avatar.equals("")) {
            return null;
        }

        String sizeExpr = "(/\\d+/?)$";
        boolean isCloudFront = avatar.contains("cloudfront");
        boolean hasSize = avatar.matches(".*" + sizeExpr);

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

        return scaledImage;
    }
}