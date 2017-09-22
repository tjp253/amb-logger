package uk.ac.nottingham.eaxtp1.CradleRideLogger;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
//import android.util.Log;

import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.crashed;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.forcedStop;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.recording;

@SuppressWarnings("MissingPermission")
public class GPSService extends Service implements LocationListener {
    public GPSService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    PowerManager.WakeLock wakelock;

    double lat, lon;
    static String sLat, sLong, sSpeed, sGPS = "0";
    static int gpsSample;
    private long statSamples, movingSamples;
//    TODO: Set 'limit' to reasonable value. 5 minutes? 10 minutes?
    long limit = 10*60;     // Number of GPS samples (seconds) before journey is considered "finished".
    float speed;


    LocationManager myLocationManager;

    @Override
    public void onCreate() {
        super.onCreate();
        if (crashed) {
            onDestroy();
        }

        myLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        myLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);

        PowerManager myPowerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakelock = myPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GPS WakeLock");
        wakelock.acquire();

    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (myLocationManager != null) {
            myLocationManager.removeUpdates(this);
        }

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

        if (speed <= 2) {
            statSamples++;
            if (statSamples >= limit){
//                Log.i("GPS Service", "Stationary for too long");
                Intent stopRecording = new Intent(this, RecordingService.class);
                Intent stopAudio = new Intent(this, AudioService.class);
                this.stopService(stopRecording);
                this.stopService(stopAudio);
                recording = false;
                forcedStop = true;
                onDestroy();
            }
        } else {
            if (movingSamples > 10 || speed > 10) {
                statSamples = 0;
                movingSamples = 0;
//                Log.i("GPS Service", "Fast, fast, fast.");
            } else {
                movingSamples++;
            }
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
