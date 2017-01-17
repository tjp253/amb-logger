package uk.ac.nottingham.eaxtp1.CradleRideLogger;

import android.app.Activity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.opengl.Matrix;
import android.os.Handler;
import android.os.Bundle;
import android.os.PowerManager;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;


@SuppressWarnings("MissingPermission")
public class SecondActivity extends Activity
        implements SensorEventListener, LocationListener {

//    DatabaseHelper myDB;

    PowerManager.WakeLock wakeLock;

    //    Creates the TextView variables
    TextView textAccel;
    TextView textTime;
    TextView textGPS;
    TextView accelRealTime;

//    Sets up the SensorManager
    private SensorManager mySensorManager;
//    Allocates sensor variables
    private Sensor myAccelerometer;
    private Sensor myGravity;
    private Sensor myMagneticField;

    private long sampleID;

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
    String sLat, sLong, sTime;
    String sGravX, sGravY, sGravZ, sMagX, sMagY, sMagZ;
    String sEast, sNorth, sDown;

    String outputToData, outputToData_First;
    String outputTitle;

    List<String> outputList, titleList;

    String initTextAccel, setTextAccel, initTextGPS, setTextGPS;

    //  Sets the initial counter value to current time
    long startTime;

//    Creates a string of the current date and time
    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy_MM_dd__HH_mm");
    Date todaysDate = new Date();
    public String date = dateFormat.format(todaysDate);

    public File myFile;
    FileOutputStream myOutputStream;
    OutputStreamWriter myWriter;
    String filepath = "New";
    String filename = "RoadVib_" + date + ".csv";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_second);

        mySensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        myAccelerometer = mySensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        myGravity = mySensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
        myMagneticField = mySensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

        mySensorManager.registerListener(this, myAccelerometer, SensorManager.SENSOR_DELAY_GAME);
        mySensorManager.registerListener(this, myGravity, SensorManager.SENSOR_DELAY_GAME);
        mySensorManager.registerListener(this, myMagneticField, SensorManager.SENSOR_DELAY_GAME);

//        Registers the Location Listener, and sets up updates
        LocationManager myLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        myLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);

//        Initialises the Sample ID
        sampleID = 0;

        PowerManager myPowerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = myPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "My WakeLock");
        wakeLock.acquire();

        myFile = new File(getExternalFilesDir(filepath), filename);
//        Creates the output stream and the stream writer
        try {
            myOutputStream = new FileOutputStream(myFile, true);
            myWriter = new OutputStreamWriter(myOutputStream);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

//        myDB = new DatabaseHelper(this);

        startTime = System.currentTimeMillis();
    }

//    Sets the runnable going and gets sets up the accelerometer
    @Override
    protected void onStart() {
        super.onStart();

//      Connects the TextView variables to the TextView objects
        textAccel = (TextView) findViewById(R.id.textAccel);
        textTime = (TextView) findViewById(R.id.textTime);
        textGPS =(TextView) findViewById(R.id.textGPS);
        accelRealTime = (TextView) findViewById(R.id.accelRealTime);

//      Sets runnable going, and hence the timer
        timerHandler.post(timerRunnable);

    }

    @Override
    protected void onResume() {
        super.onResume();

        mySensorManager.registerListener(this, myAccelerometer, SensorManager.SENSOR_DELAY_GAME);
        mySensorManager.registerListener(this, myGravity, SensorManager.SENSOR_DELAY_GAME);
        mySensorManager.registerListener(this, myMagneticField, SensorManager.SENSOR_DELAY_GAME);

    }

