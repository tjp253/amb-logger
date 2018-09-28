package uk.ac.nottingham.eaxtp1.CradleRideLogger;

import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.opengl.Matrix;
import android.os.IBinder;
import android.os.PowerManager;
import android.text.TextUtils;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static uk.ac.nottingham.eaxtp1.CradleRideLogger.AudioService.amp;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.GPSService.gpsData;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.GPSService.gpsSample;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.GPSService.sGPS;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.crashed;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.gpsOff;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.KEY_G;

public class IMUService extends Service implements SensorEventListener {
    public IMUService() {}

//    This service accesses the IMU to output accelerometer and gyroscope values. For every
// accelerometer sample, the data to be logged is combined and added to the Queue.

    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    NotificationUtilities notUtils;

    PowerManager.WakeLock wakeLock;
    long wakelockTimeout = 5 * 60 * 60 * 1000;  // 5 hour timeout to remove AndroidStudio warning.

    SensorManager manager;
    Sensor accelerometer, gravity, magnetic, gyroscope;
    boolean gyroPresent;

    private long sampleID;
    private short prevSample;

    String sID, sX, sY, sZ, sampleTime, toQueue,
            sGyX = "", sGyY = "", sGyZ = "", sE = "", sN = "", sD = "", sAmp = "";
    List<String> outputList;
    long startTime, currTime;
    int prevAmp;

//    Declare matrices for IMU data and for converting from device to world coordinates
    private float[] deviceValues = new float[4], gravityValues = new float[3],
            magneticValues = new float[3], rMatrix = new float[16], iMatrix = new float[16],
            worldMatrix = new float[16], inverse = new float[16];
    float fN, fE, fD;

    static BlockingQueue<String> myQ;   // Declare data queue

    @Override
    public void onCreate() {
        super.onCreate();

        myQ = new LinkedBlockingQueue<>();   // Initialise the queue

        if(crashed) {
            stopSelf();
        } else {
            initialiseIMU();
        }

        notUtils = new NotificationUtilities(this);

        Notification.Builder notBuild = notUtils.getForegroundNotification();
        startForeground(getResources().getInteger(R.integer.foregroundID), notBuild.build());
    }

//    Initialise the IMU sensors and the wakelock
    public void initialiseIMU() {
        gyroPresent = getSharedPreferences(getString(R.string.pref_main), MODE_PRIVATE)
                .getBoolean(KEY_G, true);

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

//        Stop the service from being destroyed
        PowerManager myPowerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (myPowerManager != null) {
            wakeLock = myPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "IMUService:WakeLock");
            if (!wakeLock.isHeld()) {
                wakeLock.acquire(wakelockTimeout);
            }
        }

        startTime = System.currentTimeMillis();
    }

//    Called when a new sensor value is available
    @Override
    public void onSensorChanged(SensorEvent event) {

        switch (event.sensor.getType()) {
            case Sensor.TYPE_ACCELEROMETER:

                currTime = System.currentTimeMillis();  // Time to be logged
                if (!gpsOff) {
                    sampleTime = String.valueOf(currTime - startTime);
                } else {
                    sampleTime = String.valueOf(currTime);  // Outputs time since 1970, when testing.
                }
                sampleID++;

//                Store the accelerometer values in device coordinates
                sX = Float.toString(event.values[0]);
                sY = Float.toString(event.values[1]);
                sZ = Float.toString(event.values[2]);

                if (gyroPresent) {  // Calculate accelerometer values in World coordinates

                    deviceValues[0] = event.values[0];
                    deviceValues[1] = event.values[1];
                    deviceValues[2] = event.values[2];

                    SensorManager.getRotationMatrix(rMatrix, iMatrix, gravityValues, magneticValues);

                    Matrix.invertM(inverse, 0, rMatrix, 0);
                    Matrix.multiplyMV(worldMatrix, 0, inverse, 0, deviceValues, 0);

                    fE = worldMatrix[0];
                    fN = worldMatrix[1];
                    fD = worldMatrix[2];

//                    Store the accelerometer values in world coordinates
                    sE = Float.toString(fE);
                    sN = Float.toString(fN);
                    sD = Float.toString(fD);

//                    If world coordinates are zero, save space in the CSV log
                    if (fE==fN && fE==fD && fE==0) {
                        sE = "";    sN = "";    sD = "";
                    }

                }

//                If a new Audio value is available, store it. Otherwise, save space in CSV log
                if (amp != 0 && amp != prevAmp) {
                    sAmp = String.valueOf(amp);
                    prevAmp = amp;
                } else {
                    sAmp = "";
                }

                sID = String.valueOf(sampleID);

//                Combine data to be logged
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

//                Store the gyroscope values
                sGyX = Float.toString(event.values[0]);
                sGyY = Float.toString(event.values[1]);
                sGyZ = Float.toString(event.values[2]);

                break;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (manager != null) {
            manager.unregisterListener(this);
        }

        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}
}
