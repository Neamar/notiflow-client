package fr.neamar.notiflow;

import java.util.ArrayList;
import java.util.HashMap;

public class NotificationHelper {
	protected static final HashMap<String, Integer> notificationsId = new HashMap<String, Integer>();
	protected static final HashMap<String, ArrayList<String>> notifications = new HashMap<String, ArrayList<String>>();
	
	/**
	 * Add a new notification
	 * @param flow
	 * @param msg
	 */
	public static void addNotification(String flow, String msg) {
		if(!notifications.containsKey(flow)) {
			notifications.put(flow, new ArrayList<String>());
		}
		
		ArrayList<String> flowNotifications = notifications.get(flow);
		
		flowNotifications.add(0, msg);
	}
	
	/**
	 * Get up to five last notifications
	 * @param flow
	 * @return
	 */
	public static ArrayList<String> getNotifications(String flow) {
		if(notifications.containsKey(flow)) {
			return notifications.get(flow);
		}
		else {
			return new ArrayList<String>();
		}
	}
	
	/**
	 * Remove all stored notficiations from this flow.
	 * @param flow
	 */
	public static void cleanNotifications(String flow) {
		notifications.put(flow, new ArrayList<String>());
	}
	
	
	/**
	 * Return a value to use for notifications from this flow
	 * @param flow
	 * @return
	 */
	public static int getFlowId(String flow) {
		if(!notificationsId.containsKey(flow)) {
			notificationsId.put(flow, notificationsId.size());
		}
		
		return notificationsId.get(flow);
	}
}
