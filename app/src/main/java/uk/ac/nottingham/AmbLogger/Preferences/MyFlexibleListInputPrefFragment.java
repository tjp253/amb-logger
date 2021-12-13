package uk.ac.nottingham.AmbLogger.Preferences;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleEventObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.preference.PreferenceDialogFragmentCompat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import uk.ac.nottingham.AmbLogger.R;

public class MyFlexibleListInputPrefFragment extends PreferenceDialogFragmentCompat implements LifecycleEventObserver {

    public static MyFlexibleListInputPrefFragment newInstance(@NonNull final String key) {
        final MyFlexibleListInputPrefFragment fragment = new MyFlexibleListInputPrefFragment();
        final Bundle bundle = new Bundle();
        bundle.putString(ARG_KEY, key);
        fragment.setArguments(bundle);
        return fragment;
    }

    AlertDialog dialog;
    MyFlexibleInputPreference preference;

    int rowOfFocus = 0;

    ConstraintLayout addButtRow;
    Button addButt;

    List<ConstraintLayout> entryRows = new ArrayList<>();
    List<EditText> entryTrolls = new ArrayList<>();

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Context context = getActivity();

        preference = (MyFlexibleInputPreference) getPreference();

        assert context != null;
        AlertDialog.Builder builder = new AlertDialog.Builder(context)
                .setTitle(preference.getTitle())
                .setPositiveButton(preference.getPositiveButtonText(), this)
                .setNegativeButton(preference.getNegativeButtonText(), this)
                .setView(
                        View.inflate(getContext(), R.layout.flexible_input_rows, null)
                );

        return builder.create();
    }

    public void checkVisibleEntries() {
        // Check if all visible EditTexts have values
        for (int row = 0; row < preference.maxNumChoices; row++) {
            if (entryRows.get(row).getVisibility() == View.VISIBLE
                    && entryTrolls.get(row).getText().length() == 0) {
                // if any are empty, disable row adding
                addButt.setEnabled(false);
                return;
            }
        }
        addButt.setEnabled(true);
    }

    public void addRow() {
        updateRows();
        int rowToAdd = preference.getNumValues();
        entryRows.get(rowToAdd).setVisibility(View.VISIBLE);
        entryTrolls.get(rowToAdd).requestFocus();
        preference.rowsVisible.add(rowToAdd);
        addButt.setEnabled(false);

        if (rowToAdd == preference.maxNumChoices - 1) {
            // Hide the button when the maximum entries are visible
            addButtRow.setVisibility(View.GONE);
        }
    }

    void reorderEntryStrings() {
        String[] orderedStrings = new String[preference.maxNumChoices];
        for (int index = 0; index < preference.rowsVisible.size(); index++) {
            orderedStrings[index] = preference.getVisibleString(index);
        }
        preference.entryStrings = orderedStrings;
    }

    public void updateRows() {

        reorderEntryStrings(); // ensure the entered values are first in the array

        int numValues = preference.getNumValues();

        if (!preference.rowsVisible.contains(rowOfFocus)) { // RowOfFocus has been deleted
            rowOfFocus = numValues - 1; // reset to the last row
        } else if (rowOfFocus > 0 && preference.rowsVisible.get(rowOfFocus - 1) == rowOfFocus) {
            rowOfFocus--; // RowOfFocus has changed index
        }

        preference.rowsVisible.clear(); // reset the visible indices, as the row order is changed
        EditText entryTroll;
        String entryString;
        for (int row = 0; row < numValues; row++) {
            // Show this row
            entryRows.get(row).setVisibility(View.VISIBLE);
            // Set the text to the next entry (useful when removing middle rows)
            entryTroll = entryTrolls.get(row);
            entryString = preference.entryStrings[row];
            entryTroll.setText(entryString);
            // update the visible indices
            preference.rowsVisible.add(row);
            if (row == rowOfFocus) {
                entryTroll.requestFocus();
                entryTroll.setSelection(entryString.length());
            }
        }

        for (int row = numValues; row < preference.maxNumChoices; row++) {
            entryRows.get(row).setVisibility(View.GONE);
            entryTrolls.get(row).setText(null);
        }

        checkVisibleEntries();

    }

    @Override
    public void onDialogClosed(boolean positiveResult) {
        if (positiveResult) { // APPLY ANY CHANGES
            // Convert non-null entries to a Set
            Set<String> newValues = new HashSet<>(
                    Arrays.asList(preference.entryStrings).subList(0, preference.getNumValues())
            );
            preference.callChangeListener(newValues);
        }
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        getLifecycle().addObserver(this);
    }

    @Override
    public void onStateChanged(@NonNull LifecycleOwner source, @NonNull Lifecycle.Event event) {
        if (event != Lifecycle.Event.ON_START) return;

        preference.onSetInitialValue(null);

        dialog = (AlertDialog) requireDialog();

        int[] rowIDs = {
                R.id.entry_0, R.id.entry_1, R.id.entry_2, R.id.entry_3, R.id.entry_4, R.id.entry_5,
        };
        for (int index = 0; index < preference.maxNumChoices; index++) {
            final int entryIndex = index;
            final ConstraintLayout cl = dialog.findViewById(rowIDs[index]);
            entryRows.add(index, cl);

            assert cl != null;
            EditText et = cl.findViewById(R.id.row_input);
            entryTrolls.add(index, et);

            // add a listener to stop rows from being added and added
            et.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {}

                @Override
                public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                    String text = charSequence.toString();
                    preference.entryStrings[entryIndex] = text;
                    if (cl.getVisibility() == View.VISIBLE) {
                        // TODO: ALSO CHECK UNIQUE
                        checkVisibleEntries();
                    }
                }

                @Override
                public void afterTextChanged(Editable editable) {}
            });

            et.setOnFocusChangeListener((view, hasFocus) -> {
                if (hasFocus) {
                    rowOfFocus = entryIndex;
                }
            });

            cl.findViewById(R.id.row_butt).setOnClickListener(view -> {
                preference.rowsVisible.remove(entryIndex);
                cl.setVisibility(View.GONE); // hide the row
                et.setText(null); // clear the value
                addButtRow.setVisibility(View.VISIBLE);
                checkVisibleEntries();
                updateRows();
            });
        }

        addButtRow = dialog.findViewById(R.id.addButtRow);
        addButt = dialog.findViewById(R.id.buttAdd);
        assert addButt != null;
        addButt.setOnClickListener(view -> {
            if (preference.getNumValues() < preference.maxNumChoices) {
                addRow();
            }
        });

        updateRows();

        getLifecycle().removeObserver(this);
    }

}
