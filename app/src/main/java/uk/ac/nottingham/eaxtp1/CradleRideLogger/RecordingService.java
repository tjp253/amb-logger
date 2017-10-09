package uk.ac.nottingham.eaxtp1.CradleRideLogger;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.opengl.Matrix;
//import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.text.TextUtils;
//import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.zip.Deflater;
import java.util.zip.GZIPOutputStream;

import static uk.ac.nottingham.eaxtp1.CradleRideLogger.AudioService.amp;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.GPSService.gpsSample;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.GPSService.sGPS;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.GPSService.sLat;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.GPSService.sLong;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.GPSService.sSpeed;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.crashed;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.gravityPresent;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.userID;

@SuppressWarnings({"MissingPermission", "SpellCheckingInspection"})
public class RecordingService extends Service
        implements SensorEventListener {
    public RecordingService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
//         TO DO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    int uploadLimit = 10350000; // Restricts file size to ~9.9mb
    long startTime, checkDelay = 5000;
    Timer sizeCheckTimer;
    TimerTask sizeChecker;
    boolean nearLimit;

    File gzFile;

    PowerManager.WakeLock wakeLock;

    //    Sets up the SensorManager
    private SensorManager mySensorManager;
    //    Allocates sensor variables
    Sensor myAccelerometer;
    Sensor myGravity;
    Sensor myMagneticField;

    private long sampleID;
    private long prevSamp;

    private float[] deviceValues = new float[4];
    private float[] worldValues = new float[3];
    private float[] gravityValues = null;
    private float[] magneticValues = null;
    //    Matrices for converting from device to world coordinates
    private float[] rMatrix = new float[16];
    private float[] iMatrix = new float[16];
    private float[] worldMatrix = new float[16];
    private float[] inverse = new float[16];

    String sID, sX, sY, sZ, sTime;
    String sGravX = "", sGravY = "", sGravZ = "";
    String sEast = "", sNorth = "", sDown = "";
    String sAmp = "";
    int prevAmp;

    String outputToData;
    String outputTitle;

    List<String> outputList, titleList;

    //    Creates a string of the current date and time
    @SuppressLint("SimpleDateFormat")
    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy_MM_dd-HH_mm");
    Date todaysDate = new Date();
    public String date = dateFormat.format(todaysDate);

    OutputStream myOutputStream;
    int zipPart = 1;
    String filepath = "Recording";
    String filename = date + "-ID" + String.valueOf(userID) + "-" + zipPart + ".csv.gz";
    static String mainPath, gzipPath;

    @Override
    public void onCreate() {
        super.onCreate();

        crashCheck();

        if (!crashed) {
            initialiseRecording();
        }
    }

    public void crashCheck() {
//        if (userID == 13319625 || userID == 0) {
        if (userID == 0) {
            crashed = true;
            onDestroy();
        }
    }

    public void initialiseRecording() {
        mySensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        myAccelerometer = mySensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mySensorManager.registerListener(this, myAccelerometer, SensorManager.SENSOR_DELAY_FASTEST);

        if (gravityPresent) {
            myGravity = mySensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
            myMagneticField = mySensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
            mySensorManager.registerListener(this, myGravity, SensorManager.SENSOR_DELAY_FASTEST);
            mySensorManager.registerListener(this, myMagneticField, SensorManager.SENSOR_DELAY_FASTEST);
        }

        PowerManager myPowerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = myPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Main WakeLock");
        if (!wakeLock.isHeld()) {
            wakeLock.acquire();
        }

        mainPath = String.valueOf(getExternalFilesDir(filepath)) + "/";
        gzipPath = mainPath + filename;

        try {
            myOutputStream = new FileOutputStream(gzipPath);
            myOutputStream = new GZIPOutputStream(myOutputStream)
            {{def.setLevel(Deflater.BEST_COMPRESSION);}};
        } catch (IOException e) {
            e.printStackTrace();
        }

        gzFile = new File(gzipPath);

        startTime = System.currentTimeMillis();

        sizeCheckTimer = new Timer();

        sizeCheckTT();

        sizeCheckTimer.schedule(sizeChecker, 5000, checkDelay);

    }

    public void sizeCheckTT() {
        sizeChecker = new TimerTask() {
            @Override
            public void run() {
                File gzFile = new File(gzipPath);
                if (gzFile.length() > uploadLimit) {
                    nearLimit = true;
                }
            }
        };
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        Sensor mySensor = sensorEvent.sensor;

        if (mySensor.getType() == Sensor.TYPE_ACCELEROMETER) {
//            Increments the Sample ID
            sTime = String.valueOf(System.currentTimeMillis() - startTime);
            sampleID++;

            deviceValues[0] = sensorEvent.values[0];
            deviceValues[1] = sensorEvent.values[1];
            deviceValues[2] = sensorEvent.values[2];
            deviceValues[3] = 0;

//            Set up strings
            sID = String.valueOf(sampleID);
            sX = Float.toString(deviceValues[0]);
            sY = Float.toString(deviceValues[1]);
            sZ = Float.toString(deviceValues[2]);


            if ((gravityValues != null) && (magneticValues != null)) {
                SensorManager.getRotationMatrix(rMatrix, iMatrix, gravityValues, magneticValues);

                Matrix.invertM(inverse, 0, rMatrix, 0);
                Matrix.multiplyMV(worldMatrix, 0, inverse, 0, deviceValues, 0);

                worldValues[0] = worldMatrix[0];
                worldValues[1] = worldMatrix[1];
                worldValues[2] = worldMatrix[2];

                sEast = Float.toString(worldValues[0]);
                sNorth = Float.toString(worldValues[1]);
                sDown = Float.toString(worldValues[2]);
            }

            if (amp != 0 && amp != prevAmp) {
                sAmp = String.valueOf(amp);
                prevAmp = amp;
            } else {
                sAmp = "";
            }

            if (gravityPresent) {
                if (sampleID == 1) {
                    titleList = Arrays.asList("id", "X", "Y", "Z", "Time", "GravX", "GravY", "GravZ",
                            "North", "East", "Down", "GPS Sample", "Lat", "Long", "Noise", "Speed");
                }
                if (gpsSample > prevSamp) {
                    outputList = Arrays.asList(sID, sX, sY, sZ, sTime, sGravX, sGravY, sGravZ,
                            sNorth, sEast, sDown, sGPS, sLat, sLong, sAmp, sSpeed);
                    prevSamp = gpsSample;
                } else {
                    outputList = Arrays.asList(sID, sX, sY, sZ, sTime, sGravX, sGravY, sGravZ,
                            sNorth, sEast, sDown, sGPS,"","",sAmp);
                }

            } else {
                if (sampleID == 1) {
                    titleList = Arrays.asList("id", "X", "Y", "Z", "Time", "GPS Sample", "Lat", "Long", "Noise", "Speed");
                }
                if (gpsSample > prevSamp) {
                    outputList = Arrays.asList(sID, sX, sY, sZ, sTime, sGPS, sLat, sLong, sAmp, sSpeed);
                    prevSamp = gpsSample;
                } else {
                    outputList = Arrays.asList(sID, sX, sY, sZ, sTime, sGPS, "","", sAmp);
                }
            }

            outputToData = "\n" + TextUtils.join(",", outputList);

            if (sampleID == 1) {
                outputTitle = TextUtils.join(",", titleList);
                try {
                    myOutputStream.write(outputTitle.getBytes("UTF-8"));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            try {
                myOutputStream.write(outputToData.getBytes("UTF-8"));
            } catch (IOException e) {
                e.printStackTrace();
            }

            if (nearLimit) {
                zipPart++;
                fileSplitter(zipPart);
                nearLimit = false;
            }

        } else if (mySensor.getType() == Sensor.TYPE_GRAVITY) {

            gravityValues = sensorEvent.values;

            sGravX = Float.toString(gravityValues[0]);
            sGravY = Float.toString(gravityValues[1]);
            sGravZ = Float.toString(gravityValues[2]);

        } else if (mySensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {

            magneticValues = sensorEvent.values;

        }
    }

    public void fileSplitter(int filePart) {

        gzipPath = mainPath + date + "-ID" + String.valueOf(userID) + "-" + filePart + ".csv.gz";

        try {
            myOutputStream.close();
            myOutputStream = new FileOutputStream(gzipPath);
            myOutputStream = new GZIPOutputStream(myOutputStream)
                                {{def.setLevel(Deflater.BEST_COMPRESSION);}};
        } catch (IOException e) {
            e.printStackTrace();
        }

        gzFile = new File(gzipPath);

    }

    //  Stops the timer on stop
    @Override
    public void onDestroy() {
        super.onDestroy();

        if (mySensorManager != null) {
            mySensorManager.unregisterListener(this);
        }

        if (myOutputStream != null) {
            try {
                myOutputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if (sizeCheckTimer != null) {
            sizeCheckTimer.cancel();
        }

        if (crashed) {
            Intent stopAudio = new Intent(this, AudioService.class);
            Intent stopGPS = new Intent(this, GPSService.class);
            this.stopService(stopAudio);
            this.stopService(stopGPS);
            Intent deletingService = new Intent(this, FileDeletingService.class);
            this.startService(deletingService);
        } else {
            Intent movingService = new Intent(this, MovingService.class);
            this.startService(movingService);
        }

        gpsSample = 0;
        prevSamp = 0;

        if (wakeLock != null) {
            wakeLock.release();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}
}