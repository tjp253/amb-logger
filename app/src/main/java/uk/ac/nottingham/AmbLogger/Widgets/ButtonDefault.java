package uk.ac.nottingham.AmbLogger.Widgets;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import uk.ac.nottingham.AmbLogger.R;

public class ButtonDefault extends androidx.appcompat.widget.AppCompatButton {

    public ButtonDefault(@NonNull Context context) {
        super(context);
        initialise(context);
    }

    public ButtonDefault(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        initialise(context);
    }

    public ButtonDefault(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initialise(context);
    }

    public void initialise(Context context) {
        // set the text colour to White (otherwise would be Black inside Settings)
        setTextColor(context.getColor(R.color.colorWhite));
    }

    final float alphaEnabled = 1, alphaDisabled = 0.5f;

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        if (enabled) {
            setAlpha(alphaEnabled);
        } else {
            setAlpha(alphaDisabled); // dim the button when disabled
        }
    }
}
