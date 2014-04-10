package fr.neamar.notiflow;

import java.util.ArrayList;
import java.util.HashMap;

public class NotificationHelper {
	protected static final HashMap<String, ArrayList<String>> notifications = new HashMap<String, ArrayList<String>>();
	
	public static void addNotification(String flow, String msg) {
		if(!notifications.containsKey(flow)) {
			notifications.put(flow, new ArrayList<String>());
		}
		
		ArrayList<String> flowNotifications = notifications.get(flow);
		
		flowNotifications.add(0, msg);
		
		if(notifications.get(flow).size() > 5) {
			flowNotifications.remove(flowNotifications.size() - 1);
		}
	}
	
	public static ArrayList<String> getNotifications(String flow) {
		if(notifications.containsKey(flow)) {
			return notifications.get(flow);
		}
		else {
			return new ArrayList<String>();
		}
	}
	
	public static void cleanNotifications(String flow) {
		notifications.put(flow, new ArrayList<String>());
	}
}
