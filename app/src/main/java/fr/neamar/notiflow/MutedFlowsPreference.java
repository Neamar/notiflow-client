package fr.neamar.notiflow;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.MultiSelectListPreference;
import android.preference.PreferenceManager;
import android.util.AttributeSet;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by neamar on 16/01/16.
 */
public class MutedFlowsPreference extends MultiSelectListPreference {

    public MutedFlowsPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onPrepareDialogBuilder(AlertDialog.Builder builder) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());

        Set<String> selections = prefs.getStringSet("knownFlows", new HashSet<String>());
        ArrayList<String> flows = new ArrayList<>(selections);
        Collections.sort(flows);

        if (flows.size() == 0) {
            Toast.makeText(getContext(), "No flows yet. They'll appear when you start getting messages.", Toast.LENGTH_SHORT).show();
        }

        setEntries(flows.toArray(new CharSequence[]{}));
        setEntryValues(flows.toArray(new CharSequence[]{}));

        super.onPrepareDialogBuilder(builder);
    }
}
