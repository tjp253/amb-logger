package uk.ac.nottingham.AmbLogger.Recording;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.GnssStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.IBinder;
import android.os.SystemClock;
import android.preference.PreferenceManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import static uk.ac.nottingham.AmbLogger.MainActivity.TIMER_BROADCAST_KILLED;
import static uk.ac.nottingham.AmbLogger.MainActivity.TIMER_BROADCAST_START;
import static uk.ac.nottingham.AmbLogger.MainActivity.TIMER_BROADCAST_TIMEOUT;
import static uk.ac.nottingham.AmbLogger.Recording.GPSService.gpsData;
import static uk.ac.nottingham.AmbLogger.MainActivity.crashed;
import static uk.ac.nottingham.AmbLogger.MainActivity.recording;
import static uk.ac.nottingham.AmbLogger.MainActivity.gpsOff;
import static uk.ac.nottingham.AmbLogger.MainActivity.timerFilter;
import static uk.ac.nottingham.AmbLogger.MainActivity.timerInt;

import uk.ac.nottingham.AmbLogger.AmbSpecific.InitialiseGPS;
import uk.ac.nottingham.AmbLogger.BuildConfig;
import uk.ac.nottingham.AmbLogger.R;
import uk.ac.nottingham.AmbLogger.Utilities.NotificationUtilities;

public class GPSTimerService extends Service implements LocationListener {
    public GPSTimerService() {}

//    This service handles the GPS lock and the timers needed for the app to run smoothly.
//    The main purpose is to delay the start of recording until GPS is fixed, and thus the
// locations of the comfort can be seen.
//    Positioning Timer allows time from 'Start' being pressed to data recording, allowing time
// for the user to position the phone (if need be).
//    GPS Timer cancels the recording if the GPS has not locked after a reasonable amount of time.
//    GPS Removal Timer allows the full GPS service time to start up and initialise before
// killing this one.

    GnssStatus.Callback myGnssStatusCallback;

    CountDownTimer timeoutTimer, positionTimer, removalTimer;

    protected LocationManager myLocationManager;
    //    Declare variables for the GPS fix-check
        boolean gpsFixed, positioned, ambGPSOff;
    long myLastLocationMillis;
    Location myLastLocation;

    int timeDelay, timeOut;

    @SuppressLint("MissingPermission")
    @Override
    public void onCreate() {
        super.onCreate();

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);

        // Retrieve and initialise positioning time
        timeDelay = preferences.getInt(getString(R.string.key_pref_delay), getResources().getInteger(R.integer.delay_default)) * 1000;

        positioned = false;

        if (!gpsOff) {

            timeOut = preferences.getInt(getString(R.string.key_pref_timeout), getResources().getInteger(R.integer.timeout_default)) * 60000;   // Retrieve timeout

            myGnssStatusCallback = new GnssStatus.Callback() {
                @Override
                public void onSatelliteStatusChanged(@NonNull GnssStatus status) {
                    // Decide if there's a reasonable location fix
                    if (recording) {
                        return; // Don't care. Already found a fix.
                    }

                    // Ensure the GPS is fixed before the user starts recording

                    if (myLastLocation != null)
                        gpsFixed = (SystemClock.elapsedRealtime() - myLastLocationMillis) < 3000;

                    if (gpsFixed && positioned) {
                        timeoutTimer.cancel();  // Cancel GPS timeout as GPS is locked

                        sendBroadcast(TIMER_BROADCAST_START);   // Start the recording
                        gpsRemoval();   // Start GPS removal timer - need to delay to enable GPS service to start
                    }
                }

                @Override
                public void onFirstFix(int ttffMillis) {
                    gpsFixed = true; //  Can start recording
                }
            };

            myLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            myLocationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER, 0, 0, this
            );
            myLocationManager.registerGnssStatusCallback(myGnssStatusCallback);
            gpsTimer(); // Cancel GPS (and recording) if no GPS signal within requested time
        }

        posTimer(); // Allow time for phone positioning before recording

        // Cancel all previous notifications.
        new NotificationUtilities(this).cancelNotifications(true);

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
                    sendBroadcast(TIMER_BROADCAST_TIMEOUT);
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
                    sendBroadcast(TIMER_BROADCAST_START);   // Start recording
                }
            }
        }.start();
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

//    Store GPS data when available - give starting location data to the recording
    @Override
    public void onLocationChanged(Location location) {
        if (!crashed) {
            if (location == null) return;

            if (!recording) {
                myLastLocationMillis = SystemClock.elapsedRealtime();

                myLastLocation = location;

                gpsData.clear();

                gpsData.add(Double.toString(location.getLatitude()));
                gpsData.add(Double.toString(location.getLongitude()));
                gpsData.add(Float.toString(location.getSpeed()));
                gpsData.add(Long.toString(location.getTime()));
                gpsData.add(Float.toString(location.getAccuracy()));
                gpsData.add(Double.toString(location.getAltitude()));
                gpsData.add(Float.toString(location.getBearing()));
                gpsData.add(Long.toString(location.getElapsedRealtimeNanos()));
            }

            if (!BuildConfig.TEST_MODE && !ambGPSOff) {   // Cancel AMB-specific GPS
                stopService(new Intent(getApplicationContext(), InitialiseGPS.class));
                ambGPSOff = true;
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        sendBroadcast(TIMER_BROADCAST_KILLED);   // Cancel Main Activity's Broadcast Receiver

        if (timeoutTimer != null) {
            timeoutTimer.cancel();
        }
        if (positionTimer != null) {
            positionTimer.cancel();
        }
        if (removalTimer != null) {
            removalTimer.cancel();
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
