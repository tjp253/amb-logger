package uk.ac.nottingham.eaxtp1.CradleRideLogger;

import android.app.IntentService;
import android.content.Intent;
import android.content.SharedPreferences;
import android.text.TextUtils;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.Deflater;
import java.util.zip.GZIPOutputStream;

import static uk.ac.nottingham.eaxtp1.CradleRideLogger.IMUService.date;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.LoggingService.gzipPath;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.LoggingService.mainPath;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.userID;

public class AmbLoggingService extends IntentService {
//    IntentService to handle logging of ambulance options.
//    Because ambulance options were not being written until the end of recordings which meant that
//    meta got lost if the phone died during recording, ambulance options are now saved to the file
//    at both the start and end of the journey.
//      - Placeholders are used at the start of recordings, to help identify interrupted recordings
//
//      This Service is called by both the standard LoggingService and, if required,
//      AmbWriteAfterReboot (although the 'after reboot' method doesn't appear to work)

    boolean atStart;
    static String ambPath;
    OutputStream myAmbStream;

    public AmbLoggingService() {
        super("AmbLoggingService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        atStart = intent.getBooleanExtra(getString(R.string.bool_at_start), true);
        prepLog();
    }

    public void prepLog() { // Prepare file to write amb options to
        if (ambPath == null) {
            ambPath = mainPath + date + getResources().getString(R.string.id_spacer) + userID + "-00.csv.gz";
        }
        try {
            myAmbStream = new FileOutputStream(ambPath);
            myAmbStream = new GZIPOutputStream(myAmbStream)
            {{def.setLevel(Deflater.BEST_COMPRESSION);}};
        } catch (IOException e) {
            e.printStackTrace();
        }

        logAmb();
    }

    public void logAmb() { // Log ambulance options
        String[] template = getResources().getStringArray(R.array.amb_file_template);

        SharedPreferences ambPref = getSharedPreferences(getString(R.string.pref_amb), MODE_PRIVATE);

        template[1] = ambPref.getString(getString(R.string.key_amb),"");
        template[3] = ambPref.getString(getString(R.string.key_troll),"");
        template[5] = ambPref.getString(getString(R.string.key_bob),"");
        if ("YES".equals(template[5])) {
            template[7] = ambPref.getString(getString(R.string.key_trans), getResources().getString(R.string.optUnknown));
        } else {
            template[7] = "N/A";
        }
        template[9] = ambPref.getString(getString(R.string.key_emerge),getResources().getString(R.string.optUnknown));

        String ambList = TextUtils.join(",", template);

        try {
            myAmbStream.write(ambList.getBytes(StandardCharsets.UTF_8));
            myAmbStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        SharedPreferences.Editor ambEd = ambPref.edit();
        if (atStart) {
            ambEd.putString(getString(R.string.key_amb_name), ambPath);
            ambEd.putString(getString(R.string.key_end_name), gzipPath);
        } else {
            ambEd.putString(getString(R.string.key_amb_name),null);
            ambEd.putString(getString(R.string.key_end_name), null);
            for (String key : getResources().getStringArray(R.array.amb_opt_keys)) {
                ambEd.remove(key);
            }

            ambPath = null;
        }
        ambEd.apply();
    }

}
