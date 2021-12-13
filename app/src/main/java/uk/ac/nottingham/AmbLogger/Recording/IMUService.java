package uk.ac.nottingham.AmbLogger.Recording;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.opengl.Matrix;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static uk.ac.nottingham.AmbLogger.Recording.AudioService.amp;
import static uk.ac.nottingham.AmbLogger.Recording.GPSService.gpsData;
import static uk.ac.nottingham.AmbLogger.Recording.GPSService.gpsSampleTime;
import static uk.ac.nottingham.AmbLogger.Recording.GPSService.sGPS;
import static uk.ac.nottingham.AmbLogger.Recording.GPSService.timerOn_Slow;
import static uk.ac.nottingham.AmbLogger.MainActivity.crashed;
import static uk.ac.nottingham.AmbLogger.MainActivity.gpsOff;
import static uk.ac.nottingham.AmbLogger.MainActivity.KEY_G;
import static uk.ac.nottingham.AmbLogger.MainActivity.initialising;
import static uk.ac.nottingham.AmbLogger.MainActivity.recording;

import uk.ac.nottingham.AmbLogger.BuildConfig;
import uk.ac.nottingham.AmbLogger.Utilities.DateFormatter;
import uk.ac.nottingham.AmbLogger.Utilities.NotificationUtilities;
import uk.ac.nottingham.AmbLogger.R;

public class IMUService extends Service /*implements SensorEventListener*/ {
    public IMUService() {}

//    This service accesses the IMU to output accelerometer and gyroscope values. For every
// accelerometer sample, the data to be logged is combined and added to the Queue.

    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    HandlerThread imuHandlerThread;
    Handler imuHandler;
    SensorEventListener imuListener;

    NotificationUtilities notUtils;

    PowerManager.WakeLock wakeLock;
    long wakelockTimeout = 5 * 60 * 60 * 1000;  // 5 hour timeout to remove AndroidStudio warning.

    SensorManager manager;
    Sensor accelerometer, gravity, magnetic, gyroscope;
    boolean gyroPresent, timerOn_Samples;
    public static boolean heldWithMagnets;

    private long sampleID;

    String toQueue;
    List<String> outputList = new ArrayList<>();
    long startTime, currTime;

//    Declare matrices for IMU data and for converting from device to world coordinates
    float[] deviceValues = new float[4], gravityValues = new float[3],
        gyroValues = new float[3], magneticValues = new float[3], rMatrix = new float[16],
        iMatrix = new float[16], worldMatrix = new float[16], inverse = new float[16];

    static BlockingQueue<String> myQ;   // Declare data queue

    public static String date;

    Intent autoStopTimerService;

    @Override
    public void onCreate() {
        super.onCreate();

        myQ = new LinkedBlockingQueue<>();   // Initialise the queue

        if(crashed) {
            stopSelf();
        } else {
            generateHandler();
//            initialiseIMU();
        }

        notUtils = new NotificationUtilities(this);

        startForeground(notUtils.FOREGROUND_INT,notUtils.getForegroundNotification().build());

        autoStopTimerService = new Intent(getApplicationContext(),AutoStopTimerService.class);
    }

