package uk.ac.nottingham.eaxtp1.CradleRideLogger.Preferences;

import android.content.Context;
import android.preference.ListPreference;
import android.util.AttributeSet;

public class MyListPreference extends ListPreference {
    // Extend the DEFAULT ListPreference to update the summary automatically

    public MyListPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public MyListPreference(Context context) {
        super(context);
    }

    @Override
    public void setValue(String value) {
        super.setValue(value); // Perform original function

        setSummary(getEntry()); // Then, set the summary to the new value
    }
}
