package fr.neamar.notiflow.db;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

class DB extends SQLiteOpenHelper {

    private final static int DB_VERSION = 1;
    private final static String DB_NAME = "notiflow.s3db";
    private final Context context;

    public DB(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
        this.context = context;
    }

    @Override
    public void onCreate(SQLiteDatabase database) {
        database.execSQL("CREATE TABLE notifications ( _id INTEGER PRIMARY KEY AUTOINCREMENT, flow TEXT NOT NULL, message TEXT NOT NULL, date INTEGER NOT NULL)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase arg0, int arg1, int arg2) {
        // See
        // http://www.drdobbs.com/database/using-sqlite-on-android/232900584?pgno=2
    }
}