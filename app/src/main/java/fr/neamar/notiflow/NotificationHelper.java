package fr.neamar.notiflow;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;

public class NotificationHelper {
	protected static final HashMap<String, Date> notificationsDate = new HashMap<String, Date>();
	protected static final HashMap<String, ArrayList<String>> notifications = new HashMap<String, ArrayList<String>>();

	/**
	 * Add a new notification
	 *
	 * @param flow
	 * @param msg
	 */
	public static void addNotification(String flow, String msg) {
		if (!notifications.containsKey(flow)) {
			notifications.put(flow, new ArrayList<String>());
		}

		notificationsDate.put(flow, new Date());

		ArrayList<String> flowNotifications = notifications.get(flow);

		flowNotifications.add(0, msg);
	}

	/**
	 * Get up to five last notifications
	 *
	 * @param flow
	 * @return
	 */
	public static ArrayList<String> getNotifications(String flow) {
		if (notifications.containsKey(flow)) {
			return notifications.get(flow);
		} else {
			return new ArrayList<String>();
		}
	}

	public static Date getLastNotificationDate(String flow) {
		if (notificationsDate.containsKey(flow)) {
			return notificationsDate.get(flow);
		} else {
			return new Date(0);
		}
	}

	/**
	 * Remove all stored notifications from this flow.
	 *
	 * @param flow
	 */
	public static void cleanNotifications(String flow) {
		notificationsDate.remove(flow);
		notifications.put(flow, new ArrayList<String>());
	}
}
