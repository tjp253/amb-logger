package uk.ac.nottingham.eaxtp1.CradleRideLogger;

import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.IBinder;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.text.TextUtils;

import java.util.Arrays;
import java.util.List;

import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.crashed;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.forcedStop;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.gpsBool;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.gpsFilter;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.recording;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.WifiCheckService.wifiConnected;

@SuppressWarnings("MissingPermission")
public class GPSService extends Service implements LocationListener {
    public GPSService() {}

//    Service to access and store GPS data for recording, to give locations of comfort.

    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    NotificationUtilities notUtils;

    PowerManager.WakeLock wakelock;
    long wakelockTimeout = 5 * 60 * 60 * 1000;  // 5 hour timeout to remove AndroidStudio warning.

    String sLat, sLong, sSpeed, sAcc, sAlt, sBear, sRT, sGTime;
    static String gpsData, sGPS = "";
    List<String> dataList;
    static short gpsSample;
    private long movingSamples;
    // Number of GPS samples (seconds) before journey is considered "finished".
    long limitStart = 10*60, limitMax = 20*60;     // TODO: Set limitStart to 10*60 (10 minutes)
    float speed;
    static boolean autoStopOn, wifiCheckOn;

    CountDownTimer stationaryTimer;
    boolean timerOn, atLimitStart;

    LocationManager myLocationManager;

    @Override
    public void onCreate() {
        super.onCreate();
        if (crashed) {
            onDestroy();
        }

        if (gpsData != null && !gpsData.equals("")) {
            gpsSample = 1;
            sGPS = "1";
        } else {
            gpsSample = 0;
            sGPS = "";
        }

//        AutoStop stops the recording if stationary for an extended period of time.
        autoStopOn = PreferenceManager.getDefaultSharedPreferences(this).getBoolean(getString(R.string.key_pref_as), true);

        myLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (myLocationManager != null) {   // Mandatory check to remove AndroidStudio NullPointer warning
            myLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);
        }

//        Stop the service from being destroyed
        PowerManager myPowerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (myPowerManager != null) {      // Mandatory check to remove AndroidStudio NullPointer warning
            wakelock = myPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GPSService:WakeLock");
            wakelock.acquire(wakelockTimeout);
        }

        notUtils = new NotificationUtilities(this);

        Notification.Builder notBuild = notUtils.getForegroundNotification();
        startForeground(getResources().getInteger(R.integer.foregroundID), notBuild.build());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (myLocationManager != null) {
            myLocationManager.removeUpdates(this);
        }

        wifiCheckOn = false;
        timerOn = false;

        gpsSample = 0;

        sendBroadcast(forcedStop);

        if (wakelock != null && wakelock.isHeld()) {
            wakelock.release();
        }
    }

//    Store GPS data for logging
    @Override
    public void onLocationChanged(Location location) {
        gpsSample++;
        sGPS = String.valueOf(gpsSample);

        speed = location.getSpeed();    // Needed for movement check

        sLat   = String.valueOf(location.getLatitude());
        sLong  = String.valueOf(location.getLongitude());
        sSpeed = String.valueOf(speed);
        sGTime = String.valueOf(location.getTime());
        sAcc   = String.valueOf(location.getAccuracy());
        sAlt   = String.valueOf(location.getAltitude());
        sBear  = String.valueOf(location.getBearing());
        sRT    = String.valueOf(location.getElapsedRealtimeNanos());

        dataList = Arrays.asList(sLat,sLong,sSpeed,sGTime,sAcc,sAlt,sBear,sRT);
        gpsData = TextUtils.join(",", dataList);

        if (autoStopOn) {
            stationaryChecker();
        }
    }

//    Check for vehicle movement
    public void stationaryChecker() {
        if (speed <= 2) {   // Less than 5 miles per hour

            if (!timerOn) {
                // Start a timer to check the limits have passed.
                statTimer();
                timerOn = true;
                atLimitStart = false;
            }

            if (atLimitStart && wifiConnected) {
                cancelRecording();
            }

        } else if (timerOn) {

            if (speed > 10 || movingSamples > 10) { // True (?) movement detected
                if (stationaryTimer != null) {
                    stationaryTimer.cancel();
                }
                movingSamples = 0;
                wifiCheckOn = false;
                timerOn = false;
            } else {    // Count towards possible movement
                movingSamples++;
            }

        }
    }

    // Countdown Timer to report if stationary limits have been met. This is rather than counting
    // the number of GPS samples.
    public void statTimer() {

        stationaryTimer = new CountDownTimer(limitMax*1000,limitStart*1000) {
            @Override
            public void onTick(long millisUntilFinish) {
                // Otherwise, it fires straight away
                if ( ! ( millisUntilFinish > (limitMax - limitStart)*1000 ) ) {
                    if (wifiConnected) {
                        cancelRecording();
                    } else {
                        atLimitStart = true;
                    }
                }
            }

            @Override
            public void onFinish() {
                // For some reason, timer doesn't get cancelled.
                if (recording && timerOn) {
                    cancelRecording();
                }
            }
        }.start();

        // As of API 26, Manifest-registered BroadcastReceivers are essentially disabled.
        // Therefore, Wifi needs to be 'manually' searched for. When stationary, a Job is created
        // which starts if connected to wifi and stops if then disconnected.
        JobUtilities jobUtils = new JobUtilities(this);
        jobUtils.getScheduler().schedule( jobUtils.wifiJob() );

        wifiCheckOn = true;

    }

    public void cancelRecording() {
        // Stop all services due to inactivity
        this.stopService(new Intent(this, AudioService.class));
        this.stopService(new Intent(this, IMUService.class));
        recording = false;
        forcedStop = true;
        if (BuildConfig.AMB_MODE) {
            Intent ambSelect = new Intent(getApplicationContext(), AmbSelect.class);
            ambSelect.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ambSelect.putExtra(getString(R.string.forcedIntent), true);
            startActivity(ambSelect);
        } else {
            this.stopService(new Intent(this, LoggingService.class));
        }

        Notification.Builder notBuild = notUtils.getStoppedNotification();
        notUtils.getManager().notify(getResources().getInteger(R.integer.stoppedID),notBuild.build());

        if (stationaryTimer != null) {
            stationaryTimer.cancel();
        }

        stopSelf();
    }

    // Tell MainActivity whether the recording has been stopped due to lack of movement or not.
    // This then enables the activity to refresh the screen and unregister the loggingReceiver in
    // case the screen is on.
    private void sendBroadcast(boolean stationary) {
        Intent intent = new Intent(gpsFilter);
        intent.putExtra(gpsBool, stationary);
        sendBroadcast(intent);
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {}

    @Override
    public void onProviderEnabled(String provider) {}

    @Override
    public void onProviderDisabled(String provider) {}
}
