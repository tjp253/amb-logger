package uk.ac.nottingham.eaxtp1.CradleRideLogger;

import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.support.annotation.Nullable;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.text.Html;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.Toast;

import static uk.ac.nottingham.eaxtp1.CradleRideLogger.DeletingJobService.userWantsFilesKept;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.GPSService.autoStopOn;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.IMUService.heldWithMagnets;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.GPSTimerService.buffering;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.gpsOff;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.recording;

public class Settings extends AppCompatActivity  {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, new SettingsFragment())
                .commit();

        findViewById(android.R.id.content).setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle(Html.fromHtml("<font color='#FFFFFF'>Settings</font>"));
            actionBar.setHomeAsUpIndicator(R.drawable.back_arrow);
        }

        SharedPreferences prefOld = getSharedPreferences(getString(R.string.pref_main),MODE_PRIVATE);
        if (prefOld.contains(getString(R.string.key_pref_as))) {
            SharedPreferences.Editor edNew = PreferenceManager.getDefaultSharedPreferences(this).edit();
            edNew.putBoolean(getString(R.string.key_pref_as), prefOld.getBoolean(getString(R.string.key_pref_as), true));
            edNew.putInt(getString(R.string.key_pref_delay), prefOld.getInt(getString(R.string.key_pref_delay), 0));
            edNew.putInt(getString(R.string.key_pref_buff_start), prefOld.getInt(getString(R.string.key_pref_buff_start), 0));
            edNew.putInt(getString(R.string.key_pref_buff_end), prefOld.getInt(getString(R.string.key_pref_buff_end), 0));
            edNew.apply();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) { // Back arrow to return from Settings
        switch (item.getItemId()) {
            case android.R.id.home:
//                NavUtils.navigateUpFromSameTask(this);
                onBackPressed();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public static class SettingsFragment extends PreferenceFragment implements SharedPreferences.OnSharedPreferenceChangeListener {
        SharedPreferences preferences, prefAmb;
        SharedPreferences.Editor prefEd, prefEdAmb;
        ListPreference nttList;
        int leftMargin, rightMargin, verticalMargin, topMargin;
        String asPref, tPref, buffS, buffE, nttPref, magPref, filePref;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            // Load the preferences from an XML resource
            addPreferencesFromResource(R.xml.preferences);

            preferences = getActivity().getSharedPreferences(getString(R.string.pref_main),MODE_PRIVATE);
            asPref = getActivity().getString(R.string.key_pref_as);
            tPref = getActivity().getString(R.string.key_pref_test);
            buffS = getActivity().getString(R.string.key_pref_buff_start);
            buffE = getActivity().getString(R.string.key_pref_buff_end);
            filePref = getActivity().getResources().getString(R.string.key_pref_files);

            if (BuildConfig.AMB_MODE) {
                prefAmb = getActivity().getSharedPreferences(getString(R.string.pref_amb),MODE_PRIVATE);
                nttPref = getActivity().getString(R.string.key_pref_ntt);
                magPref = getActivity().getString(R.string.key_pref_magnets);
                nttList = (ListPreference) findPreference(nttPref);
                nttList.setTitle(getActivity().getResources().getStringArray(R.array.ntt_choice)[prefAmb.getInt(nttPref,0)]);
            }

            PreferenceManager.getDefaultSharedPreferences(getActivity()).registerOnSharedPreferenceChangeListener(this);

            PreferenceScreen preferenceScreen = (PreferenceScreen) findPreference(getResources().getString(R.string.setScreen));
            PreferenceCategory buffCat = (PreferenceCategory) findPreference(getResources().getString(R.string.set_buff_tit));
            PreferenceCategory testCat = (PreferenceCategory) findPreference(getResources().getString(R.string.set_test_tit));
            PreferenceCategory ambCat = (PreferenceCategory) findPreference(getResources().getString(R.string.pref_amb_settings));

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
            leftMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2, getResources().getDisplayMetrics());
            if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
                rightMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 40, getResources().getDisplayMetrics());
            } else {
                rightMargin = leftMargin;
            }
            verticalMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2, getResources().getDisplayMetrics());
            topMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, (int) getResources().getDimension(R.dimen.activity_vertical_margin) + 40, getResources().getDisplayMetrics());
            View v = super.onCreateView(inflater, container, savedInstanceState);
            if (v != null) {
                ListView lv = v.findViewById(android.R.id.list);
                lv.setPadding( leftMargin, topMargin, rightMargin, verticalMargin);
            }
            return v;
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            prefEd = preferences.edit();

            if (key.equals(asPref)) {

                autoStopOn = sharedPreferences.getBoolean(key, true);
                prefEd.putBoolean(key, sharedPreferences.getBoolean(key, true)).apply();

            } else if (key.equals(tPref)) {

                gpsOff = !sharedPreferences.getBoolean(key, false);

            } else if (key.equals(filePref)) {

                userWantsFilesKept = !sharedPreferences.getBoolean(key,true);

            } else if (BuildConfig.AMB_MODE) {

                if (key.equals(nttPref)) {

//                Android Studio thinks 'choice' is not used... but it is. Twice. Inspection disabled.
                    //noinspection UnusedAssignment
                    int choice = Integer.parseInt(sharedPreferences.getString(key, ""));
                    nttList.setTitle(getActivity().getResources().getStringArray(R.array.ntt_choice)[choice]);

                    prefEdAmb = prefAmb.edit();
                    prefEdAmb.putInt(key, choice).apply();

                } else if (key.equals(magPref)) {

                    heldWithMagnets = sharedPreferences.getBoolean(key, true);
                    prefEd.putBoolean(key, sharedPreferences.getBoolean(key, true)).apply();

                }

            } else {

                prefEd.putInt(key, sharedPreferences.getInt(key,0)).commit();
                if ( (key.equals(buffS) && ( recording || buffering )) || (key.equals(buffE) && recording) ) {
                    buffToast();
                }

            }
        }

        public void buffToast() {
            String message = "Buffer value will be changed after the current recording.";
            Toast.makeText(getActivity(), message, Toast.LENGTH_SHORT).show();
        }
    }
}