//    Gets location info
    @Override
    public void onLocationChanged(Location location) {
        latGPS = location.getLatitude();
        longGPS = location.getLongitude();
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
            sampleID = sampleID + 1;

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

//            Sets up concatenated strings
            initTextAccel = "Sample ID: 0" + "\n" +
                    getString(R.string.acc_placeholder) +
                    "\t\tX: 0.0" + "\n" +
                    "\t\tY: 0.0" + "\n" +
                    "\t\tZ: 0.0";

            initTextGPS = getString(R.string.gps_placeholder) +
                    "\t\tLat: " + sLat + "\n" +
                    "\t\tLong: " + sLong;

            setTextAccel = "Sample ID: " + Long.toString(sampleID) + "\n" +
                    getString(R.string.acc_placeholder) +
                    "\t\tX: " + sX + "\n" +
                    "\t\tY: " + sY + "\n" +
                    "\t\tZ: " + sZ;

            setTextGPS = getString(R.string.gps_placeholder) +
                    "\t\tLat: " + sLat + "\n" +
                    "\t\tLong: " + sLong;

            if (magneticValues != null && gravityValues != null) {

                titleList = Arrays.asList("id", "X", "Y", "Z", "Lat", "Long", "Time", "GravX", "GravY", "GravZ",
                        "MagX", "MagY", "MagZ", "North", "East", "Down");
                outputList = Arrays.asList(sID, sX, sY, sZ, sLat, sLong, sTime, sGravX, sGravY, sGravZ,
                        sMagX, sMagY, sMagZ, sNorth, sEast, sDown);

            } else {

                titleList = Arrays.asList("id", "X", "Y", "Z", "Lat", "Long", "Time");
                outputList = Arrays.asList(sID, sX, sY, sZ, sLat, sLong, sTime);

            }


            outputTitle = TextUtils.join(", ", titleList);
            outputToData = TextUtils.join(", ", outputList);

//            Prints every 100 samples
            if (sampleID % 100 == 0) {

//                Displays accelerometer values every 100 samples
                textAccel.setText(setTextAccel);
                textGPS.setText(setTextGPS);

            } else if (sampleID == 1) {

//                Displays accelerometer values at start
                textAccel.setText(initTextAccel);
                textGPS.setText(initTextGPS);
            }

//            Provides a real time comparison
            String setRealAccel = "Real Time X: " + sX;
            accelRealTime.setText(setRealAccel);

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


////            Writes to database.
//            boolean isInserted = myDB.insertData(worldValues[0], worldValues[1], worldValues[2], latGPS, longGPS, time);

        } else if (mySensor.getType() == Sensor.TYPE_GRAVITY) {

            gravityValues = sensorEvent.values;

            sGravX = Float.toString(gravityValues[0]);
            sGravY = Float.toString(gravityValues[1]);
            sGravZ = Float.toString(gravityValues[2]);

        } else if (mySensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {

            magneticValues = sensorEvent.values;

            sMagX = Float.toString(magneticValues[0]);
            sMagY = Float.toString(magneticValues[1]);
            sMagZ = Float.toString(magneticValues[2]);

        }

    }


//    Creates handler to run code on another thread
    Handler timerHandler = new Handler();



//    Defines code to be run on new thread - incremental time
    Runnable timerRunnable = new Runnable() {

        @Override
        public void run() {

            long millis = System.currentTimeMillis() - startTime; // Calculates the amount of ms which have passed
            int seconds = (int) (millis / 1000);    // Calculates the seconds which have passed
            int minutes = seconds / 60;             // Calculates the minutes which have passed
            seconds = seconds % 60;                 // Calcs remainder of seconds after dividing by 60

            String timeDisplay = String.format("%d:%02d", minutes, seconds);

            textTime.setText(timeDisplay); // shows time as mm:ss

            timerHandler.postDelayed(this, 1000); // posts results to UI thread every 2000 ms (2 seconds) (?)

        }
    };

    //  Stops the timer on stop
    @Override
    protected void onDestroy() {
        super.onDestroy();
//        Stops the created runnable thread
        timerHandler.removeCallbacks(timerRunnable);

        mySensorManager.unregisterListener(this);

        LocationManager myLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        myLocationManager.removeUpdates(this);

//        myDB.close();

        wakeLock.release();
    }


    //    Stops the activity on pressing "stop"
    public void stopRecording(View view) {
        finish();
    }


    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }
}
