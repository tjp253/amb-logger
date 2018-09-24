package uk.ac.nottingham.eaxtp1.CradleRideLogger;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.util.Log;

public class NetworkReceiver extends BroadcastReceiver {
    public NetworkReceiver() {}

    // Checks whether the phone is connected to AND USING wifi. If it is, uploads can go ahead.
    // Otherwise, stop all uploads to save mobile data.

    String TAG = "CRL_NetworkReceiver";

    static boolean wifiConnected = false;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!WifiManager.NETWORK_STATE_CHANGED_ACTION.equals(intent.getAction())) return;   // Stop AndroidStudio warning me!

        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            NetworkInfo info = cm.getActiveNetworkInfo();

            if (info != null && info.getType() == ConnectivityManager.TYPE_WIFI && !wifiConnected) {

                wifiConnected = true;

                Log.i(TAG, "Wifi connected.");

            } else if (info != null && info.getType() != ConnectivityManager.TYPE_WIFI && wifiConnected) {

                wifiConnected = false;

                Log.i(TAG, "Wifi not connected.");

                context.stopService(new Intent(context, UploadService.class));
            }
        }
    }

}

