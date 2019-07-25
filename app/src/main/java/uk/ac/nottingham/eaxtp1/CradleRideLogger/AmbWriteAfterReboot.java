package uk.ac.nottingham.eaxtp1.CradleRideLogger;

import android.app.IntentService;
import android.content.Intent;
import android.content.SharedPreferences;

import java.io.File;

import static uk.ac.nottingham.eaxtp1.CradleRideLogger.AmbLoggingService.ambPath;

public class AmbWriteAfterReboot extends IntentService {

    public AmbWriteAfterReboot() {
        super("AmbWriteAfterReboot");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        writeAmbOpts();
    }

    public void writeAmbOpts() {

        SharedPreferences ambPref = getSharedPreferences(getString(R.string.pref_amb), MODE_PRIVATE);
        ambPath = ambPref.getString(getString(R.string.key_amb_name),null);

        startService(new Intent(this, AmbLoggingService.class)
                .putExtra(getString(R.string.bool_at_start), false));

        // Allow time for amb options to be written
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        String endPath = ambPref.getString(getString(R.string.key_end_name),null);
        if (endPath == null) return;

        File endFile = new File(endPath);
        endPath = endPath.replace(getString(R.string.file_type), getString(R.string.suffix) + getString(R.string.file_type));
        endFile.renameTo(new File(endPath));

        ambPref.edit().putBoolean(getString(R.string.key_dead),false).apply();

        this.startService(new Intent(getApplicationContext(), MovingService.class));

    }
}
