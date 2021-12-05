package uk.ac.nottingham.eaxtp1.CradleRideLogger.Preferences;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.preference.MultiSelectListPreference;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MyMultiSelectListPreference extends MultiSelectListPreference {

    // Extension of the base Preference to disable submission if no options are selected

    public MyMultiSelectListPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public MyMultiSelectListPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public MyMultiSelectListPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public MyMultiSelectListPreference(Context context) {
        super(context);
    }

    @Override
    protected void onSetInitialValue(boolean restoreValue, Object defaultValue) {
        super.onSetInitialValue(restoreValue, defaultValue);
        updateSummary();
    }

    @Override
    protected void showDialog(Bundle state) {
        super.showDialog(state);

        // Need to work within this method as otherwise the button returns "null".

        // get the dialog that contains the list and buttons
        AlertDialog dialog = (AlertDialog) getDialog();

        // get the "OK" button, to disable it when no text is present
        Button button = dialog.getButton(AlertDialog.BUTTON_POSITIVE);

        ListView listView = dialog.getListView(); // grab the ListView object

        button.setEnabled(getValues().size() > 0); // enable button if entries selected

        // listener to enable/disable "OK" button depending on number of selected options
        // call gets thrown every time an option is selected/deselected
        AdapterView.OnItemClickListener originalListener = listView.getOnItemClickListener();
        listView.setOnItemClickListener((adapterView, view, i, l) -> {
            // call the original function to ensure selections are stored
            originalListener.onItemClick(adapterView, view, i, l);
            button.setEnabled(listView.getCheckedItemCount() > 0);
        });
    }

    private void updateSummary() {
        // Provide a list of choices as a summary
        List<String> valueIndices = new ArrayList<>(getPersistedStringSet(new HashSet<>()));
        Collections.sort(valueIndices);
        int numValues = valueIndices.size();
        CharSequence[] entryStrings = getEntries(),
                chosenStrings = new CharSequence[numValues];
        for (int index = 0; index < numValues; index++) {
            chosenStrings[index] = entryStrings[Integer.parseInt(valueIndices.get(index))];
        }
        setSummary(TextUtils.join("\n", chosenStrings));
    }

    @Override
    public void setValues(Set<String> values) {
        super.setValues(values);
        updateSummary();
    }
}
