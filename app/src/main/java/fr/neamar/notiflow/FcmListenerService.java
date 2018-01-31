package fr.neamar.notiflow;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

public class FcmListenerService extends FirebaseMessagingService {
    public static final String TAG = "FCM_LISTENER_SERVICE";

    @Override
    public void onMessageReceived(RemoteMessage message){
        String from = message.getFrom();
        Map data = message.getData();
    }

}