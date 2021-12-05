package uk.ac.nottingham.eaxtp1.CradleRideLogger;

import android.os.Bundle;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import android.text.Html;
import android.view.MenuItem;
import android.view.View;

public class Settings extends AppCompatActivity  {

    // Class to instantiate the SettingsFragment, which enables a UI method of changing preferences

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, new SettingsFragment())
                .commit();

        findViewById(android.R.id.content).setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle(Html.fromHtml("<font color='#FFFFFF'>Settings</font>"));
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