package uk.ac.nottingham.AmbLogger.Settings;

import static uk.ac.nottingham.AmbLogger.FileHandling.DeletingJobService.userWantsFilesKept;
import static uk.ac.nottingham.AmbLogger.Recording.GPSService.autoStopOn;
import static uk.ac.nottingham.AmbLogger.Recording.IMUService.heldWithMagnets;
import static uk.ac.nottingham.AmbLogger.MainActivity.gpsOff;

import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;
import android.preference.PreferenceManager;

import androidx.fragment.app.DialogFragment;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import uk.ac.nottingham.AmbLogger.BuildConfig;
import uk.ac.nottingham.AmbLogger.Preferences.MyEditTextPreferenceFragment;
import uk.ac.nottingham.AmbLogger.Preferences.MyFlexibleInputPreference;
import uk.ac.nottingham.AmbLogger.Preferences.MyFlexibleListInputPrefFragment;
import uk.ac.nottingham.AmbLogger.Preferences.MyMultiSelectListPreference;
import uk.ac.nottingham.AmbLogger.Preferences.MyMultiSelectPreferenceFragment;
import uk.ac.nottingham.AmbLogger.Preferences.MyNumberPickerPrefFragment;
import uk.ac.nottingham.AmbLogger.Preferences.MyNumberPickerPreference;
import uk.ac.nottingham.AmbLogger.R;

public class SettingsFragment extends PreferenceFragmentCompat
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    Resources res;

    String asPref, filePref, // general use
            magPref, modesPref, // ambulance options
            testPref; // for testing

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {

        // Load the preferences from an XML resource
        setPreferencesFromResource(R.xml.preferences, rootKey);

        res = requireActivity().getResources();

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
        asPref = res.getString(R.string.key_pref_as);
        filePref = res.getString(R.string.key_pref_files);
        magPref = res.getString(R.string.key_pref_magnets);
        modesPref = res.getString(R.string.key_modes);
        testPref = res.getString(R.string.key_pref_test);

        preferences.registerOnSharedPreferenceChangeListener(this);

        PreferenceScreen prefScreen = findPreference(res.getString(R.string.setScreen));
        assert prefScreen != null;

        if (BuildConfig.TEST_MODE) {
            prefScreen.removePreference(findPreference(res.getString(R.string.pref_amb_settings)));
        } else {
            prefScreen.removePreference(findPreference(res.getString(R.string.set_test_tit)));
        }

    }

    @Override
    public void onDisplayPreferenceDialog(Preference preference) {
        DialogFragment f = null;

        if (preference instanceof EditTextPreference) {
            f = MyEditTextPreferenceFragment.newInstance(
                    preference.getKey()
            );
        } else if (preference instanceof MyMultiSelectListPreference) {
            f = MyMultiSelectPreferenceFragment.newInstance(
                    preference.getKey()
            );
        } else if (preference instanceof MyFlexibleInputPreference) {
            f = MyFlexibleListInputPrefFragment.newInstance(
                    preference.getKey()
            );
        } else if (preference instanceof MyNumberPickerPreference) {
            MyNumberPickerPreference myPreference = (MyNumberPickerPreference) preference;
            f = MyNumberPickerPrefFragment.newInstance(
                    myPreference.getKey(),
                    myPreference.getMinValue(), myPreference.getMaxValue(),
                    myPreference.getWrapSelectorWheel(), myPreference.getStepValue()
            );
        }

        if (f == null) {
            super.onDisplayPreferenceDialog(preference);
            return;
        }

        f.setTargetFragment(this, 0);
        f.show(getParentFragmentManager(), "Custom Preference");

    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences preferences, String key) {

        if (key.equals(asPref)) { // Auto stop on/off

            autoStopOn = preferences.getBoolean(key, true);

        } else if (key.equals(filePref)) { // Delete files automatically on/off

            userWantsFilesKept = !preferences.getBoolean(key,true);

        } else if (key.equals(testPref)) { // Location receiver on/off

            gpsOff = !preferences.getBoolean(key, false);

        } else if (key.equals(magPref)) { // if attached using magnets, compass doesn't work.

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

