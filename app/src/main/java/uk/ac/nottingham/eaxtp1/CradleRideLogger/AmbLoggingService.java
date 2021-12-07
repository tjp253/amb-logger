package uk.ac.nottingham.eaxtp1.CradleRideLogger;

import android.app.IntentService;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Build;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.Deflater;
import java.util.zip.GZIPOutputStream;

import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.versionNum;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.Recording.IMUService.date;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.Recording.LoggingService.gzipPath;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.Recording.LoggingService.mainPath;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.userID;

import androidx.preference.PreferenceManager;

import uk.ac.nottingham.eaxtp1.CradleRideLogger.Utilities.TextUtils;

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
    SharedPreferences preferences;

    public AmbLoggingService() {
        super("AmbLoggingService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        res = getResources();
        atStart = intent.getBooleanExtra(res.getString(R.string.bool_at_start), true);
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
    
    String fillMeta() {
        // Fill the string array of Meta Options

        // TODO: change the template depending on the available options? processing can handle it.
        String[] template_meta = res.getStringArray(R.array.amb_file_template);

        String notApplicable = res.getString(R.string.valueNotApplicable),
                unknown = res.getString(R.string.optUnknown);

        // COUNTRY
        template_meta[1] = preferences.getString(res.getString(R.string.key_country), "GB");
        // TEAM
        template_meta[3] = preferences.getString(res.getString(R.string.key_pref_ntt), res.getString(R.string.ntt_centre));
        // MODE
        template_meta[5] = preferences.getString(res.getString(R.string.key_mode), res.getString(R.string.mode_road));
        // MANUFACTURER
        template_meta[7] = preferences.getString(res.getString(R.string.key_man), notApplicable);
        // ENGINE
        template_meta[9] = preferences.getString(res.getString(R.string.key_eng), notApplicable);
        // TROLLEY
        template_meta[11] = preferences.getString(res.getString(R.string.key_troll),"");
        // PATIENT
        template_meta[13] = preferences.getString(res.getString(R.string.key_bob),"");
        // REASON
        if (res.getString(R.string.yesButt).equals(template_meta[13])) {
            template_meta[15] = preferences.getString(res.getString(R.string.key_trans), unknown);
        } else {
            template_meta[15] = notApplicable;
        }
        // EMERGENCY
        template_meta[17] = preferences.getString(res.getString(R.string.key_emerge), unknown);

        return TextUtils.joinCSV(template_meta);
    }

    String fillProcessing() {
        String[] template_pro = res.getStringArray(R.array.processing_info_template);

        // USER ID
        template_pro[1] = String.valueOf(userID);
        // APP VERSION
        template_pro[3] = versionNum;
        // DEVICE MODEL
        template_pro[5] = Build.MODEL;

        return TextUtils.joinCSV(template_pro);
    }

    public void logAmb() { // Log ambulance options

        preferences = PreferenceManager.getDefaultSharedPreferences(this);

        String ambList = TextUtils.joinCSV(new String[] {fillMeta(), fillProcessing()}) + "\n";

        try {
            myAmbStream.write(ambList.getBytes(StandardCharsets.UTF_8));
            myAmbStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        SharedPreferences.Editor ambEd = preferences.edit();
        if (atStart) {
            ambEd.putString(res.getString(R.string.key_amb_name), ambPath);
            ambEd.putString(res.getString(R.string.key_end_name), gzipPath);
        } else {
            ambEd.putString(res.getString(R.string.key_amb_name),null);
            ambEd.putString(res.getString(R.string.key_end_name), null);
            for (String key : res.getStringArray(R.array.amb_opt_keys)) {
                ambEd.remove(key);
            }

            ambPath = null;
        }
        ambEd.apply();
    }

}
