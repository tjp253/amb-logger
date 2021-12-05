package uk.ac.nottingham.eaxtp1.CradleRideLogger.Utilities;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.IBinder;
import android.preference.PreferenceManager;

import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.KEY_F_CHECK;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.KEY_FS;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.KEY_G;

public class FSChecker extends Service implements SensorEventListener {
    public FSChecker() {}

//    Quick and simple service run when app is first installed and opened. This finds the IMU
// sampling frequency and whether a gyroscope sensor is present or not, and stores the
// information in the ShardPreferences. This helps decide what to log in the LoggingService.

    SensorManager manager;
    Sensor acc, gyro;

    boolean gPresent;
    int nSamples, fSample;
    long startTime;

//    Initialise IMU sensors
    @Override
    public void onCreate() {
        super.onCreate();
        manager = (SensorManager) getSystemService(SENSOR_SERVICE);
        if (manager != null) {      // Mandatory check to remove Android Studio NullPointer warning
            acc = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            gyro = manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);

            manager.registerListener(this, acc, SensorManager.SENSOR_DELAY_FASTEST);
            manager.registerListener(this, gyro, SensorManager.SENSOR_DELAY_FASTEST);

            startTime = System.currentTimeMillis();
        }
    }

//    Save the information to the preferences
    @Override
    public void onDestroy() {
        super.onDestroy();

        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor prefEditor = pref.edit();
        prefEditor.putBoolean(KEY_G, gPresent);
        prefEditor.putInt(KEY_FS, fSample);
        prefEditor.putBoolean(KEY_F_CHECK, false);
        prefEditor.apply();

    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        switch (event.sensor.getType()) {
            case Sensor.TYPE_ACCELEROMETER:

//                Wait 5 seconds for the IMU to sort itself out and then calculate the sample
// frequency from the 10 second period after.

                int duration = (int) (System.currentTimeMillis() - startTime) / 1000;

                if (duration >= 5) {
                    nSamples++;
                }
                if (duration >= 15) {
                    duration -= 5 ;
                    fSample = ( nSamples / duration ) + 1 ;
                    manager.unregisterListener(this, acc);
                    stopSelf();
                }

                break;

            case Sensor.TYPE_GYROSCOPE: // If result, gyro exists...

                gPresent = true;
                manager.unregisterListener(this, gyro);

                break;

        }

    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}