package uk.ac.nottingham.eaxtp1.CradleRideLogger;

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

import static uk.ac.nottingham.eaxtp1.CradleRideLogger.LoggingService.newSample;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.crashed;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.gravityPresent;

public class IMUService extends Service implements SensorEventListener {
    public IMUService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    PowerManager.WakeLock wakeLock;

    SensorManager manager;
    Sensor accelerometer, gravity, magnetic;

    static String sX, sY, sZ;
    static String sGX, sGY, sGZ, sE, sN, sD;
    long startTime;
    static long sampleTime;

    private float[] deviceValues = new float[4];
    private float[] worldValues = new float[3];
    private float[] gravityValues = new float[3];
    private float[] magneticValues = new float[3];
    //    Matrices for converting from device to world coordinates
    private float[] rMatrix = new float[16];
    private float[] iMatrix = new float[16];
    private float[] worldMatrix = new float[16];
    private float[] inverse = new float[16];

    @Override
    public void onCreate() {
        super.onCreate();

        if(crashed) {
            onDestroy();
        } else {
            initialiseIMU();
        }
    }

    public void initialiseIMU() {
        manager = (SensorManager) getSystemService(SENSOR_SERVICE);

        accelerometer = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        manager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_FASTEST);

        if (gravityPresent) {
            gravity = manager.getDefaultSensor(Sensor.TYPE_GRAVITY);
            magnetic = manager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
            manager.registerListener(this, gravity, SensorManager.SENSOR_DELAY_FASTEST);
            manager.registerListener(this, magnetic, SensorManager.SENSOR_DELAY_FASTEST);

            deviceValues[3] = 0;
        }

        PowerManager myPowerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = myPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "IMU WakeLock");
        if (!wakeLock.isHeld()) {
            wakeLock.acquire();
        }

        startTime = System.currentTimeMillis();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        Sensor sensor = event.sensor;

        if (sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            sampleTime = System.currentTimeMillis() - startTime;
            newSample = true;

            if (gravityPresent) {

                deviceValues[0] = event.values[0];
                deviceValues[1] = event.values[1];
                deviceValues[2] = event.values[2];

                SensorManager.getRotationMatrix(rMatrix, iMatrix, gravityValues, magneticValues);

                Matrix.invertM(inverse, 0, rMatrix, 0);
                Matrix.multiplyMV(worldMatrix, 0, inverse, 0, deviceValues, 0);

                worldValues[0] = worldMatrix[0];
                worldValues[1] = worldMatrix[1];
                worldValues[2] = worldMatrix[2];

                sE = Float.toString(worldValues[0]);
                sN = Float.toString(worldValues[1]);
                sD = Float.toString(worldValues[2]);

                sX = Float.toString(deviceValues[0]);
                sY = Float.toString(deviceValues[1]);
                sZ = Float.toString(deviceValues[2]);



            } else {

                sX = Float.toString(event.values[0]);
                sY = Float.toString(event.values[1]);
                sZ = Float.toString(event.values[2]);

            }

        } else if (sensor.getType() == Sensor.TYPE_GRAVITY) {

            gravityValues = event.values;

            sGX = Float.toString(gravityValues[0]);
            sGY = Float.toString(gravityValues[1]);
            sGZ = Float.toString(gravityValues[2]);

        } else if (sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {

            magneticValues = event.values;

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
