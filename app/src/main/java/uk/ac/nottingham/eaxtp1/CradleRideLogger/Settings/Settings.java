package uk.ac.nottingham.eaxtp1.CradleRideLogger.Settings;

import android.os.Bundle;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import android.text.Html;
import android.view.MenuItem;

import uk.ac.nottingham.eaxtp1.CradleRideLogger.R;

public class Settings extends AppCompatActivity  {

    // Class to instantiate the SettingsFragment, which enables a UI method of changing preferences

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_settings);

        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.settings, new SettingsFragment())
                .commit();

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle(
                    Html.fromHtml(
                            "<font color='#FFFFFF'>Settings</font>",
                            Html.FROM_HTML_MODE_LEGACY
                    )
            );
            actionBar.setHomeAsUpIndicator(R.drawable.back_arrow);
        }

    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) { // Back arrow to return from Settings
        if (item.getItemId() == android.R.id.home) {//                NavUtils.navigateUpFromSameTask(this);
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

}