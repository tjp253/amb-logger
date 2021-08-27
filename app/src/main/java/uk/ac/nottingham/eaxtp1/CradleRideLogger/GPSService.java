package uk.ac.nottingham.eaxtp1.CradleRideLogger;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.preference.PreferenceManager;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.crashed;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.forcedStop;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.gpsBool;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.gpsFilter;

import com.google.gson.Gson;

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

    Resources res;

    static String sGPS = "";
    static List<String> gpsData = new ArrayList<>();
    long gpsSample;
    static long gpsSampleTime;
    private byte movingSamples;
    // Number of GPS samples (seconds) before journey is considered "finished".
    int maxAccuracy, speedMin, speedMoving;
    float speed, accuracy;
    static boolean autoStopOn, wifiCheckOn, timerOn_Slow, atHospital;
    double lat, lon;

    Intent autoStopTimerService;

    LocationManager myLocationManager;

    @Override
    public void onCreate() {
        super.onCreate();
        if (crashed) {
            onDestroy();
        }

        if (gpsData.isEmpty()) {
            sGPS = "";
        } else {
            gpsSample = 1;
            sGPS = "1";
        }

        gpsSampleTime = 0;

        res = getResources();
        maxAccuracy = res.getInteger(R.integer.acc_max);
        speedMin = res.getInteger(R.integer.speed_min);
        speedMoving = res.getInteger(R.integer.speed_moving);

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

        startForeground(notUtils.FOREGROUND_INT,notUtils.getForegroundNotification().build());

        autoStopTimerService = new Intent(getApplicationContext(),AutoStopTimerService.class);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (myLocationManager != null) {
            myLocationManager.removeUpdates(this);
        }

        wifiCheckOn = false;
        timerOn_Slow = false;

        sendBroadcast(forcedStop);

        if (wakelock != null && wakelock.isHeld()) {
            wakelock.release();
        }
    }

//    Store GPS data for logging
    @Override
    public void onLocationChanged(Location location) {
        gpsSample++;
        gpsSampleTime = System.currentTimeMillis();
        sGPS = Long.toString(gpsSample);

        // Needed for AutoStop coordinate comparisons
        lat = location.getLatitude();
        lon = location.getLongitude();

        speed = location.getSpeed();    // Needed for movement check

        accuracy = location.getAccuracy();

        gpsData.clear();

        gpsData.add(Double.toString(lat));
        gpsData.add(Double.toString(lon));
        gpsData.add(Float.toString(speed));
        gpsData.add(Long.toString(location.getTime()));
        gpsData.add(Float.toString(accuracy));
        gpsData.add(Double.toString(location.getAltitude()));
        gpsData.add(Float.toString(location.getBearing()));
        gpsData.add(Long.toString(location.getElapsedRealtimeNanos()));

        if (autoStopOn) {
            stationaryChecker();
        }
    }

//    Check for vehicle movement. High radial accuracy tends to imply the device is in a building.
    public void stationaryChecker() {
        if (speed <= speedMin || accuracy > maxAccuracy) {
            // If speed is less than 5 miles per hour or the radial accuracy is over 35m.
            // Using 35 metres here (instead of 20 metres in post-processing) to be safe.

            if (!timerOn_Slow) {
                movingSamples = 0; // Reset number of moving samples to zero
                // Start the AutoStop timer service to check the limits have passed.
                startService(autoStopTimerService);
                timerOn_Slow = true;
            }

            compareCoordinates();

        } else if (timerOn_Slow) {

            if (accuracy > maxAccuracy) {
                return;
            }

            if (speed > speedMoving || movingSamples > 10) { // True (?) movement detected
                stopService(autoStopTimerService);
                timerOn_Slow = false;
                atHospital = false;
            } else {    // Count towards possible movement
                compareCoordinates();
                movingSamples++;
            }

        }
    }

    double[][] hospCoords;

    // Compare current coordinates against a list of hospital coordinates to determine if journey
    // has "arrived"
    public void compareCoordinates() {
        if (!BuildConfig.AMB_MODE || atHospital) {
            return; // Don't need to compare
        }
        if (hospCoords == null) { // Initialise values of hospital coordinates
            try {

                InputStream in = this.getAssets().open("hosp_coords.json");
                byte[] buffer = new byte[in.available()];
                in.read(buffer);
                in.close();

                hospCoords = new Gson().fromJson(
                        new String(buffer, StandardCharsets.UTF_8), JSONCoords.class
                ).coords;

            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        double lat_rad = Math.toRadians(lat), lon_rad = Math.toRadians(lon);

        for (double[] hosp : hospCoords) {
            if (haversine(hosp[0], hosp[1], lat_rad, lon_rad) <= 0.02) {
                atHospital = true;
                return;
            }
        }

    }

    // Calculate the distance between two sets of coordinates.
    public double haversine(double lat1, double lon1, double lat2, double lon2) {
        double angle = Math.pow(Math.sin((lat2 - lat1) / 2), 2)
                + Math.pow((Math.cos(lat2) * Math.cos(lat1) * Math.sin((lon2 - lon1) / 2)), 2);

        double surface_angle = 2 * Math.atan2(Math.sqrt(angle), Math.sqrt(1 - angle));

        return 6371 * surface_angle;
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

class JSONCoords { // Simple class to enable hospital coordinates to be read easily to arrays
    double[][] coords; // variable needs to have same name as the JSON field!
}
