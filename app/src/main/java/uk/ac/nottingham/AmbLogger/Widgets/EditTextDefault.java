package uk.ac.nottingham.AmbLogger.Widgets;

import android.content.Context;
import android.text.InputFilter;
import android.util.AttributeSet;
import android.view.inputmethod.EditorInfo;

import uk.ac.nottingham.AmbLogger.R;

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

        // Set the text color to black
        setTextColor(getResources().getColor(R.color.colorBlack, getContext().getTheme()));

        // Ensure everything is capitalised
        setFilters(new InputFilter[] {new InputFilter.AllCaps()});

        // Restrict entry to a single line
        setSingleLine(true);

        // Capitalise keyboard
        setInputType(EditorInfo.TYPE_TEXT_FLAG_CAP_CHARACTERS);
    }

}
