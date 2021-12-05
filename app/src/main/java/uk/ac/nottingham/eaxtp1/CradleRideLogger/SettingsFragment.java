package uk.ac.nottingham.eaxtp1.CradleRideLogger;

import static uk.ac.nottingham.eaxtp1.CradleRideLogger.FileHandling.DeletingJobService.userWantsFilesKept;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.Recording.GPSService.autoStopOn;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.Recording.GPSTimerService.buffering;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.Recording.IMUService.heldWithMagnets;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.gpsOff;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.recording;

import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Bundle;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class SettingsFragment extends PreferenceFragment implements SharedPreferences.OnSharedPreferenceChangeListener {

    Resources res;

    int leftMargin, rightMargin, verticalMargin, topMargin;

    String asPref, buffS, buffE, filePref, // general use
            magPref, modesPref, // ambulance options
            testPref; // for testing

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.preferences);

        res = getActivity().getResources();

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
        asPref = res.getString(R.string.key_pref_as);
        filePref = res.getString(R.string.key_pref_files);

        if (BuildConfig.CROWD_MODE) { // buffer settings

            buffS = res.getString(R.string.key_pref_buff_start);
            buffE = res.getString(R.string.key_pref_buff_end);

        } else if (BuildConfig.AMB_MODE) {

            modesPref = res.getString(R.string.key_modes);

            magPref = res.getString(R.string.key_pref_magnets);

        } else if (BuildConfig.TEST_MODE) {

            testPref = res.getString(R.string.key_pref_test);

        }

        preferences.registerOnSharedPreferenceChangeListener(this);

        PreferenceScreen preferenceScreen = (PreferenceScreen) findPreference(res.getString(R.string.setScreen));
        PreferenceCategory buffCat = (PreferenceCategory) findPreference(res.getString(R.string.set_buff_tit));
        PreferenceCategory testCat = (PreferenceCategory) findPreference(res.getString(R.string.set_test_tit));
        PreferenceCategory ambCat = (PreferenceCategory) findPreference(res.getString(R.string.pref_amb_settings));

        if (!BuildConfig.CROWD_MODE) {
            preferenceScreen.removePreference(buffCat);
        }
        if (!BuildConfig.TEST_MODE) {
            preferenceScreen.removePreference(testCat);
        }
        if (!BuildConfig.AMB_MODE) {
            preferenceScreen.removePreference(ambCat);
        }

    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        leftMargin = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 2, res.getDisplayMetrics()
        );
        if (res.getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            rightMargin = (int) TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, 40, res.getDisplayMetrics()
            );
        } else {
            rightMargin = leftMargin;
        }
        verticalMargin = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 2, res.getDisplayMetrics()
        );
        topMargin = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, (int) res.getDimension(R.dimen.activity_vertical_margin) + 40, res.getDisplayMetrics()
        );
        View v = super.onCreateView(inflater, container, savedInstanceState);
        if (v != null) {
            ListView lv = v.findViewById(android.R.id.list);
            lv.setPadding( leftMargin, topMargin, rightMargin, verticalMargin);
        }
        return v;
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences preferences, String key) {

        if ( (key.equals(buffS) && ( recording || buffering )) || (key.equals(buffE) && recording) ) {

            buffToast(); // update user that this will be changed after any current recording.

        } else if (key.equals(asPref)) { // Auto stop on/off

            autoStopOn = preferences.getBoolean(key, true);

        } else if (key.equals(filePref)) { // Delete files automatically on/off

            userWantsFilesKept = !preferences.getBoolean(key,true);

        } else if (BuildConfig.TEST_MODE && key.equals(testPref)) { // Test mode on/off

            gpsOff = !preferences.getBoolean(key, false);

        } else if (BuildConfig.AMB_MODE) { // Remaining options are only for AMB MODE

            if (key.equals(magPref)) { // if attached using magnets, compass doesn't work.

                heldWithMagnets = preferences.getBoolean(key, true);

            } else if (key.equals(modesPref)) { // Assess the amount of modes selected

                Set<String> chosenModes = preferences.getStringSet(modesPref, new HashSet<>());

                // If only one option selected, set the Mode for all recordings
                if (chosenModes.size() == 1) {
                    SharedPreferences.Editor prefEd = preferences.edit();
                    String[] possibleModes = res.getStringArray(R.array.transport_modes);
                    int chosenModeInt = Integer.parseInt(new ArrayList<>(chosenModes).get(0));
                    String chosenMode = possibleModes[chosenModeInt];
                    prefEd.putString(res.getString(R.string.key_mode), chosenMode);
                    if (!chosenMode.equals(res.getString(R.string.mode_road))) {
                        // REMOVE THE MANUFACTURER AND ENGINE INPUTS
                        prefEd.remove(res.getString(R.string.key_man));
                        prefEd.remove(res.getString(R.string.key_eng));
                    }
                    prefEd.apply();
                }

            }

        }

    }

    public void buffToast() {
        String message = "Buffer value will be changed after the current recording.";
        Toast.makeText(getActivity(), message, Toast.LENGTH_SHORT).show();
    }
}

