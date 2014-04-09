package fr.neamar.notiflow;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

public class DismissNotification extends BroadcastReceiver {
	public static final String STORAGE_COLLECTION = "notifier";
	public static final String PROPERTY_NOTIFICATION = "notification";
	public static final String PROPERTY_MESSAGE = "message-";
	
	@Override
	public void onReceive(Context context, Intent intent) {
		
		final SharedPreferences prefs = context.getSharedPreferences(STORAGE_COLLECTION, Context.MODE_PRIVATE);
		SharedPreferences.Editor editor = prefs.edit();
		editor.putString(PROPERTY_NOTIFICATION, "");
		
		for(int i = 0; i < 5; i++) {
			editor.putString(getPreviousMessageKey(i), "");
		}
		
		editor.commit();
		
		Log.i("Notiflow", "Dismissed notification");
	}
	
	
	public static String getPreviousMessageKey(int id) {
		return PROPERTY_MESSAGE + Integer.toString(id);
	}
}
