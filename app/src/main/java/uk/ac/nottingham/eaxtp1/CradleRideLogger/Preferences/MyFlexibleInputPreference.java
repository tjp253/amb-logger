package uk.ac.nottingham.eaxtp1.CradleRideLogger.Preferences;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;

import androidx.preference.DialogPreference;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import uk.ac.nottingham.eaxtp1.CradleRideLogger.Utilities.TextUtils;

public class MyFlexibleInputPreference extends DialogPreference {

    int maxNumChoices;
    String[] entryStrings; // To contain the strings for each entry row
    List<Integer> rowsVisible = new ArrayList<>(); // To contain the indices for visible rows

    Set<String> persistedSet = new HashSet<>(); // Grab initial values and compare at end.

    public MyFlexibleInputPreference(Context context, AttributeSet attrs) {
        super(context, attrs);

        maxNumChoices = 6/*getContext().getResources().getInteger(R.integer.max_input_options)*/;

        entryStrings = new String[maxNumChoices];

        setSummaryProvider();
    }

    void setSummaryProvider() {
        setSummaryProvider(preference -> {
            List<String> values = new ArrayList<>(getPersistedStringSet(new HashSet<>()));
            Collections.sort(values);
            return TextUtils.joinNewLine(values);
        });
    }

    @Override
    public boolean callChangeListener(Object newValue) {
        boolean valuesChanged = !newValue.equals(getPersistedStringSet(persistedSet));
        if (valuesChanged) {
            //noinspection unchecked
            persistStringSet((Set<String>) newValue);
            notifyChanged(); // so the SummaryProvider is called to update the Summary
        }
        return valuesChanged;
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        CharSequence[] defaultValues = a.getTextArray(index);
        Set<String> defaultSet = new HashSet<>();
        for (CharSequence value : defaultValues) {
            defaultSet.add(value.toString());
        }
        return defaultSet;
    }

    @Override
    protected void onSetInitialValue(Object defaultValue) {

        // first, grab any values stored in preferences and convert to a list
        //noinspection unchecked: it is definitely a Set!
        persistedSet = getPersistedStringSet((Set<String>) defaultValue);
        List<String> persistedValues = new ArrayList<>(persistedSet);
        rowsVisible.clear();

        // fill entries with the stored values
        Collections.sort(persistedValues);
        for (int index = 0; index < persistedValues.size(); index++) {
            // PersistedSet is stored in random order...
            entryStrings[index] = persistedValues.get(index);
            rowsVisible.add(index);
        }

    }

    int getNumValues() {
        // Returns the number of entered values
        int count = 0;
        for (String entry : entryStrings) {
            if (entry != null && entry.length() > 0) {
                count++;
            }
        }
        return count;
    }

    String getVisibleString(int index) {
        return entryStrings[rowsVisible.get(index)];
    }

}
