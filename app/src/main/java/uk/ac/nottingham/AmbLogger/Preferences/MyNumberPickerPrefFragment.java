package uk.ac.nottingham.AmbLogger.Preferences;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.NumberPicker;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.PreferenceDialogFragmentCompat;

import java.util.Arrays;
import java.util.List;

public class MyNumberPickerPrefFragment extends PreferenceDialogFragmentCompat {

    private static final String ARG_MIN_VALUE = "min_value", ARG_MAX_VALUE = "max_value",
        ARG_STEP_VALUE = "step_value", ARG_WRAP_WHEEL = "wrap_wheel";

    private int minValue, stepValue;
    static int DEFAULT_MIN_VALUE = 0, DEFAULT_MAX_VALUE = 60, DEFAULT_STEP_VALUE = 1;
    static boolean DEFAULT_WRAP = false;

    final static Bundle arguments = new Bundle();

    NumberPicker numberPicker;

    public static MyNumberPickerPrefFragment newInstance(
            @NonNull final String key,
            final Integer minValue,
            final Integer maxValue,
            final Boolean wrapSelectorWheel,
            final Integer stepValue
    ) {
        final MyNumberPickerPrefFragment fragment = new MyNumberPickerPrefFragment();

        arguments.putString(ARG_KEY, key);
        arguments.putInt(ARG_MIN_VALUE, minValue);
        arguments.putInt(ARG_MAX_VALUE, maxValue);
        arguments.putInt(ARG_STEP_VALUE, stepValue);
        arguments.putBoolean(ARG_WRAP_WHEEL, wrapSelectorWheel);

        fragment.setArguments(arguments);
        return fragment;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {

        Context context = getActivity();

        Integer chosenValue = getNumberPickerPreference().getValue();

        minValue = arguments.getInt(ARG_MIN_VALUE, DEFAULT_MIN_VALUE);
        stepValue = arguments.getInt(ARG_STEP_VALUE, DEFAULT_STEP_VALUE);
        final int maxValue = arguments.getInt(ARG_MAX_VALUE, DEFAULT_MAX_VALUE);
        final boolean wrapSelectorWheel = arguments.getBoolean(ARG_WRAP_WHEEL, DEFAULT_WRAP);

        String[] displayValues = getDisplayValues(minValue, maxValue, stepValue);

        numberPicker = new NumberPicker(context);
        numberPicker.setMinValue(0); // First displayValue
        numberPicker.setMaxValue(displayValues.length - 1);
        numberPicker.setDisplayedValues(displayValues);
        numberPicker.setWrapSelectorWheel(wrapSelectorWheel);
        // Set the "value" to the index of the chosenValue
        numberPicker.setValue(getChosenIndex(displayValues, chosenValue));
        numberPicker.setLayoutParams(
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );

        MyNumberPickerPreference preference = getNumberPickerPreference();


        final LinearLayout linearLayout = new LinearLayout(this.getContext());
        linearLayout.setLayoutParams(
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
                )
        );
        linearLayout.setGravity(Gravity.CENTER);
        linearLayout.addView(numberPicker);

        assert context != null;
        AlertDialog.Builder builder = new AlertDialog.Builder(context)
                .setTitle(preference.getTitle() + preference.getUnitsTitle())
                .setPositiveButton(preference.getPositiveButtonText(), this)
                .setNegativeButton(preference.getNegativeButtonText(), this)
                .setView(linearLayout);

        return builder.create();
    }

    String[] getDisplayValues(int min, int max, int step) {
        int length = 1 + (max - min) / step;
        String[] displayValues = new String[length];
        for (int index = 0; index < length; index++) {
            displayValues[index] = String.valueOf(min + (index * step));
        }
        return displayValues;
    }

    Integer getChosenIndex(String[] displayValues, Integer chosenValue) {
        List<String> values = Arrays.asList(displayValues);
        String choice = String.valueOf(chosenValue);
        if (!values.contains(choice)) return 0;
        return values.indexOf(choice);
    }

    @Override
    public void onDialogClosed(boolean positiveResult) {
        if (positiveResult) {
            Integer value = minValue + (numberPicker.getValue() * stepValue);
            if (getNumberPickerPreference().callChangeListener(value)) {
                getNumberPickerPreference().setPersistedValue(value);
            }
        }
    }

    private MyNumberPickerPreference getNumberPickerPreference() {
        return (MyNumberPickerPreference) getPreference();
    }
}
