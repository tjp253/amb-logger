package uk.ac.nottingham.AmbLogger.Preferences;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.DialogPreference;

import uk.ac.nottingham.AmbLogger.R;

public class MyNumberPickerPreference extends DialogPreference {

    private static final Integer DEFAULT_VALUE = 0;
    private Integer minValue = 0;
    private Integer maxValue = 60;
    private Integer stepValue = 1;
    private Boolean wrapSelectorWheel = false;
    private Integer chosenValue;
    private String units = ""; // string containing the units

    public MyNumberPickerPreference(Context context, AttributeSet attrs) {
        super(context, attrs);

        TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.myNumberPickerPreference);

        minValue = ta.getInt(R.styleable.myNumberPickerPreference_minValue, minValue);
        maxValue = ta.getInt(R.styleable.myNumberPickerPreference_maxValue, maxValue);
        stepValue = ta.getInt(R.styleable.myNumberPickerPreference_stepValue, stepValue);
        wrapSelectorWheel = ta.getBoolean(R.styleable.myNumberPickerPreference_wrapSelectorWheel, wrapSelectorWheel);
        if (ta.getString(R.styleable.myNumberPickerPreference_units) != null) {
            // grab the units, but add a space in front!
            units = ta.getString(R.styleable.myNumberPickerPreference_units);
        }

        ta.recycle();

        setSummaryProvider(preference -> getValue() + getUnits());

    }

    public int getValue() {
        if (chosenValue == null) return DEFAULT_VALUE;
        return chosenValue;
    }

    public Integer getMinValue() {
        return minValue;
    }

    public Integer getMaxValue() {
        return maxValue;
    }

    public Integer getStepValue() {
        return stepValue;
    }

    public String getUnits() {
        if (units.equals("")) {
            return units;
        }
        return " " + units;
    }

    public String getUnitsTitle() {
        if (units.equals("")) {
            return units;
        }
        return " (" + units + ")";
    }

    public Boolean getWrapSelectorWheel() {
        return wrapSelectorWheel;
    }

    public void setPersistedValue(@NonNull final Integer value) {
        chosenValue = value;
        persistInt(chosenValue);
        notifyChanged();
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        return a.getString(index);
    }

    @Override
    protected void onSetInitialValue(@Nullable Object defaultValue) {
        int value;
        if (defaultValue == null) {
            value = getPersistedInt(DEFAULT_VALUE);
        } else if (defaultValue instanceof String) {
            value = Integer.parseInt((String) defaultValue);
        } else {
            value = (Integer) defaultValue;
        }
        setPersistedValue(value);
    }

}
