package uk.ac.nottingham.eaxtp1.CradleRideLogger;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.v4.app.ActivityCompat;
//import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.io.File;
//import java.io.FileNotFoundException;
//import java.io.FileOutputStream;
//import java.io.IOException;
//import java.io.OutputStreamWriter;
//import java.text.SimpleDateFormat;
//import java.util.Arrays;
//import java.util.Date;
//import java.util.List;
import java.util.Random;

@SuppressWarnings("deprecation")
public class MainActivity extends Activity implements View.OnClickListener, LocationListener, GpsStatus.Listener {

    WifiManager wifiManager;
    WifiInfo wifiInfo;

    Intent compressionService, uploadService, recordingService;

    SharedPreferences preferences;
    String user_ID = "User ID";
    static int userID;

    private int MY_PERMISSIONS_REQUEST_GPS = 2;

    public Button recordButton, initialiseButton;//, potholeButton, surfaceButton;
    public TextView infoDisplay, versionView;

    protected LocationManager myLocationManager;

//    Sets up variables for the GPS fix-check
    boolean isGPSFixed;
    long myLastLocationMillis;
    Location myLastLocation;
//    double latGPS, longGPS;
    long startTime;//, currentTime;

    boolean recording, initialising, badSurface;

    //    Initialise strings for the zipping
    static String mainPath, folderPath, zipPath;

//    public String date, feedbackName;
//    public File myFeedback;
//    FileOutputStream myFeedbackStream;
//    OutputStreamWriter myFeedbackWriter;
//    String filepath = "New";
//
//    String sLat, sLong, sTime, sPothole, sSurface;
//    String outputToFeedback;
//    String feedbackTitle;
//    List<String> outputList, titleList;

    @SuppressLint("WifiManagerLeak")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

//        Initialises a Unique ID for each user.
        preferences = getSharedPreferences("myPreferences", MODE_PRIVATE);
        if (preferences.getBoolean("firstLogin", true)) {

            Random random = new Random();
            int rndUserID = 10000000 + random.nextInt(90000000);

            SharedPreferences.Editor editor = preferences.edit();
            editor.putBoolean("firstLogin", false);
            editor.putInt(user_ID, rndUserID);
            editor.commit();
        }

        userID = preferences.getInt(user_ID, 1);

        infoDisplay = (TextView) findViewById(R.id.infoDisplay);
        versionView = (TextView) findViewById(R.id.versionView);

        String version = "Unique ID: " + String.valueOf(userID) +"\n"+ "Version: " ;
//        Gets the versionName from the app gradle to display.
        try {
            PackageInfo packageInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            version = version + packageInfo.versionName;
        } catch (Exception e) {
            e.printStackTrace();
        }
        versionView.setText(version);

        initialiseButton = (Button) findViewById(R.id.button_Initialise);
        recordButton = (Button) findViewById(R.id.button_Record);
//        potholeButton = (Button) findViewById(R.id.button_Pothole);
//        surfaceButton = (Button) findViewById(R.id.button_Surface);

        initialiseButton.setOnClickListener(this);
        recordButton.setOnClickListener(this);
//        potholeButton.setOnClickListener(this);
//        surfaceButton.setOnClickListener(this);

//        Disables the Start button
        recordButton.setEnabled(false);

////        Hides the feedback buttons until needed.
//        potholeButton.setVisibility(View.GONE);
//        surfaceButton.setVisibility(View.GONE);

////        Initialises feedback.
//        sLat = "0.0";
//        sLong = "0.0";
//        sPothole = "0";
//        sSurface = "0";
//        sTime = "0";
//        outputList = Arrays.asList(sLat, sLong, sPothole, sSurface);
//        outputToFeedback = TextUtils.join(", ", outputList);

        infoDisplay.setText(R.string.startGPS);

        recording = false;
        initialising = false;
        badSurface = false;

        mainPath = String.valueOf(getExternalFilesDir(""));
        folderPath = mainPath + "/New";
        zipPath = mainPath + "/Zipped";

//        Checks (and asks for) permission on app start-up
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

//            Checks if permission is NOT granted. Asks for permission if it isn't.
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                    checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION}, MY_PERMISSIONS_REQUEST_GPS);

            }

        }

        wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        uploadService = new Intent(this, UploadService.class);
        compressionService = new Intent(this, CompressionService.class);
        recordingService = new Intent(this, RecordingService.class);

        myLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

    }

    @Override
    protected void onResume() {
        super.onResume();

//        Resumes GPS when initialising.
        if (initialising) {
            initialiseButton.setEnabled(false);
            recordButton.setEnabled(false);

            //noinspection MissingPermission
            myLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);
            //noinspection MissingPermission
            myLocationManager.addGpsStatusListener(this);

            infoDisplay.setText(R.string.initialising);
        }