    private void generateHandler() {
        imuHandlerThread = new HandlerThread("IMU_Handler");
        imuHandlerThread.start();

        imuHandler = new Handler(imuHandlerThread.getLooper());

        setupListener();

        initialiseIMU();
    }

//    Initialise the IMU sensors and the wakelock
    public void initialiseIMU() {

        date = new DateFormatter(this).formDate();

        startTime = System.nanoTime();

        gyroPresent = PreferenceManager.getDefaultSharedPreferences(this)
                .getBoolean(KEY_G, true);

        manager = (SensorManager) getSystemService(SENSOR_SERVICE);
        int delay = SensorManager.SENSOR_DELAY_FASTEST;
        if (manager != null) {
            accelerometer = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            manager.registerListener(imuListener, accelerometer, delay, imuHandler);

            if (gyroPresent) {
                gravity = manager.getDefaultSensor(Sensor.TYPE_GRAVITY);
                magnetic = manager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
                gyroscope = manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
                manager.registerListener(imuListener, gravity, delay, imuHandler);
                manager.registerListener(imuListener, magnetic, delay, imuHandler);
                manager.registerListener(imuListener, gyroscope, delay, imuHandler);

                deviceValues[3] = 0;

                if (!BuildConfig.TEST_MODE) {
                    heldWithMagnets = PreferenceManager.getDefaultSharedPreferences(this)
                            .getBoolean(getString(R.string.key_pref_magnets), true);
                }
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

    }

    private void setupListener() {
        imuListener = new SensorEventListener() {
            //    Called when a new sensor value is available
            @Override
            public void onSensorChanged(SensorEvent event) {
                if (!recording && (BuildConfig.TEST_MODE && !initialising)) {
                    imuHandlerThread.quit();
                }
//                Log.i("IMU", "Handler listener");
                switch (event.sensor.getType()) {
                    case Sensor.TYPE_ACCELEROMETER:

                        currTime = System.nanoTime();  // Time to be logged

                        outputList.clear();

                        sampleID++;
                        outputList.add(Long.toString(sampleID));

                        // Store the accelerometer values in device coordinates
                        for (float value : event.values) {
                            outputList.add(Float.toString(value));
                        }

                        if (!gpsOff) {
                            outputList.add(Long.toString(currTime - startTime));

                            // The following code handles the AutoStop service, depending on how long it
                            // has been since the last GPS Sample. If the AutoStop Timer is NOT on due to
                            // slow speed, check if the GPS has lost signal.
                            if (!timerOn_Slow && gpsSampleTime > 0) { // GPS Service has started
                                long timeSinceGPS = currTime - gpsSampleTime;

                                if (timeSinceGPS < 60000) { // TODO: Set to 1 minute (60000)
                                    // If AutoStopTimer is on because of lack of GPS cancel the Timer.
                                    if (timerOn_Samples) {
                                        timerOn_Samples = false;
                                        stopService(autoStopTimerService);
                                    }
                                    // If it's been a minute without a GPS sample, start the AutoStop Timer
                                } else if (!timerOn_Samples) {
                                    timerOn_Samples = true;
                                    startService(autoStopTimerService);
                                }
                            }

                        } else {
                            outputList.add(Long.toString(currTime));  // Outputs time since 1970, when testing.
                        }

                        if (gyroPresent) {  // Calculate accelerometer values in World coordinates

                            for (float value : gyroValues) {
                                outputList.add(Float.toString(value));
                            }

                            deviceValues[0] = event.values[0];
                            deviceValues[1] = event.values[1];
                            deviceValues[2] = event.values[2];

                            SensorManager.getRotationMatrix(rMatrix, iMatrix, gravityValues, magneticValues);

                            Matrix.invertM(inverse, 0, rMatrix, 0);
                            Matrix.multiplyMV(worldMatrix, 0, inverse, 0, deviceValues, 0);

//                    Store the accelerometer values in world coordinates
                            if (worldMatrix[0] + worldMatrix[1] + worldMatrix[2] == 0) {
                                outputList.add("");
                                outputList.add("");
                                outputList.add("");
                            } else if (!BuildConfig.TEST_MODE && heldWithMagnets) {
                                // If holding the phone in place using magnets on the ambulance trolley,
                                // the magnetometer (compass) doesn't function properly. Therefore, save
                                // space by blanking the 'North' and 'East' variables.
                                outputList.add("");
                                outputList.add("");
                                outputList.add(Float.toString(worldMatrix[2]));
                            } else {
                                for (float value : Arrays.copyOfRange(worldMatrix, 0, 3)) {
                                    outputList.add(Float.toString(value));
                                }
                            }

                        }

                        outputList.add(sGPS);

//                If a new Audio value is available, store it. Otherwise, save space in CSV log
                        if (amp != 0) {
                            outputList.add(Integer.toString(amp));
                            amp = 0;
                        } else if (!gpsData.isEmpty()){
                            outputList.add("");
                        }

                        if (!gpsData.isEmpty()) {
                            outputList.addAll(gpsData);
                            gpsData.clear();
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
                        gyroValues = event.values;

                        break;
                }
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {

            }

        };
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (manager != null) {
            manager.unregisterListener(imuListener);
        }

        imuHandlerThread.quit();

        // Reset the static variables for the next recording.
        sGPS = "";
        amp = 0;
        gpsSampleTime = 0;

        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
    }
}
