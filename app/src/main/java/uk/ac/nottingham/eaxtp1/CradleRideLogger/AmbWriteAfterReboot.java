package uk.ac.nottingham.eaxtp1.CradleRideLogger;

import android.app.IntentService;
import android.content.Intent;
import android.content.SharedPreferences;
import android.text.TextUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.Deflater;
import java.util.zip.GZIPOutputStream;

public class AmbWriteAfterReboot extends IntentService {

    public AmbWriteAfterReboot() {
        super("AmbWriteAfterReboot");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        writeAmbOpts();
    }

    OutputStream ambStream;

    public void writeAmbOpts() {

        SharedPreferences ambPref = getSharedPreferences(getString(R.string.pref_amb), MODE_PRIVATE);
        String ambList1 = ambPref.getString(getString(R.string.key_amb_opts),null),
                ambPath = ambPref.getString(getString(R.string.key_amb_name),null);

        if (ambList1 == null || ambPath == null) return;

        String trans = ambPref.getString(getString(R.string.key_trans),""),
                emerge = ambPref.getString(getString(R.string.key_emerge),""),
                ambList2 = TextUtils.join(",", Arrays.asList("Reason for Transfer", trans,
                        "Emergency driving used", emerge)) + "\n";

        try {
            ambStream = new FileOutputStream(ambPath);
            ambStream = new GZIPOutputStream(ambStream)
            {{def.setLevel(Deflater.BEST_COMPRESSION);}};

            ambStream.write(ambList1.getBytes(StandardCharsets.UTF_8));
            ambStream.write(ambList2.getBytes(StandardCharsets.UTF_8));

            ambStream.close();
        } catch (IOException e) {
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
