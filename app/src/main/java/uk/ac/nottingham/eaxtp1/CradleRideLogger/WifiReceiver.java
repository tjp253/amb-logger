package uk.ac.nottingham.eaxtp1.CradleRideLogger;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.widget.Toast;

import java.io.File;

public class WifiReceiver extends BroadcastReceiver {
    public WifiReceiver() {
    }

    String mainPath, zipPath;

    private int SERVICE_STARTED = 0;
    int jobNumber;

    @Override
    public void onReceive(Context context, Intent intent) {

        mainPath = String.valueOf(context.getExternalFilesDir(""));
        zipPath = mainPath + "/Zipped";

        Intent uploadService = new Intent(context, UploadService.class);

        NetworkInfo info = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);

        if (info != null && info.isConnected()) {

//            Shows that the receiver has been called
//            Toast.makeText(context, "This is the receiver", Toast.LENGTH_SHORT).show();

//            Allocates where to look for the files to be uploaded
//            String folder = String.valueOf(context.getExternalFilesDir("Zipped"));
            File directory = new File(zipPath);
            File[] contents = directory.listFiles();

            if ((contents != null) && (contents.length != 0)) {

//                Toast.makeText(context, "No folder here...", Toast.LENGTH_SHORT).show();

//            } else if (contents.length != 0) {

//                Toast.makeText(context, "We have files!", Toast.LENGTH_SHORT).show();

//                int filesLeft = contents.length;

//                jobNumber = 1;
//
//                while (filesLeft > 0) {
//
//                    uploadService.putExtra("jobNumber", jobNumber);

                    context.startService(uploadService);

//                    jobNumber++;

//                    filesLeft = filesLeft - 1;
//                }

                SERVICE_STARTED = 1;


//            } else {
////                Toast.makeText(context, "No files here...", Toast.LENGTH_SHORT).show();
            }

        } else if (info != null && !info.isConnected() && SERVICE_STARTED == 1) {
            context.stopService(uploadService);
        }

    }

}

