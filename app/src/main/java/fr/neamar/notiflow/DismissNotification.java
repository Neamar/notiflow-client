package fr.neamar.notiflow;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.widget.Toast;

import fr.neamar.notiflow.db.NotificationHelper;

public class DismissNotification extends BroadcastReceiver {
@Override
	public void onReceive(Context context, Intent intent) {

		NotificationHelper.cleanNotifications(context, intent.getStringExtra("flow"));

		Log.i("Notiflow", "Dismissed notification");

		if (!intent.getAction().equals("notification_cancelled")) {
			Intent flowdockIntent;

			if (intent.hasExtra("flow_url") && !intent.getStringExtra("flow_url").equals("")) {
				flowdockIntent = new Intent();
				flowdockIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
				flowdockIntent.setAction(Intent.ACTION_VIEW);
				flowdockIntent.setData(Uri.parse(intent.getStringExtra("flow_url")));
			} else {
				flowdockIntent = context.getPackageManager().getLaunchIntentForPackage("com.flowdock.jorge");
			}

			if (flowdockIntent == null) {
				Toast.makeText(context, "Flowdock app not installed on the device. Unable to display conversation", Toast.LENGTH_SHORT).show();
			} else {
				context.startActivity(flowdockIntent);
			}
		}
	}
}
