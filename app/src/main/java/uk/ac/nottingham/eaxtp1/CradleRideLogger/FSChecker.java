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

import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.KEY_F_CHECK;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.KEY_FS;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.KEY_G;

public class FSChecker extends Service implements SensorEventListener {
    public FSChecker() {
    }

    final String TAG = "FSChecker";

    SharedPreferences preferences;
    SharedPreferences.Editor prefEditor;
    SensorManager manager;
    Sensor acc, gyro;

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
            gyro = manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);

            manager.registerListener(this, acc, SensorManager.SENSOR_DELAY_FASTEST);
            manager.registerListener(this, gyro, SensorManager.SENSOR_DELAY_FASTEST);

            startTime = System.currentTimeMillis();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        preferences = getSharedPreferences(getString(R.string.pref_main), MODE_PRIVATE);
        prefEditor = preferences.edit();
        prefEditor.putBoolean(KEY_G, gPresent);
        prefEditor.putInt(KEY_FS, fSample);
        prefEditor.putBoolean(KEY_F_CHECK, false);
        prefEditor.commit();

    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        switch (event.sensor.getType()) {
            case Sensor.TYPE_ACCELEROMETER:

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
                    Log.i(TAG, "Seconds: " + now);
                    manager.unregisterListener(this, acc);
                    onDestroy();
                }

                break;

            case Sensor.TYPE_GYROSCOPE:

                gPresent = true;
                manager.unregisterListener(this, gyro);
                Log.i(TAG, "Gyro Present: " + gPresent);

                break;

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