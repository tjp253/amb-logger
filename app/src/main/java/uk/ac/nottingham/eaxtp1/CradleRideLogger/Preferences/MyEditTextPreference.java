package uk.ac.nottingham.eaxtp1.CradleRideLogger.Preferences;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

public class MyEditTextPreference extends EditTextPreference {

    // Extension of the Base EditTextPreference to enforce CAPITAL entries and minimum
    // entry length

    Button buttonPositive;
    int startingTextLength;

    public MyEditTextPreference(Context context) {
        super(context);
    }

    public MyEditTextPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onAddEditTextToDialogView(View dialogView, EditText editText) {
        super.onAddEditTextToDialogView(dialogView, editText);

        startingTextLength = editText.getText().length(); // length of initial entry

        editText.setSelection(startingTextLength); // move cursor to end of text

        // only allow capital letters (avoids user discretion)
        editText.setFilters(new InputFilter[] {new InputFilter.AllCaps()});

        // listener to enable/disable "OK" button depending on length of text
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

    @Override
    protected void showDialog(Bundle state) {
        super.showDialog(state);

        // get the "OK" button, to disable it when no text is present
        buttonPositive = ((AlertDialog) getDialog()).getButton(AlertDialog.BUTTON_POSITIVE);
        buttonPositive.setEnabled(startingTextLength > 0); // enable button if text exists

    }

    @Override
    public void setText(String text) {
        super.setText(text); // Perform original function

        setSummary(getText()); // Then, set the summary to the new value
    }

    // TODO: control the EditText dialog
}
