package uk.ac.nottingham.eaxtp1.CradleRideLogger.Preferences;

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.TypedArray;
import android.os.Bundle;
import android.preference.DialogPreference;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import androidx.constraintlayout.widget.ConstraintLayout;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import uk.ac.nottingham.eaxtp1.CradleRideLogger.R;
import uk.ac.nottingham.eaxtp1.CradleRideLogger.Utilities.TextUtils;

public class MyFlexibleInputPreference extends DialogPreference {

    int maxNumChoices, rowOfFocus = 0;
    String[] entryStrings; // To contain the strings for each entry row
    List<Integer> rowsVisible = new ArrayList<>(); // To contain the indices for visible rows

    AlertDialog dialog;

    ConstraintLayout addButtRow;
    Button addButt;

    Set<String> persistedSet = new HashSet<>();

    List<ConstraintLayout> entryRows = new ArrayList<>();
    List<EditText> entryTrolls = new ArrayList<>();

    public MyFlexibleInputPreference(Context context, AttributeSet attrs) {
        super(context, attrs);

        maxNumChoices = getContext().getResources().getInteger(R.integer.max_input_options);

        entryStrings = new String[maxNumChoices];
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
    protected void onSetInitialValue(boolean restorePersistedValue, Object defaultValue) {

        if (!restorePersistedValue) {
            //noinspection unchecked: it is definitely a Set!
            persistStringSet((Set<String>) defaultValue);
        }

        // first, grab any values stored in preferences and convert to a list
        persistedSet = getPersistedStringSet(persistedSet); // store for later comparison
        List<String> persistedValues = new ArrayList<>(persistedSet);
        rowsVisible.clear();

        // fill entries with the stored values
        Collections.sort(persistedValues);
        for (int index = 0; index < persistedValues.size(); index++) {
            // PersistedSet is stored in random order...
            entryStrings[index] = persistedValues.get(index);
            rowsVisible.add(index);
        }

        updateSummary();
    }

    @Override
    protected void onPrepareDialogBuilder(AlertDialog.Builder builder) {
        super.onPrepareDialogBuilder(builder);

        builder.setView(View.inflate(getContext(), R.layout.flexible_input_rows, null));

    }

    @Override
    protected void showDialog(Bundle state) {
        super.showDialog(state);

        onSetInitialValue(true, null);

        dialog = (AlertDialog) super.getDialog();

        int[] rowIDs = {
            R.id.entry_0, R.id.entry_1, R.id.entry_2, R.id.entry_3, R.id.entry_4, R.id.entry_5,
        };
        for (int index = 0; index < maxNumChoices; index++) {
            final int entryIndex = index;
            final ConstraintLayout cl = dialog.findViewById(rowIDs[index]);
            entryRows.add(index, cl);

            EditText et = cl.findViewById(R.id.row_input);
            entryTrolls.add(index, et);

            // add a listener to stop rows from being added and added
            et.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

                }

                @Override
                public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                    String text = charSequence.toString();
                    entryStrings[entryIndex] = text;
                    if (cl.getVisibility() == View.VISIBLE) {
                        // TODO: ALSO CHECK UNIQUE
                        checkVisibleEntries();
                    }
                }

                @Override
                public void afterTextChanged(Editable editable) {

                }
            });

            et.setOnFocusChangeListener((view, hasFocus) -> {
                if (hasFocus) {
                    rowOfFocus = entryIndex;
                }
            });

            cl.findViewById(R.id.row_butt).setOnClickListener(view -> {
                rowsVisible.remove(entryIndex);
                cl.setVisibility(View.GONE); // hide the row
                et.setText(null); // clear the value
                addButtRow.setVisibility(View.VISIBLE);
                checkVisibleEntries();
                updateRows();
            });
        }

        addButtRow = dialog.findViewById(R.id.addButtRow);
        addButt = dialog.findViewById(R.id.buttAdd);
        addButt.setOnClickListener(view -> {
            if (getNumValues() < maxNumChoices) {
                addRow();
            }
        });

        updateRows();

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

    public void addRow() {
        updateRows();
        int rowToAdd = getNumValues();
        entryRows.get(rowToAdd).setVisibility(View.VISIBLE);
        entryTrolls.get(rowToAdd).requestFocus();
        rowsVisible.add(rowToAdd);
        addButt.setEnabled(false);

        if (rowToAdd == maxNumChoices - 1) {
            // Hide the button when the maximum entries are visible
            addButtRow.setVisibility(View.GONE);
        }
    }

    void reorderEntryStrings() {
        String[] orderedStrings = new String[maxNumChoices];
        for (int index = 0; index < rowsVisible.size(); index++) {
            orderedStrings[index] = entryStrings[rowsVisible.get(index)];
        }
        entryStrings = orderedStrings;
    }

    public void updateRows() {

        reorderEntryStrings(); // ensure the entered values are first in the array

        int numValues = getNumValues();

        if (!rowsVisible.contains(rowOfFocus)) { // RowOfFocus has been deleted
            rowOfFocus = numValues - 1; // reset to the last row
        } else if (rowOfFocus > 0 && rowsVisible.get(rowOfFocus - 1) == rowOfFocus) {
            rowOfFocus--; // RowOfFocus has changed index
        }

        rowsVisible.clear(); // reset the visible indices, as the row order is changed
        EditText entryTroll;
        String entryString;
        for (int row = 0; row < numValues; row++) {
            // Show this row
            entryRows.get(row).setVisibility(View.VISIBLE);
            // Set the text to the next entry (useful when removing middle rows)
            entryTroll = entryTrolls.get(row);
            entryString = entryStrings[row];
            entryTroll.setText(entryString);
            // update the visible indices
            rowsVisible.add(row);
            if (row == rowOfFocus) {
                entryTroll.requestFocus();
                entryTroll.setSelection(entryString.length());
            }
        }

        for (int row = numValues; row < maxNumChoices; row++) {
            entryRows.get(row).setVisibility(View.GONE);
            entryTrolls.get(row).setText(null);
        }

        checkVisibleEntries();

    }

    public void checkVisibleEntries() {
        // Check if all visible EditTexts have values
        for (int row = 0; row < maxNumChoices; row++) {
            if (entryRows.get(row).getVisibility() == View.VISIBLE
                    && entryTrolls.get(row).getText().length() == 0) {
                // if any are empty, disable row adding
                addButt.setEnabled(false);
                return;
            }
        }
        addButt.setEnabled(true);
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);

        if (positiveResult) { // APPLY ANY CHANGES
            Set<String> newValues = new HashSet<>();
            // Convert non-null entries to a Set
            for (int index = 0; index < getNumValues(); index++) {
                newValues.add(entryStrings[index]);
            }
            if (!newValues.equals(persistedSet)) {
                // If the new set is different, store it!
                persistStringSet(newValues);
                updateSummary();
            }
        }
    }

    public void updateSummary() {
        setSummary(
            TextUtils.joinNewLine(Arrays.copyOfRange(entryStrings, 0, getNumValues()))
        );
    }
}
