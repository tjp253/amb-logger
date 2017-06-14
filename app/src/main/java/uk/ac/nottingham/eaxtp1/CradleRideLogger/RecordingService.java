package uk.ac.nottingham.eaxtp1.CradleRideLogger;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.opengl.Matrix;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.text.TextUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.zip.Deflater;
import java.util.zip.GZIPOutputStream;

import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.crashed;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.userID;

@SuppressWarnings({"MissingPermission", "SpellCheckingInspection"})
public class RecordingService extends Service
        implements SensorEventListener, LocationListener {
    public RecordingService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
//         TO DO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    int uploadLimit = 10350000; // Restricts file size to ~9.9mb
    long startTime, checkTime = 1000, checkDelay = 5000;

    File gzFile;

    PowerManager.WakeLock wakeLock;

    //    Sets up the SensorManager
    private SensorManager mySensorManager;
    //    Allocates sensor variables
    Sensor myAccelerometer;
    Sensor myGravity;
    Sensor myMagneticField;

    private long sampleID;
    private long gpsSamp, prevSamp;

    private float[] deviceValues = new float[4];
    private float[] worldValues = new float[3];
    private float[] gravityValues = null;
    private float[] magneticValues = null;
    //    Matrices for converting from device to world coordinates
    private float[] rMatrix = new float[16];
    private float[] iMatrix = new float[16];
    private float[] worldMatrix = new float[16];
    private float[] inverse = new float[16];

    //    Sets up GPS variables
    private double latGPS;
    private double longGPS;

    String sID, sX, sY, sZ;
    String sLat, sLong, sTime, sGPS;
    String sGravX, sGravY, sGravZ;
    String sEast, sNorth, sDown;

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
    String mainPath, gzipPath;

    @Override
    public void onCreate() {
        super.onCreate();

        if (userID == 0) {
            crashed = true;
            onDestroy();
        }

        mySensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        myAccelerometer = mySensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        myGravity = mySensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
        myMagneticField = mySensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

        mySensorManager.registerListener(this, myAccelerometer, SensorManager.SENSOR_DELAY_FASTEST);
        mySensorManager.registerListener(this, myGravity, SensorManager.SENSOR_DELAY_FASTEST);
        mySensorManager.registerListener(this, myMagneticField, SensorManager.SENSOR_DELAY_FASTEST);

//        Registers the Location Listener, and sets up updates
        LocationManager myLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        myLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);

//        Initialises the Sample ID
        sampleID = 0;
        gpsSamp = 0;
        prevSamp = 0;

        PowerManager myPowerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = myPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "My WakeLock");
        wakeLock.acquire();

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

//        myDB = new DatabaseHelper(this);

        startTime = System.currentTimeMillis();

        mySensorManager.registerListener(this, myAccelerometer, SensorManager.SENSOR_DELAY_FASTEST);
        mySensorManager.registerListener(this, myGravity, SensorManager.SENSOR_DELAY_FASTEST);
        mySensorManager.registerListener(this, myMagneticField, SensorManager.SENSOR_DELAY_FASTEST);
    }

    //    Gets location info
    @Override
    public void onLocationChanged(Location location) {
        latGPS = location.getLatitude();
        longGPS = location.getLongitude();
        gpsSamp++;
    }

    @Override
    public void onProviderDisabled(String provider) {

    }

    @Override
    public void onProviderEnabled(String provider) {

    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        Sensor mySensor = sensorEvent.sensor;

        if (mySensor.getType() == Sensor.TYPE_ACCELEROMETER) {
//            Increments the sample ID
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

            sLat = String.valueOf(latGPS);
            sLong = String.valueOf(longGPS);
            sGPS = String.valueOf(gpsSamp);

            long time = System.currentTimeMillis() - startTime;
            sTime = String.valueOf(time);

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

            if (magneticValues != null && gravityValues != null) {

                if (gpsSamp > prevSamp) {
                    titleList = Arrays.asList("id", "X", "Y", "Z", "Time", "GravX", "GravY", "GravZ",
                            "North", "East", "Down", "GPS Sample", "Lat", "Long");
                    outputList = Arrays.asList(sID, sX, sY, sZ, sTime, sGravX, sGravY, sGravZ,
                            sNorth, sEast, sDown, sGPS, sLat, sLong);
                    prevSamp = gpsSamp;
                } else {
                    titleList = Arrays.asList("id", "X", "Y", "Z", "Time", "GravX", "GravY", "GravZ",
                            "North", "East", "Down", "GPS Sample");
                    outputList = Arrays.asList(sID, sX, sY, sZ, sTime, sGravX, sGravY, sGravZ,
                            sNorth, sEast, sDown, sGPS);
                }

            } else {

                if (gpsSamp > prevSamp) {
                    titleList = Arrays.asList("id", "X", "Y", "Z", "Time", "GPS Sample", "Lat", "Long");
                    outputList = Arrays.asList(sID, sX, sY, sZ, sTime, sGPS, sLat, sLong);
                    prevSamp = gpsSamp;
                } else {
                    titleList = Arrays.asList("id", "X", "Y", "Z", "Time", "GPS Sample");
                    outputList = Arrays.asList(sID, sX, sY, sZ, sTime, sGPS);
                }

            }


            outputTitle = TextUtils.join(", ", titleList);
            outputToData = "\n" + TextUtils.join(", ", outputList);

            if (sampleID == 1) {
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

            if (time - checkTime > checkDelay ) {
//                File gzFile = new File(gzipPath);
                if (gzFile.length() > uploadLimit) {
                    // Split the recording into a new file
                    zipPart++;
                    fileSplitter(zipPart);
                }

                checkTime = time;
            }

////            Writes to database.
//            boolean isInserted = myDB.insertData(worldValues[0], worldValues[1], worldValues[2], latGPS, longGPS, time);

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

        if (!crashed) {
            mySensorManager.unregisterListener(this);

            LocationManager myLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            myLocationManager.removeUpdates(this);

            try {
                myOutputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

//        myDB.close();

            wakeLock.release();
        }

        Intent movingService = new Intent(this, MovingService.class);
        this.startService(movingService);

    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }
}