//        Compresses all finished data.
        if (!recording) {
            File csvFolder = new File(folderPath);
            File[] fileList = csvFolder.listFiles();
            int filesLeft = fileList.length;

            while (filesLeft > 0) {

                this.startService(compressionService);

                filesLeft = filesLeft - 1;
            }
        }

    }

    @Override
    protected void onPause() {
        super.onPause();

//        Stops GPS from draining the battery.
        if (initialising && !recording) {
            //noinspection MissingPermission
            myLocationManager.removeUpdates(this);
        }

//        Uploads files. Only when not recording.
        if (!recording) {
            File zipFolder = new File(zipPath);
            File[] zipList = zipFolder.listFiles();

            if (zipList != null && zipList.length != 0) {

                wifiInfo = wifiManager.getConnectionInfo();

                if (wifiInfo != null && wifiInfo.getNetworkId() != -1) {

                    this.startService(uploadService);

                }
            }
        }

    }

    @Override
    public void onClick(View v) {
        
        if (v == initialiseButton) {
            recording = false;
            initialising = true;

            initialiseButton.setEnabled(false);
            recordButton.setEnabled(false);

//        Re-checks (and asks for) the GPS permission needed
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

//            Checks if permission is NOT granted. Asks for permission if it isn't.
                if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                        checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

                    ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION}, MY_PERMISSIONS_REQUEST_GPS);

                }

            }

//        Checks for permission before running following code
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                    && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

                return;
            }

            myLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);
            myLocationManager.addGpsStatusListener(this);

//        Updates text to ask user to wait for GPS fix
            infoDisplay.setText(R.string.initialising);
            
        } else if (v == recordButton) {

            if (!recording) { // Start recording data
                infoDisplay.setText(R.string.recording);
                startService(recordingService);

                startTime = System.currentTimeMillis();

                //noinspection MissingPermission
                myLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);
                myLocationManager.addGpsStatusListener(this);

                recording = true;
                initialising = false;

                initialiseButton.setEnabled(false);
//                initialiseButton.setVisibility(View.GONE);
//
//                potholeButton.setVisibility(View.VISIBLE);
//                surfaceButton.setVisibility(View.VISIBLE);

                recordButton.setText(R.string.button_Stop);

//            Intent changeActivity = new Intent(this, SecondActivity.class);
//            startActivity(changeActivity);

////                Create the feedback file.
//                //    Creates a string of the current date and time
//                @SuppressLint("SimpleDateFormat")
//                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy_MM_dd-HH_mm");
//                Date todaysDate = new Date();
//                date = dateFormat.format(todaysDate);
//                feedbackName = date + "-ID" + String.valueOf(userID) + "-Feedback" + ".csv";
//
//                titleList = Arrays.asList("Lat", "Long", "Time", "Pothole", "Surface");
//                feedbackTitle = TextUtils.join(", ", titleList);
//
//                myFeedback = new File(getExternalFilesDir(filepath), feedbackName); // Feedback file - bumps and road surface
////              Creates the output stream and the stream writer
//                try {
//                    myFeedbackStream = new FileOutputStream(myFeedback, true);
//                    myFeedbackWriter = new OutputStreamWriter(myFeedbackStream);
//                } catch (FileNotFoundException e) {
//                    e.printStackTrace();
//                }
//
//                if (!myFeedback.exists()) {
//                    try {
//                        myFeedbackStream.write(feedbackTitle.getBytes());
//                        myFeedbackStream.close();
//
//                    } catch (IOException e) {
//                        e.printStackTrace();
//                    }
//                } else {
//                    try {
//                            myFeedbackWriter.append(feedbackTitle);
//                            myFeedbackWriter.flush();
//
//                    } catch (IOException e) {
//                        e.printStackTrace();
//                    }
//                }
                
            } else { // Stop recording data

                stopService(recordingService);

                infoDisplay.setText(R.string.finished);

                recording = false;

                recordButton.setText(R.string.button_Start);
                recordButton.setEnabled(false);
                initialiseButton.setEnabled(true);
//                initialiseButton.setVisibility(View.VISIBLE);
//
//                potholeButton.setVisibility(View.GONE);
//                surfaceButton.setVisibility(View.GONE);

                if (myLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    //noinspection MissingPermission
                    myLocationManager.removeUpdates(this);
                }
//                sPothole = "0";
//                sSurface = "0";

//                Compress the recording.
                File csvFolder = new File(folderPath);
                File[] fileList = csvFolder.listFiles();
                int filesLeft = fileList.length;

                while (filesLeft > 0) {

                    this.startService(compressionService);

                    filesLeft = filesLeft - 1;
                }

            }
            
        } //else if (v == potholeButton) {
