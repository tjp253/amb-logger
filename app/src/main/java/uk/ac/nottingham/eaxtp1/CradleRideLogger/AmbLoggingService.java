package uk.ac.nottingham.eaxtp1.CradleRideLogger;

import android.app.IntentService;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.text.TextUtils;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.Deflater;
import java.util.zip.GZIPOutputStream;

import static uk.ac.nottingham.eaxtp1.CradleRideLogger.Recording.IMUService.date;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.Recording.LoggingService.gzipPath;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.Recording.LoggingService.mainPath;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.userID;

import androidx.preference.PreferenceManager;

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
    Resources res;

    public AmbLoggingService() {
        super("AmbLoggingService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        res = getResources();
        atStart = intent.getBooleanExtra(getString(R.string.bool_at_start), true);
        prepLog();
    }

    public void prepLog() { // Prepare file to write amb options to
        if (ambPath == null) {
            ambPath = mainPath + date + res.getString(R.string.id_spacer) + userID + res.getString(R.string.suffix_meta) + res.getString(R.string.file_type);
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
        // TODO: change the template depending on the available options? processing can handle it.
        String[] template = res.getStringArray(R.array.amb_file_template);

        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);

        String notApplicable = getString(R.string.valueNotApplicable),
            unknown = getString(R.string.optUnknown);

        // COUNTRY
        template[1] = pref.getString(getString(R.string.key_country), "GB");
        // TEAM
        template[3] = pref.getString(getString(R.string.key_pref_ntt), getString(R.string.ntt_centre));
        // MODE
        template[5] = pref.getString(getString(R.string.key_mode), "");
        // MANUFACTURER
        template[7] = pref.getString(getString(R.string.key_man), notApplicable);
        // ENGINE
        template[9] = pref.getString(getString(R.string.key_eng), notApplicable);
        // TROLLEY
        template[11] = pref.getString(getString(R.string.key_troll),"");
        // PATIENT
        template[13] = pref.getString(getString(R.string.key_bob),"");
        // REASON
        if (getString(R.string.yesButt).equals(template[13])) {
            template[15] = pref.getString(getString(R.string.key_trans), unknown);
        } else {
            template[15] = notApplicable;
        }
        // EMERGENCY
        template[17] = pref.getString(getString(R.string.key_emerge), unknown);

        String ambList = TextUtils.join(",", template) + "\n";

        try {
            myAmbStream.write(ambList.getBytes(StandardCharsets.UTF_8));
            myAmbStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        SharedPreferences.Editor ambEd = pref.edit();
        if (atStart) {
            ambEd.putString(getString(R.string.key_amb_name), ambPath);
            ambEd.putString(getString(R.string.key_end_name), gzipPath);
        } else {
            ambEd.putString(getString(R.string.key_amb_name),null);
            ambEd.putString(getString(R.string.key_end_name), null);
            for (String key : res.getStringArray(R.array.amb_opt_keys)) {
                ambEd.remove(key);
            }

            ambPath = null;
        }
        ambEd.apply();
    }

}
