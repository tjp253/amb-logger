package uk.ac.nottingham.AmbLogger.Preferences;

import android.content.Context;
import android.util.AttributeSet;

import androidx.preference.MultiSelectListPreference;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import uk.ac.nottingham.AmbLogger.Utilities.TextUtils;

public class MyMultiSelectListPreference extends MultiSelectListPreference {

    // Extension of the base Preference to disable submission if no options are selected

    public MyMultiSelectListPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setSummaryProvider();
    }

    void setSummaryProvider() { // SET HERE SO THAT IT WORKS ON SETTINGS CREATE
        setSummaryProvider(preference -> {
            List<String> valueIndices = new ArrayList<>(getPersistedStringSet(new HashSet<>()));
            Collections.sort(valueIndices);
            int numValues = valueIndices.size();
            CharSequence[] entryStrings = getEntries(),
                    chosenStrings = new CharSequence[numValues];
            for (int index = 0; index < numValues; index++) {
                chosenStrings[index] = entryStrings[Integer.parseInt(valueIndices.get(index))];
            }
            return TextUtils.joinNewLine(chosenStrings);
        });
    }

}
