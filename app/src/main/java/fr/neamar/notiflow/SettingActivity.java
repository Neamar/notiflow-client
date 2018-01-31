package fr.neamar.notiflow;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceGroup;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.text.Html;
import android.text.Spanned;
import android.util.Log;
import android.widget.Toast;

import com.google.firebase.iid.FirebaseInstanceId;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import fr.neamar.notiflow.db.NotificationHelper;


public class SettingActivity extends PreferenceActivity implements SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String PROPERTY_FLOWDOCK = "flowdockToken";

    /**
     * Tag used on log messages.
     */
    private static final String TAG = "Notiflow";

    public static class RegisterWithServer extends AsyncTask<Void, Void, String> {
        private static final String TAG = "REGISTER_SERVER";

        private final Boolean silent;
        private boolean success = false;
        private WeakReference<Context> context;
        final String flowdockToken;

        public RegisterWithServer(Context context, boolean silent) {
            flowdockToken = PreferenceManager.getDefaultSharedPreferences(context).getString(PROPERTY_FLOWDOCK, "");
            this.silent = silent;
            this.context = new WeakReference<>(context);
        }

        @Override
        protected String doInBackground(Void... params) {
            // Check flowdock token is valid
            String pattern = "^[a-fA-F0-9]{32}$";
            if (!flowdockToken.matches(pattern)) {
                return "Token must be a 32 character hexadecimal string";
            }

            String fcmToken =  FirebaseInstanceId.getInstance().getToken();

            if (fcmToken == null || fcmToken.isEmpty()) {
                return "FCM token still generating. Please wait a few seconds, check your connection and retry.";
            }

            Log.i(TAG, "Registering flowdockToken: " + flowdockToken);
            Log.i(TAG, "Registering FCM token: " + fcmToken);

            // Create a new HttpClient and Post Header
            HttpClient httpclient = new DefaultHttpClient();
            HttpPost httppost = new HttpPost("http://notiflow.herokuapp.com/init");

            try {
                // Add your data
                List<NameValuePair> nameValuePairs = new ArrayList<>(2);
                nameValuePairs.add(new BasicNameValuePair("flowdock_token", flowdockToken));
                nameValuePairs.add(new BasicNameValuePair("gcm_token", fcmToken));
                httppost.setEntity(new UrlEncodedFormEntity(nameValuePairs));

                // Execute HTTP Post Request
                HttpResponse response = httpclient.execute(httppost);

                BufferedReader reader = new BufferedReader(new InputStreamReader(response.getEntity().getContent(), "UTF-8"));
                StringBuilder builder = new StringBuilder();
                for (String line; (line = reader.readLine()) != null; ) {
                    builder.append(line).append("\n");
                }

                if (response.getStatusLine().getStatusCode() == 200) {
                    success = true;

                    String flowsString = builder.toString();

                    return "Notifications are on their ways... Followed flows: " + flowsString;
                } else {
                    return "Unable to match token. Error: " + builder.toString();
                }


            } catch (IOException e) {
                return e.toString();
            }
        }

        @Override
        protected void onPostExecute(String msg) {
            if (!silent && context.get() != null) {
                Toast.makeText(context.get(), msg, Toast.LENGTH_LONG).show();
            }
            Log.i(TAG, msg);

            if (success) {
                Log.i(TAG, "Registered!");
            }
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        // Set up
        super.onCreate(savedInstanceState);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.registerOnSharedPreferenceChangeListener(this);

        // UI
        addPreferencesFromResource(R.xml.settings);
        EditTextPreference token = (EditTextPreference) findPreference(PROPERTY_FLOWDOCK);
        final Spanned tokenDescription = Html.fromHtml(getString(R.string.pref_token_description));
        token.setDialogMessage(tokenDescription);

        // UX
        if (prefs.getString(PROPERTY_FLOWDOCK, "").equals("")) {
            Toast.makeText(this, getString(R.string.pref_token_toast), Toast.LENGTH_SHORT).show();
        } else {
            token.setSummary(R.string.pref_token_summary_ok);
        }

        getPreferenceManager()
                .findPreference("flowdockLink")
                .setOnPreferenceClickListener(
                        new Preference.OnPreferenceClickListener() {
                            @Override
                            public boolean onPreferenceClick(Preference preference) {
                                Intent intent = new Intent(Intent.ACTION_VIEW);
                                intent.setData(Uri.parse("https://www.flowdock.com/account/tokens#reset-api-token"));
                                startActivity(intent);
                                return true;
                            }
                        });

        Long notificationCount = NotificationHelper.getTotalCreatedRows(this);
        PreferenceScreen preferenceScreen = getPreferenceScreen();

        PreferenceGroup preferenceGroup = (PreferenceGroup) findPreference("notiflowStats");
        if (notificationCount < 2) {
            preferenceScreen.removePreference(preferenceGroup);
        } else {
            Preference totalStats = findPreference("notiflowStatsTotal");
            String generatedText = getString(R.string.pref_stats_total_placeholder).replace("%s", String.valueOf(notificationCount));
            totalStats.setTitle(generatedText);
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equalsIgnoreCase(PROPERTY_FLOWDOCK)) {
            Log.i(TAG, "Registering token");
            new RegisterWithServer(this, false).execute(null, null, null);
        }
    }
}