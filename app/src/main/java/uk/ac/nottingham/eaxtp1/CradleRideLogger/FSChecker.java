package uk.ac.nottingham.eaxtp1.CradleRideLogger;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.IBinder;
import android.util.Log;

import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.keyFCheck;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.keyFS;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.keyG;

public class FSChecker extends Service implements SensorEventListener {
    public FSChecker() {
    }

    String TAG = "FSChecker";

    SharedPreferences preferences;
    SharedPreferences.Editor prefEditor;
    SensorManager manager;
    Sensor acc, gravity;

    boolean gPresent;
    int nSamples, fSample;
    long startTime;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "onCreate");
        manager = (SensorManager) getSystemService(SENSOR_SERVICE);
        if (manager != null) {      // Mandatory check to remove AndroidStudio NullPointer warning
            acc = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            gravity = manager.getDefaultSensor(Sensor.TYPE_GRAVITY);

            manager.registerListener(this, acc, SensorManager.SENSOR_DELAY_FASTEST);
            manager.registerListener(this, gravity, SensorManager.SENSOR_DELAY_FASTEST);

            startTime = System.currentTimeMillis();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        preferences = getSharedPreferences("myPreferences", MODE_PRIVATE);
        prefEditor = preferences.edit();
        prefEditor.putBoolean(keyG, gPresent);
        prefEditor.putInt(keyFS, fSample);
        prefEditor.putBoolean(keyFCheck, false);
        prefEditor.commit();

    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        int sensor = event.sensor.getType();

        if (sensor == Sensor.TYPE_ACCELEROMETER){
//            nSamples++;
            int now = (int) (System.currentTimeMillis() - startTime) / 1000;

            if (now >= 5) {
                nSamples++;
            }
            if (now >= 15) {
                now -= 5 ;
                fSample = ( nSamples / now ) + 1 ;
                Log.i(TAG, "Sample Frequency: " + fSample);
                Log.i(TAG, "Samples: " + nSamples);
                Log.i(TAG, "Now: " + now);
                manager.unregisterListener(this, acc);
                onDestroy();
            }

        } else if (sensor == Sensor.TYPE_GRAVITY){
            gPresent = true;
            manager.unregisterListener(this, gravity);
            Log.i(TAG, "Gravity Present: " + gPresent);
        }

    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
