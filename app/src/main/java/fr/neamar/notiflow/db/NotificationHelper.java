package fr.neamar.notiflow.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.Date;

public class NotificationHelper {
    public static class PreviousMessage {
        public String author;
        public String message;
        public String avatar;
        public long date;

        public PreviousMessage(String author, String message, String avatar, long date) {
            this.author = author;
            this.message = message;
            this.avatar = avatar;
            this.date = date;
        }
    }

    private static SQLiteDatabase getDatabase(Context context) {
        DB db = new DB(context);
        return db.getReadableDatabase();
    }

    /**
     * Add a new notification
     *
     * @param flow
     * @param msg
     */
    public static void addNotification(Context context, String flow, String author, String avatar, String msg) {
        SQLiteDatabase db = getDatabase(context);

        ContentValues values = new ContentValues();
        values.put("flow", flow);
        values.put("message", author + "||" + avatar + "||" + msg);
        values.put("date", new Date().getTime());
        db.insert("notifications", null, values);
        db.close();
    }

    /**
     * Get last notifications
     *
     * @param flow
     * @return
     */
    public static ArrayList<PreviousMessage> getNotifications(Context context, String flow) {
        SQLiteDatabase db = getDatabase(context);

        ArrayList<PreviousMessage> flowNotifications = new ArrayList<>();

        // Cursor query (String table, String[] columns,
        // String selection, String[] selectionArgs, String groupBy, String
        // having, String orderBy, String limit)
        Cursor cursor = db.query("notifications", new String[]{"message", "date"}, "flow = ?", new String[]{flow}, null, null, "_id DESC", "101");

        cursor.moveToFirst();
        while (!cursor.isAfterLast()) {
            String message = cursor.getString(0);
            String messageAuthor = "?";
            String messageAvatar = "";
            if (message.contains("||")) {
                messageAuthor = message.substring(0, message.indexOf("||"));
                message = message.substring(message.indexOf("||") + 2);
                messageAvatar = message.substring(0, message.indexOf("||"));
                message = message.substring(message.indexOf("||") + 2);
            }
            flowNotifications.add(new PreviousMessage(messageAuthor, message, messageAvatar, cursor.getLong(1)));

            cursor.moveToNext();
        }
        cursor.close();
        db.close();

        return flowNotifications;
    }

    public static Date getLastNotificationDate(Context context, String flow) {
        SQLiteDatabase db = getDatabase(context);
        Long lastTimestamp = (long) 0;

        Cursor cursor = db.query("notifications", new String[]{"date"}, "flow = ?", new String[]{flow}, null, null, "_id DESC", "1");

        cursor.moveToFirst();

        if (!cursor.isAfterLast()) {
            lastTimestamp = cursor.getLong(0);
        }

        cursor.close();
        db.close();

        return new Date(lastTimestamp);
    }

    /**
     * Remove all stored notifications from this flow.
     *
     * @param flow
     */
    public static void cleanNotifications(Context context, String flow) {
        SQLiteDatabase db = getDatabase(context);

        db.delete("notifications", "flow = ?", new String[]{flow});
        db.close();
    }


    /**
     * Return the number of rows ever inserted into the notification table
     */
    public static long getTotalCreatedRows(Context context) {
        SQLiteDatabase db = getDatabase(context);

        String query = "SELECT seq FROM SQLITE_SEQUENCE WHERE name = ?";
        Cursor cursor = db.rawQuery(query, new String[]{"notifications"});

        cursor.moveToFirst();

        long notificationCount = 0;
        if (!cursor.isAfterLast()) {
            notificationCount = cursor.getLong(cursor.getColumnIndex("seq"));
        }
        cursor.close();
        db.close();

        return notificationCount;

    }
}
