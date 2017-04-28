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
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

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

    PowerManager.WakeLock wakeLock;

    //    Sets up the SensorManager
    private SensorManager mySensorManager;
    //    Allocates sensor variables
    Sensor myAccelerometer;
    Sensor myGravity;
    Sensor myMagneticField;

    private long sampleID;
    private long gpsSamp;

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

    String outputToData, outputToData_First;
    String outputTitle;

    List<String> outputList, titleList;

//    String initTextAccel, setTextAccel, initTextGPS, setTextGPS;

    //  Sets the initial counter value to current time
    long startTime;

    //    Creates a string of the current date and time
    @SuppressLint("SimpleDateFormat")
    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy_MM_dd-HH_mm");
    Date todaysDate = new Date();
    public String date = dateFormat.format(todaysDate);

    public File myFile;//, myFeedback;
    FileOutputStream myOutputStream;//, myFeedbackStream;
    OutputStreamWriter myWriter;//, myFeedbackWriter;
    String filepath = "New";
    String filename = date + "-ID" + String.valueOf(userID) + ".csv";
//    String feedbackName = date + "-ID" + String.valueOf(userID) + "-Feedback" + ".csv";

    @Override
    public void onCreate() {
        super.onCreate();

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

        PowerManager myPowerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = myPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "My WakeLock");
        wakeLock.acquire();

        myFile = new File(getExternalFilesDir(filepath), filename); // Data file
//        myFeedback = new File(getExternalFilesDir(filepath), feedbackName); // Feedback file - bumps and road surface
//        Creates the output stream and the stream writer
        try {
            myOutputStream = new FileOutputStream(myFile, true);
//            myFeedbackStream = new FileOutputStream(myFeedback, true);
            myWriter = new OutputStreamWriter(myOutputStream);
//            myFeedbackWriter = new OutputStreamWriter(myFeedbackStream);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

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

                titleList = Arrays.asList("id", "X", "Y", "Z", "Lat", "Long", "Time", "GravX", "GravY", "GravZ",
                        "North", "East", "Down", "GPS Sample");
                outputList = Arrays.asList(sID, sX, sY, sZ, sLat, sLong, sTime, sGravX, sGravY, sGravZ,
                        sNorth, sEast, sDown, sGPS);

            } else {

                titleList = Arrays.asList("id", "X", "Y", "Z", "Lat", "Long", "Time");
                outputList = Arrays.asList(sID, sX, sY, sZ, sLat, sLong, sTime);

            }


            outputTitle = TextUtils.join(", ", titleList);
            outputToData = TextUtils.join(", ", outputList);

            if (!myFile.exists()) {
                try {

                    outputToData_First = outputTitle + "\n" + outputToData;

                    myOutputStream.write(outputToData_First.getBytes());
                    myOutputStream.close();

                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else {

                try {
                    if (sampleID==1) {

                        outputToData_First = outputTitle + "\n" + outputToData;

                        myWriter.append(outputToData_First);
                        myWriter.flush();

                    } else {

                        String appendToData = "\n" + outputToData;
                        myWriter.append(appendToData);
                        myWriter.flush();

                    }

                } catch (IOException e) {
                    e.printStackTrace();
                }

            }

//            if (!myFeedback.exists()) {
//                try {
//                    myFeedbackStream.write(outputToData.getBytes());
//                    myFeedbackStream.close();
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
//            }


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

    //  Stops the timer on stop
    @Override
    public void onDestroy() {
        super.onDestroy();

        mySensorManager.unregisterListener(this);

        LocationManager myLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        myLocationManager.removeUpdates(this);

//        myDB.close();

        wakeLock.release();
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }
}