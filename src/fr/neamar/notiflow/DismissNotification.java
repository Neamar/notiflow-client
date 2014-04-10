package fr.neamar.notiflow;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class DismissNotification extends BroadcastReceiver {
	public static final String STORAGE_COLLECTION = "notifier";
	public static final String PROPERTY_NOTIFICATION = "notification";
	public static final String PROPERTY_MESSAGE = "message-";
	
	@Override
	public void onReceive(Context context, Intent intent) {
		
		NotificationHelper.cleanNotifications(intent.getStringExtra("flow"));
		
		Log.i("Notiflow", "Dismissed notification");
	}
	
	
	public static String getPreviousMessageKey(int id) {
		return PROPERTY_MESSAGE + Integer.toString(id);
	}
}
