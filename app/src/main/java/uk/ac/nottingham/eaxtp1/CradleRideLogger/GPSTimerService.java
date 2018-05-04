package uk.ac.nottingham.eaxtp1.CradleRideLogger;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.IBinder;
import android.os.SystemClock;
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;
import android.text.TextUtils;
import android.util.Log;

import java.util.Arrays;
import java.util.List;

import static uk.ac.nottingham.eaxtp1.CradleRideLogger.GPSService.gpsData;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.crashed;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.keyBuffStart;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.keyDelay;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.keyTimeout;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.recording;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.gpsOff;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.timerFilter;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.timerInt;

public class GPSTimerService extends Service implements LocationListener, GpsStatus.Listener {
    public GPSTimerService() {
    }

    String TAG = "CRL_GPS Timer Service";

    CountDownTimer timeoutTimer, positionTimer, removalTimer, startBuffer;
    SharedPreferences preferences;

    protected LocationManager myLocationManager;
    //    Sets up variables for the GPS fix-check
    boolean gpsFixed, positioned;
    long myLastLocationMillis;
    Location myLastLocation;

    int timeDelay, timeOut;

    @SuppressLint("MissingPermission")
    @Override
    public void onCreate() {
        super.onCreate();

        preferences = getSharedPreferences("myPreferences", MODE_PRIVATE);
        if (preferences.contains(keyDelay)) {
            timeDelay = preferences.getInt(keyDelay, 10) * 1000;
        } else {
            timeDelay = 0;
        }

        positioned = false;

        if (!gpsOff) {

            if (preferences.contains(keyTimeout)) {
                timeOut = preferences.getInt(keyTimeout, 60);
            } else {
                timeOut = 60;
            }

            myLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            //noinspection ConstantConditions
            myLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);
            myLocationManager.addGpsStatusListener(this);
            gpsTimer();
        }

        posTimer();

    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void sendBroadcast(int response) {
        Intent intent = new Intent(timerFilter);
        intent.putExtra(timerInt, response);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    public void gpsTimer() {

        timeoutTimer = new CountDownTimer(timeOut*1000, timeOut*1000) {

            public void onTick(long millisUntilFinished) {}

            @Override
            public void onFinish() {
                if (!recording) {
                    Log.i(TAG, "GPS Timed Out");
                    sendBroadcast(0);
                }
            }
        }.start();
    }

    public void posTimer() {

        positionTimer = new CountDownTimer(timeDelay, timeDelay) {
            @Override
            public void onTick(long millisUntilFinished) {}

            @Override
            public void onFinish() {
                Log.i(TAG, "Positioned");
                positioned = true;          // Allow time for phone positioning before recording
                if (gpsOff) {
                    sendBroadcast(1);
                }
            }
        }.start();
    }

    @Override
    public void onGpsStatusChanged(int event) {
        if (!recording) {

//        Ensures the GPS is fixed before the user starts recording
            switch (event) {
                case GpsStatus.GPS_EVENT_SATELLITE_STATUS:
                    if (myLastLocation != null)
                        gpsFixed = (SystemClock.elapsedRealtime() - myLastLocationMillis) < 3000;

                    if (gpsFixed && positioned) {
//                        Log.i(TAG, "Fixed and in Position");
                        timeoutTimer.cancel();
                        buffTheStart();
                    }

                    break;
                case GpsStatus.GPS_EVENT_FIRST_FIX:

                    gpsFixed = true;

                    break;
            }
        }
    }

    public void buffTheStart() {
        int bs = preferences.getInt(keyBuffStart, 0) * 60000;
        if ( bs == 0) {
            sendBroadcast(1);
            gpsRemoval();
        } else {
            sendBroadcast(2);   // Let User know GPS is fixed, but waiting for buffer before recording.
            startBuffer = new CountDownTimer(bs, bs) {
                @Override
                public void onTick(long millisUntilFinished) {}

                @Override
                public void onFinish() {
                    sendBroadcast(1);
                    gpsRemoval();
                }
            }.start();
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        if (!crashed) {
            if (location == null) return;

            if (!recording) {
                myLastLocationMillis = SystemClock.elapsedRealtime();

                myLastLocation = location;

                String sLat = String.valueOf(location.getLatitude());
                String sLong = String.valueOf(location.getLongitude());
                String sSpeed = String.valueOf(location.getSpeed());
                String sGTime = String.valueOf(location.getTime());
                String sAcc = String.valueOf(location.getAccuracy());
                String sAlt = String.valueOf(location.getAltitude());
                String sBear = String.valueOf(location.getBearing());
                String sRT = String.valueOf(location.getElapsedRealtimeNanos());

                List<String> dataList = Arrays.asList(sLat, sLong, sSpeed, sGTime, sAcc, sAlt, sBear, sRT);
                gpsData = TextUtils.join(",", dataList);
            }
        }
    }

    // Allow time for phone positioning before recording - less cut-off needed in analysis?
    public void gpsRemoval() {
        removalTimer = new CountDownTimer(1000,1000) {
            public void onTick(long millisUntilFinished) {}

            @Override
            public void onFinish() {
                myLocationManager.removeUpdates(GPSTimerService.this);
            }
        }.start();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (timeoutTimer != null) {
            timeoutTimer.cancel();
        }
        if (positionTimer != null) {
            positionTimer.cancel();
        }
        if (removalTimer != null) {
            removalTimer.cancel();
        }
        if (startBuffer != null) {
            startBuffer.cancel();
        }
        if (myLocationManager != null) {
            myLocationManager.removeUpdates(this);
        }
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }

    @Override
    public void onProviderEnabled(String provider) {

    }

    @Override
    public void onProviderDisabled(String provider) {

    }
}
