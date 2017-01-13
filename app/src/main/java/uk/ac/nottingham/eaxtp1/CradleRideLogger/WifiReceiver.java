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

    private int SERVICE_STARTED = 0;
    static int jobNumber = 1;

    @Override
    public void onReceive(Context context, Intent intent) {



        Intent uploadService = new Intent(context, UploadIntentService.class);
        uploadService.putExtra("jobNumber", jobNumber);

        NetworkInfo info = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);

        if (info != null && info.isConnected()) {

//            Shows that the receiver has been called
            Toast.makeText(context, "This is the receiver", Toast.LENGTH_SHORT).show();

//            Allocates where to look for the files to be uploaded
            String folder = String.valueOf(context.getExternalFilesDir("New"));
            File directory = new File(folder);
            File[] contents = directory.listFiles();

            if (contents == null) {

                Toast.makeText(context, "No folder here...", Toast.LENGTH_SHORT).show();

            } else if (contents.length != 0) {

                Toast.makeText(context, "We have files!", Toast.LENGTH_SHORT).show();

//                Start the file upload service
                context.startService(uploadService);
                SERVICE_STARTED = 1;
                jobNumber++;

            } else {
                Toast.makeText(context, "No files here...", Toast.LENGTH_SHORT).show();
            }

        } else if (info != null && !info.isConnected() && SERVICE_STARTED == 1) {
            context.stopService(uploadService);
        }

    }

}

