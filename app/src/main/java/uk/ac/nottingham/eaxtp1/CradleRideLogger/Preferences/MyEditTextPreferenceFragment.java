package uk.ac.nottingham.eaxtp1.CradleRideLogger.Preferences;

import android.content.DialogInterface;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.EditTextPreferenceDialogFragmentCompat;

public class MyEditTextPreferenceFragment extends EditTextPreferenceDialogFragmentCompat {

    // DO NOT NEED TO OVERRIDE EDIT-TEXT-PREFERENCE, BUT USE THIS FRAGMENT

    public static MyEditTextPreferenceFragment newInstance(@NonNull final String key) {
        final MyEditTextPreferenceFragment fragment = new MyEditTextPreferenceFragment();
        final Bundle bundle = new Bundle();
        bundle.putString(ARG_KEY, key);
        fragment.setArguments(bundle);
        return fragment;
    }

    @Override
    public void onStart() {
        super.onStart();
        AlertDialog dialog = (AlertDialog) getDialog();
        if (dialog == null) return;
        Button buttonPositive = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
        EditText editText = getDialog().findViewById(android.R.id.edit);

        int startingTextLength = editText.getText().length(); // length of initial entry

        editText.setSelection(startingTextLength); // move cursor to end of text

        // only allow capital letters (avoids user discretion)
        editText.setFilters(new InputFilter[] {new InputFilter.AllCaps()});

        editText.setInputType(InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);

        buttonPositive.setEnabled(startingTextLength > 0); // enable button if text exists

        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {}

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                buttonPositive.setEnabled(charSequence.length() > 0);
            }

            @Override
            public void afterTextChanged(Editable editable) {}
        });

    }
}
