package uk.ac.nottingham.eaxtp1.CradleRideLogger;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.text.TextUtils;

import java.util.Arrays;
import java.util.List;

import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.autoStopOn;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.crashed;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.forcedStop;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.recording;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.WifiReceiver.wifiConnected;

@SuppressWarnings("MissingPermission")
public class GPSService extends Service implements LocationListener {
    public GPSService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    PowerManager.WakeLock wakelock;
    long wakelockTimeout = 5 * 60 * 60 * 1000;  // 5 hour timeout to remove AndroidStudio warning.

    Notification.Builder builder;

    double lat, lon;
    static String sLat, sLong, sSpeed, sGPS = "0";
    String sAcc, sAlt, sBear, sRT, sGTime;
    static String gpsData;
    List<String> dataList;
    static short gpsSample;
    private long statSamples, movingSamples;
    // Number of GPS samples (seconds) before journey is considered "finished".
    long limit = 10*60;     //    TODO: Set limit to 10*60
    float speed;

    LocationManager myLocationManager;

    @Override
    public void onCreate() {
        super.onCreate();
        if (crashed) {
            onDestroy();
        }

        myLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (myLocationManager != null) {      // Mandatory check to remove AndroidStudio NullPointer warning
            myLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);
        }

        PowerManager myPowerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (myPowerManager != null) {      // Mandatory check to remove AndroidStudio NullPointer warning
            wakelock = myPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GPS WakeLock");
            wakelock.acquire(wakelockTimeout);
        }

    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (myLocationManager != null) {
            myLocationManager.removeUpdates(this);
        }

        gpsSample = 0;

        if (wakelock != null && wakelock.isHeld()) {
            wakelock.release();
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        lat = location.getLatitude();
        lon = location.getLongitude();
        speed = location.getSpeed();

        sLat = String.valueOf(lat);
        sLong = String.valueOf(lon);
        sSpeed = String.valueOf(speed);

        gpsSample++;
        sGPS = String.valueOf(gpsSample);

        sGTime = String.valueOf(location.getTime());
        sAcc = String.valueOf(location.getAccuracy());
        sAlt = String.valueOf(location.getAltitude());
        sBear = String.valueOf(location.getBearing());
        sRT = String.valueOf(location.getElapsedRealtimeNanos());

        dataList = Arrays.asList(sLat,sLong,sSpeed,sGTime,sAcc,sAlt,sBear,sRT);
        gpsData = TextUtils.join(",", dataList);

        if (autoStopOn) {
            stationaryChecker();
        }
    }

    public void stationaryChecker() {
        if (speed <= 2) {
            statSamples++;
            if (statSamples >= limit && wifiConnected){
                stopOthers();
                recording = false;
                forcedStop = true;
                stopNotification();
                onDestroy();
            }
        } else {
            if (movingSamples > 10 || speed > 10) {
                statSamples = 0;
                movingSamples = 0;
            } else {
                movingSamples++;
            }
        }
    }

    public void stopOthers() {
//        Intent stopRecording = new Intent(this, RecordingService.class);
        Intent stopAudio = new Intent(this, AudioService.class);
        Intent stopIMU = new Intent(this, IMUService.class);
        Intent stopLogging = new Intent(this, LoggingService.class);
//        this.stopService(stopRecording);
        this.stopService(stopAudio);
        this.stopService(stopIMU);
        this.stopService(stopLogging);
    }

    public void stopNotification() {
        builder = new Notification.Builder(this)
                .setSmallIcon(R.drawable.stop_symb)
                .setContentTitle("CradleRide Logger")
                .setContentText("Recording stopped due to lack of movement.");

        NotificationManager manager =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) {      // Mandatory check to remove AndroidStudio NullPointer warning
            manager.notify(1, builder.build());
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
