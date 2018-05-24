package uk.ac.nottingham.eaxtp1.CradleRideLogger;

import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.opengl.Matrix;
import android.os.IBinder;
import android.os.PowerManager;
import android.text.TextUtils;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import static uk.ac.nottingham.eaxtp1.CradleRideLogger.AudioService.amp;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.GPSService.gpsData;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.GPSService.gpsSample;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.GPSService.sGPS;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.crashed;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.foreID;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.gpsOff;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.KEY_G;

public class IMUService extends Service implements SensorEventListener {
    public IMUService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    PowerManager.WakeLock wakeLock;
    long wakelockTimeout = 5 * 60 * 60 * 1000;  // 5 hour timeout to remove AndroidStudio warning.

    SensorManager manager;
    Sensor accelerometer, gravity, magnetic, gyroscope;
    boolean gyroPresent;

    private long sampleID;
    private short prevSample;

    String sID, sX, sY, sZ, sampleTime;
    String sGyX = "", sGyY = "", sGyZ = "", sE = "", sN = "", sD = "";
    List<String> outputList;
    String toQueue;
    long startTime, currTime;

    String sAmp = "";
    int prevAmp;

    private float[] deviceValues = new float[4];
    float fN, fE, fD;
    private float[] gravityValues = new float[3];
    private float[] magneticValues = new float[3];
    //    Matrices for converting from device to world coordinates
    private float[] rMatrix = new float[16];
    private float[] iMatrix = new float[16];
    private float[] worldMatrix = new float[16];
    private float[] inverse = new float[16];

    static Queue<String> myQ;

    @Override
    public void onCreate() {
        super.onCreate();

        myQ = new LinkedList<>();

        if(crashed) {
            onDestroy();
        } else {
            initialiseIMU();
        }

        Notification notification = new Notification.Builder(this)
                .setSmallIcon(R.drawable.ambulance_symb)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.recording_data)).build();

        startForeground(foreID, notification);
    }

    public void initialiseIMU() {
        SharedPreferences preferences = getSharedPreferences(getString(R.string.pref_main), MODE_PRIVATE);
        gyroPresent = preferences.getBoolean(KEY_G, true);

        manager = (SensorManager) getSystemService(SENSOR_SERVICE);
        if (manager != null) {
            accelerometer = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            manager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_FASTEST);

            if (gyroPresent) {
                gravity = manager.getDefaultSensor(Sensor.TYPE_GRAVITY);
                magnetic = manager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
                gyroscope = manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
                manager.registerListener(this, gravity, SensorManager.SENSOR_DELAY_FASTEST);
                manager.registerListener(this, magnetic, SensorManager.SENSOR_DELAY_FASTEST);
                manager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_FASTEST);

                deviceValues[3] = 0;
            }
        }

        PowerManager myPowerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (myPowerManager != null) {
            wakeLock = myPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "IMU WakeLock");
            if (!wakeLock.isHeld()) {
                wakeLock.acquire(wakelockTimeout);
            }
        }

        startTime = System.currentTimeMillis();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {

        switch (event.sensor.getType()) {
            case Sensor.TYPE_ACCELEROMETER:

                currTime = System.currentTimeMillis();
                if (!gpsOff) {
                    sampleTime = String.valueOf(currTime - startTime);
                } else {
                    sampleTime = String.valueOf(currTime);  // Outputs time since 1970, when testing.
                }
                sampleID++;

                if (gyroPresent) {

                    deviceValues[0] = event.values[0];
                    deviceValues[1] = event.values[1];
                    deviceValues[2] = event.values[2];

                    SensorManager.getRotationMatrix(rMatrix, iMatrix, gravityValues, magneticValues);

                    Matrix.invertM(inverse, 0, rMatrix, 0);
                    Matrix.multiplyMV(worldMatrix, 0, inverse, 0, deviceValues, 0);

                    fE = worldMatrix[0];
                    fN = worldMatrix[1];
                    fD = worldMatrix[2];

                    sX = Float.toString(deviceValues[0]);
                    sY = Float.toString(deviceValues[1]);
                    sZ = Float.toString(deviceValues[2]);

                    sE = Float.toString(fE);
                    sN = Float.toString(fN);
                    sD = Float.toString(fD);

                    if (fE==fN && fE==fD && fE==0) {
                        sE = "";    sN = "";    sD = "";
                    }

                } else {

                    sX = Float.toString(event.values[0]);
                    sY = Float.toString(event.values[1]);
                    sZ = Float.toString(event.values[2]);

                }

                if (amp != 0 && amp != prevAmp) {
                    sAmp = String.valueOf(amp);
                    prevAmp = amp;
                } else {
                    sAmp = "";
                }

                sID = String.valueOf(sampleID);

                if (gyroPresent) {
                    if (gpsSample > prevSample) {
                        outputList = Arrays.asList(sID, sX, sY, sZ, sampleTime, sGyX, sGyY, sGyZ,
                                sN, sE, sD, sGPS, sAmp, gpsData);
                        prevSample = gpsSample;
                    } else {
                        outputList = Arrays.asList(sID, sX, sY, sZ, sampleTime, sGyX, sGyY, sGyZ,
                                sN, sE, sD, sGPS, sAmp);
                    }

                } else {
                    if (gpsSample > prevSample) {
                        outputList = Arrays.asList(sID, sX, sY, sZ, sampleTime, sGPS, sAmp, gpsData);
                        prevSample = gpsSample;
                    } else {
                        outputList = Arrays.asList(sID, sX, sY, sZ, sampleTime, sGPS, sAmp);
                    }
                }

                toQueue = TextUtils.join(",", outputList) + "\n";

                myQ.add(toQueue);

                break;

            case Sensor.TYPE_GRAVITY:

                gravityValues = event.values;
                break;

            case Sensor.TYPE_MAGNETIC_FIELD:

                magneticValues = event.values;
                break;

            case Sensor.TYPE_GYROSCOPE:

                float[] gyroValues = event.values;

                sGyX = Float.toString(gyroValues[0]);
                sGyY = Float.toString(gyroValues[1]);
                sGyZ = Float.toString(gyroValues[2]);

                break;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (manager != null) {
            manager.unregisterListener(this);
        }

        if (wakeLock != null) {
            wakeLock.release();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }
}
