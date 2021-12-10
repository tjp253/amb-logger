package uk.ac.nottingham.eaxtp1.CradleRideLogger.Settings;

import static uk.ac.nottingham.eaxtp1.CradleRideLogger.FileHandling.DeletingJobService.userWantsFilesKept;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.Recording.GPSService.autoStopOn;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.Recording.GPSTimerService.buffering;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.Recording.IMUService.heldWithMagnets;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.gpsOff;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.recording;

import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.widget.Toast;

import androidx.fragment.app.DialogFragment;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import uk.ac.nottingham.eaxtp1.CradleRideLogger.BuildConfig;
import uk.ac.nottingham.eaxtp1.CradleRideLogger.Preferences.MyEditTextPreferenceFragment;
import uk.ac.nottingham.eaxtp1.CradleRideLogger.Preferences.MyFlexibleInputPreference;
import uk.ac.nottingham.eaxtp1.CradleRideLogger.Preferences.MyFlexibleListInputPrefFragment;
import uk.ac.nottingham.eaxtp1.CradleRideLogger.Preferences.MyMultiSelectListPreference;
import uk.ac.nottingham.eaxtp1.CradleRideLogger.Preferences.MyMultiSelectPreferenceFragment;
import uk.ac.nottingham.eaxtp1.CradleRideLogger.Preferences.MyNumberPickerPrefFragment;
import uk.ac.nottingham.eaxtp1.CradleRideLogger.Preferences.MyNumberPickerPreference;
import uk.ac.nottingham.eaxtp1.CradleRideLogger.R;

public class SettingsFragment extends PreferenceFragmentCompat
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    Resources res;

    String asPref, buffS, buffE, filePref, // general use
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

        PreferenceScreen prefScreen = findPreference(res.getString(R.string.setScreen));
        assert prefScreen != null;

        if (!BuildConfig.CROWD_MODE) {
            prefScreen.removePreference(findPreference(res.getString(R.string.set_buff_tit)));
        }
        if (!BuildConfig.TEST_MODE) {
            prefScreen.removePreference(findPreference(res.getString(R.string.set_test_tit)));
        }
        if (!BuildConfig.AMB_MODE) {
            prefScreen.removePreference(findPreference(res.getString(R.string.pref_amb_settings)));
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

