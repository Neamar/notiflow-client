package fr.neamar.notiflow;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.widget.Toast;

public class DismissNotification extends BroadcastReceiver {
	public static final String STORAGE_COLLECTION = "notifier";
	public static final String PROPERTY_NOTIFICATION = "notification";
	public static final String PROPERTY_MESSAGE = "message-";
	
	@Override
	public void onReceive(Context context, Intent intent) {
		
		NotificationHelper.cleanNotifications(intent.getStringExtra("flow"));
		
		Log.i("Notiflow", "Dismissed notification");
		
		if(!intent.getAction().equals("notification_cancelled")) {
            Intent flowdockIntent;

            if(intent.hasExtra("flow_url")) {
                flowdockIntent = new Intent();
                flowdockIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                flowdockIntent.setAction(Intent.ACTION_VIEW);
                flowdockIntent.setData(Uri.parse(intent.getStringExtra("flow_url")));
            }
            else {
                flowdockIntent = context.getPackageManager().getLaunchIntentForPackage("com.flowdock.jorge");
            }

            if(flowdockIntent == null) {
                Toast.makeText(context, "Flowdock app not installed on the device. Unable to display conversation", Toast.LENGTH_SHORT).show();
            }
            else {
                context.startActivity(flowdockIntent);
            }
		}
	}
	
	
	public static String getPreviousMessageKey(int id) {
		return PROPERTY_MESSAGE + Integer.toString(id);
	}
}
