package uk.ac.nottingham.eaxtp1.CradleRideLogger.Widgets;

import android.content.Context;
import android.text.InputFilter;
import android.util.AttributeSet;
import android.view.inputmethod.EditorInfo;

public class EditTextDefault extends androidx.appcompat.widget.AppCompatEditText {
    public EditTextDefault(Context context) {
        super(context);
        setDefaults();
    }

    public EditTextDefault(Context context, AttributeSet attrs) {
        super(context, attrs);
        setDefaults();
    }

    public EditTextDefault(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setDefaults();
    }
    
    public void setDefaults() {
        // Ensure everything is capitalised
        setFilters(new InputFilter[] {new InputFilter.AllCaps()});

        // Restrict entry to a single line
        setSingleLine(true);

        // Capitalise keyboard
        setInputType(EditorInfo.TYPE_TEXT_FLAG_CAP_CHARACTERS);
    }

}
