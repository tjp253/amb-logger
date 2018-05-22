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
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;
import android.text.TextUtils;
import android.util.Log;

import java.util.Arrays;
import java.util.List;

import static uk.ac.nottingham.eaxtp1.CradleRideLogger.GPSService.gpsData;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.crashed;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.recording;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.gpsOff;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.timerFilter;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.timerInt;

public class GPSTimerService extends Service implements LocationListener, GpsStatus.Listener {
    public GPSTimerService() {
    }

    final String TAG = "CRL_GPS Timer Service";

    CountDownTimer timeoutTimer, positionTimer, removalTimer, startBuffer;
    SharedPreferences preferences;

    protected LocationManager myLocationManager;
    //    Sets up variables for the GPS fix-check
    boolean gpsFixed, positioned, buffFinished, ambGPSOff; static boolean buffering;
    long myLastLocationMillis;
    Location myLastLocation;

    int timeDelay, timeOut;

    @SuppressLint("MissingPermission")
    @Override
    public void onCreate() {
        super.onCreate();

        preferences = PreferenceManager.getDefaultSharedPreferences(this);

        timeDelay = preferences.getInt(getString(R.string.key_pref_delay), getResources().getInteger(R.integer.delay_default)) * 1000;

        positioned = false; buffFinished = false;   buffering = false;

        if (!gpsOff) {

            timeOut = preferences.getInt(getString(R.string.key_pref_timeout), getResources().getInteger(R.integer.timeout_default)) * 60000;

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

        timeoutTimer = new CountDownTimer(timeOut, timeOut) {

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
        int bs = preferences.getInt(getString(R.string.key_pref_buff_start), getResources().getInteger(R.integer.buff_default)) * 60000;
        if ( bs == 0) {
            sendBroadcast(1);
            gpsRemoval();
        } else {
            buffering = true;
            sendBroadcast(2);   // Let User know GPS is fixed, but waiting for buffer before recording.
            startBuffer = new CountDownTimer(bs, bs) {
                @Override
                public void onTick(long millisUntilFinished) {}

                @Override
                public void onFinish() {
                    buffFinished = true;    buffering = false;
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

            if (BuildConfig.AMB_MODE && !ambGPSOff) {
                Intent stopAmbGPS = new Intent(getApplicationContext(), AmbGPSService.class);
                stopService(stopAmbGPS);
                ambGPSOff = true;
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

        if (!buffFinished) {
            sendBroadcast(3);
            buffering = false;
        }

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
