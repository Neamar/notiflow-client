package fr.neamar.notiflow;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.PreferenceActivity;
import android.text.Html;
import android.text.Spanned;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.gcm.GoogleCloudMessaging;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import static com.google.android.gms.common.GooglePlayServicesUtil.*;


public class SettingActivity extends PreferenceActivity {

    public static final String PROPERTY_GCM_TOKEN = "gcm_token";
    private static final String PROPERTY_APP_VERSION = "appVersion";
    private static final int PLAY_SERVICES_RESOLUTION_REQUEST = 9000;

    /**
     * Sender ID for GCM.
     */
    String SENDER_ID = "880839177332";

    /**
     * Tag used on log messages.
     */
    static final String TAG = "Notiflow";

    GoogleCloudMessaging gcm;
    AtomicInteger msgId = new AtomicInteger();
    Context context;

    String regid;
    private SharedPreferences prefs;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        // Set up
        super.onCreate(savedInstanceState);

        context = getApplicationContext();
        prefs = PreferenceManager.getDefaultSharedPreferences(this);

        // UI
        addPreferencesFromResource(R.xml.settings);
        EditTextPreference token = (EditTextPreference) findPreference("flowdockToken");
        final Spanned tokenDescription = Html.fromHtml(getString(R.string.pref_token_description));
        token.setDialogMessage(tokenDescription);

        // UX
        if(prefs.getString("flowdockToken", "").equals("")) {
             token.setSummary(R.string.pref_token_summary_ok);
        }
        else {
            Toast.makeText(this, getString(R.string.pref_token_toast), Toast.LENGTH_SHORT);
        }

        // Check device for Play Services APK. If check succeeds, proceed with
        // GCM registration.
        if (checkPlayServices()) {
            gcm = GoogleCloudMessaging.getInstance(this);
            regid = getGcmToken(context);
            if (regid.isEmpty()) {
                registerInBackground();
            }
        } else {
            Toast.makeText(this, getString(R.string.activity_main_no_gps), Toast.LENGTH_LONG).show();
            finish();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Check device for Play Services APK.
        checkPlayServices();
    }

    /**
     * Check the device to make sure it has the Google Play Services APK. If it
     * doesn't, display a dialog that allows users to download the APK from the
     * Google Play Store or enable it in the device's system settings.
     */
    private boolean checkPlayServices() {
        int resultCode = isGooglePlayServicesAvailable(this);
        if (resultCode != ConnectionResult.SUCCESS) {
            if (isUserRecoverableError(resultCode)) {
                getErrorDialog(resultCode, this, PLAY_SERVICES_RESOLUTION_REQUEST).show();
            } else {
                Log.i(TAG, "This device is not supported.");
                finish();
            }
            return false;
        }
        return true;
    }

    /**
     * Stores the registration ID and the app versionCode in the application's
     * {@code SharedPreferences}.
     *
     * @param context
     *            application's context.
     * @param gcmToken
     *            registration ID
     */
    private void storeRegistrationId(Context context, String gcmToken) {
        int appVersion = getAppVersion(context);
        Log.i(TAG, "Saving gcmToken on app version " + appVersion);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(PROPERTY_GCM_TOKEN, gcmToken);
        editor.putInt(PROPERTY_APP_VERSION, appVersion);
        editor.apply();
    }


    /**
     * Gets the current registration ID for application on GCM service, if there
     * is one.
     * <p>
     * If result is empty, the app needs to register.
     *
     * @return registration ID, or empty string if there is no existing
     *         registration ID.
     */
    private String getGcmToken(Context context) {
        String gcmToken = prefs.getString(PROPERTY_GCM_TOKEN, "");
        if (gcmToken.isEmpty()) {
            Log.i(TAG, "Registration not found.");
            return "";
        }
        // Check if app was updated; if so, it must clear the registration ID
        // since the existing regID is not guaranteed to work with the new
        // app version.
        int registeredVersion = prefs.getInt(PROPERTY_APP_VERSION, Integer.MIN_VALUE);
        int currentVersion = getAppVersion(context);
        if (registeredVersion != currentVersion) {
            Log.i(TAG, "App version changed.");
            return "";
        }

        return gcmToken;
    }


    /**
     * Registers the application with GCM servers asynchronously.
     * <p>
     * Stores the registration ID and the app versionCode in the application's
     * shared preferences.
     */
    private void registerInBackground() {
        new AsyncTask<Void, Void, String>() {
            @Override
            protected String doInBackground(Void... params) {
                String msg = "";
                try {
                    if (gcm == null) {
                        gcm = GoogleCloudMessaging.getInstance(context);
                    }
                    regid = gcm.register(SENDER_ID);
                    msg = "Device registered, registration ID=" + regid;

                    // Persist the regID - no need to register again.
                    storeRegistrationId(context, regid);
                } catch (IOException ex) {
                    msg = "Error :" + ex.getMessage();
                    // If there is an error, don't just keep trying to register.
                    // Require the user to click a button again, or perform
                    // exponential back-off.
                }
                return msg;
            }
        }.execute(null, null, null);
    }

    /**
     * @return Application's version code from the {@code PackageManager}.
     */
    private static int getAppVersion(Context context) {
        try {
            PackageInfo packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return packageInfo.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            // should never happen
            throw new RuntimeException("Could not get package name: " + e);
        }
    }
}