/*
 * Copyright 2013 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package fr.neamar.notiflow;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.gcm.GoogleCloudMessaging;

/**
 * Ask for the user token
 */
public class MainActivity extends Activity {

	public static final String PROPERTY_GCM_TOKEN = "gcm_token";
	public static final String PROPERTY_FLOWDOCK_TOKEN = "flowdock_token";
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

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.activity_main);
		
		setDescription(getString(R.string.activity_main_description));
		
		EditText flowdockTokenInput = (EditText) findViewById(R.id.flowdockTokenInput);
		flowdockTokenInput.setText(getFlowdockToken(this));

		context = getApplicationContext();

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

	// Send an upstream message.
	public void onClick(final View view) {
		if (view == findViewById(R.id.submitTokenButton)) {
			EditText flowdockTokenInput = (EditText) findViewById(R.id.flowdockTokenInput);
			final String flowdockToken = flowdockTokenInput.getText().toString();
			final String gcmToken = getGcmToken(this);

			new AsyncTask<Void, Void, String>() {
				private Boolean success = false;
				
				@Override
				protected String doInBackground(Void... params) {
					// Check flowdock token is valid
					String pattern = "^[a-fA-F0-9]{32}$";
					if (!flowdockToken.matches(pattern)) {
						return "Token must be a 32 character hexadecimal string";
					}
					storeFlowdockToken(MainActivity.this, flowdockToken);

					// Check we have a GCM id
					if (gcmToken.isEmpty()) {
						return "GCM token still generating. Please wait a few seconds, check your connection and retry.";
					}

					Log.i(TAG, "Registering flowdockToken: " + flowdockToken);
					Log.i(TAG, "Registering GCM token: " + gcmToken);

					// Create a new HttpClient and Post Header
					HttpClient httpclient = new DefaultHttpClient();
					HttpPost httppost = new HttpPost("http://notiflow.herokuapp.com/init");

					try {
						// Add your data
						List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>(2);
						nameValuePairs.add(new BasicNameValuePair("flowdock_token", flowdockToken));
						nameValuePairs.add(new BasicNameValuePair("gcm_token", gcmToken));
						httppost.setEntity(new UrlEncodedFormEntity(nameValuePairs));

						// Execute HTTP Post Request
						HttpResponse response = httpclient.execute(httppost);
						
						BufferedReader reader = new BufferedReader(new InputStreamReader(response.getEntity().getContent(), "UTF-8"));
						StringBuilder builder = new StringBuilder();
						for (String line = null; (line = reader.readLine()) != null;) {
							builder.append(line).append("\n");
						}
						
						if(response.getStatusLine().getStatusCode() == 200) {
							success = true;
							return "Notification are on their ways... Followed flows: " + builder.toString();
						}
						else {
							String error= "Unable to match token. Error: " + builder.toString();
							return error;
						}
						
						
					} catch (ClientProtocolException e) {
						return e.toString();
					} catch (IOException e) {
						return e.toString();
					}
				}

				@Override
				protected void onPostExecute(String msg) {
					Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show();
					
					if(success) {
						finish();
					}
				}
			}.execute(null, null, null);
		}
	}
	
	private void setDescription(String description) {
		TextView mainActivityDescription = (TextView) findViewById(R.id.mainActivityDescription);
		mainActivityDescription.setText(Html.fromHtml(description));
		mainActivityDescription.setMovementMethod(LinkMovementMethod.getInstance());
	}

	/**
	 * Check the device to make sure it has the Google Play Services APK. If it
	 * doesn't, display a dialog that allows users to download the APK from the
	 * Google Play Store or enable it in the device's system settings.
	 */
	private boolean checkPlayServices() {
		int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);
		if (resultCode != ConnectionResult.SUCCESS) {
			if (GooglePlayServicesUtil.isUserRecoverableError(resultCode)) {
				GooglePlayServicesUtil.getErrorDialog(resultCode, this, PLAY_SERVICES_RESOLUTION_REQUEST).show();
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
	 * @param regId
	 *            registration ID
	 */
	private void storeRegistrationId(Context context, String gcmToken) {
		final SharedPreferences prefs = getPreferences(context);
		int appVersion = getAppVersion(context);
		Log.i(TAG, "Saving gcmToken on app version " + appVersion);
		SharedPreferences.Editor editor = prefs.edit();
		editor.putString(PROPERTY_GCM_TOKEN, gcmToken);
		editor.putInt(PROPERTY_APP_VERSION, appVersion);
		editor.commit();
	}

	/**
	 * Stores the registration ID and the app versionCode in the application's
	 * {@code SharedPreferences}.
	 * 
	 * @param context
	 *            application's context.
	 * @param regId
	 *            registration ID
	 */
	private void storeFlowdockToken(Context context, String flowdockToken) {
		final SharedPreferences prefs = getPreferences(context);
		SharedPreferences.Editor editor = prefs.edit();
		editor.putString(PROPERTY_FLOWDOCK_TOKEN, flowdockToken);
		editor.commit();
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
		final SharedPreferences prefs = getPreferences(context);
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
	 * Gets the current flowdock token.
	 * 
	 * @return flowdockToken, or empty on first use
	 */
	private String getFlowdockToken(Context context) {
		final SharedPreferences prefs = getPreferences(context);
		String flowdockToken = prefs.getString(PROPERTY_FLOWDOCK_TOKEN, "");

		return flowdockToken;
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
		} catch (NameNotFoundException e) {
			// should never happen
			throw new RuntimeException("Could not get package name: " + e);
		}
	}

	/**
	 * @return Application's {@code SharedPreferences}.
	 */
	private SharedPreferences getPreferences(Context context) {
		return getSharedPreferences(MainActivity.class.getSimpleName(), Context.MODE_PRIVATE);
	}
}
