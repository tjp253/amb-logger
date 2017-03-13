package uk.ac.nottingham.eaxtp1.CradleRideLogger;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;

import java.io.File;

public class WifiReceiver extends BroadcastReceiver {
    public WifiReceiver() {
    }

    String mainPath, zipPath;

    private int SERVICE_STARTED = 0;

    @Override
    public void onReceive(Context context, Intent intent) {

        mainPath = String.valueOf(context.getExternalFilesDir(""));
        zipPath = mainPath + "/Zipped";

        Intent uploadService = new Intent(context, UploadService.class);

        NetworkInfo info = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);

        if (info != null && info.isConnected()) {

//            Allocates where to look for the files to be uploaded
            File directory = new File(zipPath);
            File[] contents = directory.listFiles();

            if ((contents != null) && (contents.length != 0)) {

                context.startService(uploadService);

                SERVICE_STARTED = 1;

            }

        } else if (info != null && !info.isConnected() && SERVICE_STARTED == 1) {
            context.stopService(uploadService);
        }

    }

}