//
//            currentTime = System.currentTimeMillis() - startTime;
//            sTime = String.valueOf(currentTime);
//            sPothole = "1";
//
//            outputList = Arrays.asList(sLat, sLong, sTime, sPothole, sSurface);
//            outputToFeedback = TextUtils.join(", ", outputList);
//            outputToFeedback = "\n" + outputToFeedback;
////            Record the coordinates.
//            try {
//                myFeedbackWriter.append(outputToFeedback);
//                myFeedbackWriter.flush();
//            } catch (Exception e) {
//                e.printStackTrace();
//            }
//
//            sPothole = "0";
//
//        } else if (v == surfaceButton) {
//
//            if (!badSurface) {
//
//                sSurface = "1";
//                badSurface = true;
//
//                currentTime = System.currentTimeMillis() - startTime;
//                sTime = String.valueOf(currentTime);
//
//                outputList = Arrays.asList(sLat, sLong, sTime, sPothole, sSurface);
//                outputToFeedback = TextUtils.join(", ", outputList);
//
//                try {
//                    outputToFeedback = "\n" + outputToFeedback;
//                    myFeedbackWriter.append(outputToFeedback);
//                    myFeedbackWriter.flush();
//                } catch (Exception e) {
//                    e.printStackTrace();
//                }
//
//                surfaceButton.setText(R.string.button_SurfaceGood);
//
//            } else {
//
//                sSurface = "0";
//                badSurface = false;
//
//                surfaceButton.setText(R.string.button_SurfaceBad);
//
//            }
//
//        }
        
    }

    @Override
    public void onGpsStatusChanged(int event) {

        if (initialising) {
            initialiseButton.setEnabled(false);

//        Ensures the GPS is fixed before the user starts recording
            switch (event) {
                case GpsStatus.GPS_EVENT_SATELLITE_STATUS:
                    if (myLastLocation != null)
                        isGPSFixed = (SystemClock.elapsedRealtime() - myLastLocationMillis) < 3000;

                    if (isGPSFixed) {

                        if (!recordButton.isEnabled()) {
//                        Updates text to tell user they can start recording
                            infoDisplay.setText(R.string.locked);
                        }

                        recordButton.setEnabled(true);
                    } else {
                        recordButton.setEnabled(false);

                        infoDisplay.setText(R.string.initialising);
                    }

                    break;
                case GpsStatus.GPS_EVENT_FIRST_FIX:

                    isGPSFixed = true;

                    break;
            }
        }
    }

    @Override
    public void onLocationChanged(Location location) {

        if (location == null) return;

        myLastLocationMillis = SystemClock.elapsedRealtime();

        myLastLocation = location;

//        if (recording) {
//            latGPS = location.getLatitude();
//            longGPS = location.getLongitude();
//
//            sLat = String.valueOf(latGPS);
//            sLong = String.valueOf(longGPS);
//
//            currentTime = System.currentTimeMillis() - startTime;
//            sTime = String.valueOf(currentTime);
//
//            outputList = Arrays.asList(sLat, sLong, sTime, sPothole, sSurface);
//            outputToFeedback = TextUtils.join(", ", outputList);
//        }
//
//        if (badSurface) {
//            try {
//                outputToFeedback = "\n" + outputToFeedback;
//                myFeedbackWriter.append(outputToFeedback);
//                myFeedbackWriter.flush();
//            } catch (Exception e) {
//                e.printStackTrace();
//            }
//        }

    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {}

    @Override
    public void onProviderEnabled(String provider) {}

    @Override
    public void onProviderDisabled(String provider) {}

}