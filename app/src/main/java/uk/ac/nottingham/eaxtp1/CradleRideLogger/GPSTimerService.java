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
import android.text.TextUtils;

import java.util.Arrays;
import java.util.List;

import static uk.ac.nottingham.eaxtp1.CradleRideLogger.GPSService.gpsData;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.crashed;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.recording;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.gpsOff;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.timerFilter;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.timerInt;

public class GPSTimerService extends Service implements LocationListener, GpsStatus.Listener {
    public GPSTimerService() {}

//    This service handles the GPS lock and the timers needed for the app to run smoothly.
//    The main purpose is to delay the start of recording until GPS is fixed, and thus the
// locations of the comfort can be seen.
//    Positioning Timer allows time from 'Start' being pressed to data recording, allowing time
// for the user to position the phone (if need be).
//    GPS Timer cancels the recording if the GPS has not locked after a reasonable amount of time.
//    Start Buffer simply delays the recording (after GPS lock) to protect the user's starting
// location.
//    GPS Removal Timer allows the full GPS service time to start up and initialise before
// killing this one.

    CountDownTimer timeoutTimer, positionTimer, removalTimer, startBuffer;

    protected LocationManager myLocationManager;
    //    Declare variables for the GPS fix-check
    boolean gpsFixed, positioned, buffFinished, ambGPSOff; static boolean buffering;
    long myLastLocationMillis;
    Location myLastLocation;

    int timeDelay, timeOut, bs;

    @SuppressLint("MissingPermission")
    @Override
    public void onCreate() {
        super.onCreate();

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);

//        Retrieve and initialise positioning time and start buffer
        timeDelay = preferences.getInt(getString(R.string.key_pref_delay), getResources().getInteger(R.integer.delay_default)) * 1000;
        bs = preferences.getInt(getString(R.string.key_pref_buff_start), getResources().getInteger(R.integer.buff_default)) * 60000;

        positioned = false; buffFinished = false;   buffering = false;

        if (!gpsOff) {

            timeOut = preferences.getInt(getString(R.string.key_pref_timeout), getResources().getInteger(R.integer.timeout_default)) * 60000;   // Retrieve timeout

            myLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            //noinspection ConstantConditions
            myLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);
            myLocationManager.addGpsStatusListener(this);
            gpsTimer(); // Cancel GPS (and recording) if no GPS signal within requested time
        }

        posTimer(); // Allow time for phone positioning before recording

    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

//    Feed back info to Main Activity
    private void sendBroadcast(int response) {
        Intent intent = new Intent(timerFilter);
        intent.putExtra(timerInt, response);
        sendBroadcast(intent);
    }

//    ** SET UP TIMERS **
//    Cancel GPS (and recording) if no GPS signal within requested time
    public void gpsTimer() {

        timeoutTimer = new CountDownTimer(timeOut, timeOut) {

            public void onTick(long millisUntilFinished) {}

            @Override
            public void onFinish() {
                if (!recording) {
                    sendBroadcast(0);
                }
            }
        }.start();
    }

//    Allow time for phone positioning before recording
    public void posTimer() {

        positionTimer = new CountDownTimer(timeDelay, timeDelay) {
            @Override
            public void onTick(long millisUntilFinished) {}

            @Override
            public void onFinish() {
                positioned = true;
                if (gpsOff) {
                    sendBroadcast(1);   // Start recording
                }
            }
        }.start();
    }

    public void buffTheStart() {
        if ( bs == 0) { // No buffer
            sendBroadcast(1);   // Start recording
            gpsRemoval();   // Start GPS removal timer - need to delay to enable GPS service to start

        } else {    // Wait for buffer before recording data
            buffering = true;
            sendBroadcast(2);   // Let User know GPS is fixed, but waiting for buffer before recording.
            startBuffer = new CountDownTimer(bs, bs) {
                @Override
                public void onTick(long millisUntilFinished) {}

                @Override
                public void onFinish() {
                    buffFinished = true;    buffering = false;
                    sendBroadcast(1);   // Start recording
                    gpsRemoval();   // Start GPS removal timer - need to delay to enable GPS service to start
                }
            }.start();
        }
    }

// Start GPS removal timer - need to delay to enable GPS service to start
    public void gpsRemoval() {
        removalTimer = new CountDownTimer(1000,1000) {
            public void onTick(long millisUntilFinished) {}

            @Override
            public void onFinish() {
                myLocationManager.removeUpdates(GPSTimerService.this);
                stopSelf();
            }
        }.start();
    }

//    Decide whether GPS is locked or not
    @Override
    public void onGpsStatusChanged(int event) {
        if (!recording) {
//        Ensures the GPS is fixed before the user starts recording
            switch (event) {
                case GpsStatus.GPS_EVENT_SATELLITE_STATUS:
                    if (myLastLocation != null)
                        gpsFixed = (SystemClock.elapsedRealtime() - myLastLocationMillis) < 3000;

                    if (gpsFixed && positioned) {
                        timeoutTimer.cancel();  // Cancel GPS timeout as GPS is locked

                        if ( bs == 0) { // No buffer
                            sendBroadcast(1);   // Start the recording
                            gpsRemoval();   // Start GPS removal timer - need to delay to enable GPS service to start

                        } else {    // Apply start buffer to protect user's location
                            buffering = true;
                            sendBroadcast(2);   // Let User know GPS is fixed, but waiting for buffer before recording.
                            buffTheStart();
                        }
                    }

                    break;
                case GpsStatus.GPS_EVENT_FIRST_FIX:

                    gpsFixed = true;

                    break;
            }
        }
    }

//    Store GPS data when available - give starting location data to the recording
    @Override
    public void onLocationChanged(Location location) {
        if (!crashed) {
            if (location == null) return;

            if (!recording) {
                myLastLocationMillis = SystemClock.elapsedRealtime();

                myLastLocation = location;

                String sLat   = String.valueOf(location.getLatitude());
                String sLong  = String.valueOf(location.getLongitude());
                String sSpeed = String.valueOf(location.getSpeed());
                String sGTime = String.valueOf(location.getTime());
                String sAcc   = String.valueOf(location.getAccuracy());
                String sAlt   = String.valueOf(location.getAltitude());
                String sBear  = String.valueOf(location.getBearing());
                String sRT    = String.valueOf(location.getElapsedRealtimeNanos());

                List<String> dataList = Arrays.asList(sLat, sLong, sSpeed, sGTime, sAcc, sAlt, sBear, sRT);
                gpsData = TextUtils.join(",", dataList);
            }

            if (BuildConfig.AMB_MODE && !ambGPSOff) {   // Cancel AMB-specific GPS
                stopService(new Intent(getApplicationContext(), AmbGPSService.class));
                ambGPSOff = true;
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (!buffFinished) {
            sendBroadcast(3);   // Cancel Main Activity's Broadcast Receiver
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
    public void onStatusChanged(String provider, int status, Bundle extras) {}

    @Override
    public void onProviderEnabled(String provider) {}

    @Override
    public void onProviderDisabled(String provider) {}
}
