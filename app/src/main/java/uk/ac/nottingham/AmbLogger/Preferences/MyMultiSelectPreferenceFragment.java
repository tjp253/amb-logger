package uk.ac.nottingham.AmbLogger.Preferences;

import android.os.Bundle;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.MultiSelectListPreferenceDialogFragmentCompat;

public class MyMultiSelectPreferenceFragment extends MultiSelectListPreferenceDialogFragmentCompat {

    public static MyMultiSelectPreferenceFragment newInstance(@NonNull final String key) {
        final MyMultiSelectPreferenceFragment fragment = new MyMultiSelectPreferenceFragment();
        final Bundle bundle = new Bundle();
        bundle.putString(ARG_KEY, key);
        fragment.setArguments(bundle);
        return fragment;
    }

    @Override
    public void onStart() {
        super.onStart();

        // get the dialog that contains the list and buttons
        AlertDialog dialog = (AlertDialog) getDialog();

        if (dialog == null) return;

        // get the "OK" button, to disable it when no text is present
        Button buttonPositive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);

        ListView listView = dialog.getListView(); // grab the ListView object

        // listener to enable/disable "OK" button depending on number of selected options
        // call gets thrown every time an option is selected/deselected
        AdapterView.OnItemClickListener originalListener = listView.getOnItemClickListener();
        listView.setOnItemClickListener((adapterView, view, i, l) -> {
            // call the original function to ensure selections are stored
            originalListener.onItemClick(adapterView, view, i, l);
            // enable button if entries selected
            buttonPositive.setEnabled(listView.getCheckedItemCount() > 0);
        });
    }
}
