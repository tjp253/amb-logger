package uk.ac.nottingham.eaxtp1.CradleRideLogger;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;

public class WifiReceiver extends BroadcastReceiver {
    public WifiReceiver() {
    }

    String mainPath, zipPath;

    static boolean wifiConnected = false;

    private int SERVICE_STARTED = 0;

    @Override
    public void onReceive(Context context, Intent intent) {

        mainPath = String.valueOf(context.getExternalFilesDir(""));
        zipPath = mainPath + "/Finished";

        Intent uploadService = new Intent(context, UploadService.class);

        NetworkInfo info = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);

        if (info != null && info.isConnected()) {

            wifiConnected = true;

//            Allocates where to look for the files to be uploaded
            File zipDirectory = new File(zipPath);
            File[] zipContents = zipDirectory.listFiles();

            if ((zipContents != null) && (zipContents.length != 0)) {

                context.startService(uploadService);

                SERVICE_STARTED = 1;

            }

        } else if (info != null && !info.isConnected()) {

            wifiConnected = false;

            if (SERVICE_STARTED == 1) {
                context.stopService(uploadService);
            }
        }

    }

}

