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
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.io.File;
import java.util.Random;

@SuppressWarnings("deprecation")
public class MainActivity extends Activity implements LocationListener, GpsStatus.Listener {

    WifiManager wifiManager;
    WifiInfo wifiInfo;

    Intent compressionService, uploadService;

    SharedPreferences preferences;
    String user_ID = "User ID";
    static int userID;

    private int MY_PERMISSIONS_REQUEST_GPS = 2;

    public Button startButton;
    public Button initialiseButton;
    public TextView infoDisplay;
    public TextView versionView;

    protected LocationManager myLocationManager;

//    Sets up variables for the GPS fix-check
    boolean isGPSFixed;
    long myLastLocationMillis;
    Location myLastLocation;

    boolean recordedYet, gpsON;

    //    Initialise strings for the zipping
    static String mainPath, folderPath, zipPath;

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
        startButton = (Button) findViewById(R.id.button_Start);
//        Disables the Start button
        startButton.setEnabled(false);

        String startGPS = "Please start the GPS receiver.";
        infoDisplay.setText(startGPS);

        recordedYet = false;
        gpsON = false;

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

        myLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

    }

    @Override
    protected void onResume() {
        super.onResume();

        if (recordedYet) {
            initialiseButton.setEnabled(true);
            startButton.setEnabled(false);

            String closeApp = "Thank you for recording your journey.";
            infoDisplay.setText(closeApp);

            if (myLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                //noinspection MissingPermission
                myLocationManager.removeUpdates(this);
            }

            gpsON = false;

            File csvFolder = new File(folderPath);
            File[] fileList = csvFolder.listFiles();
            int filesLeft = fileList.length;

            while (filesLeft > 0) {

                this.startService(compressionService);

                filesLeft = filesLeft - 1;
            }

        }

        if (gpsON) {
            //noinspection MissingPermission
            myLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);
            //noinspection MissingPermission
            myLocationManager.addGpsStatusListener(this);
        }

    }

    @Override
    protected void onPause() {
        super.onPause();

        if (gpsON) {
            //noinspection MissingPermission
            myLocationManager.removeUpdates(this);
        }

        File zipFolder = new File(zipPath);
        File[] zipList = zipFolder.listFiles();

        if (zipList != null && zipList.length != 0) {

            wifiInfo = wifiManager.getConnectionInfo();

            if (wifiInfo != null && wifiInfo.getNetworkId() != -1) {

                this.startService(uploadService);

            }

        }

    }

    //    Called when user press the "Initialise GPS" button
    public void initialiseGPS(View view) {

        recordedYet = false;
        gpsON = true;

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
        String waitGPS = "Please wait for the GPS to fix your location.";
        infoDisplay.setText(waitGPS);
    }

    // Called when user presses the "start" button
    public void startRecording(View view) {
        recordedYet = true;

        Intent changeActivity = new Intent(this, SecondActivity.class);

        startActivity(changeActivity);

    }

    @Override
    public void onGpsStatusChanged(int event) {

        if (!recordedYet) {
            initialiseButton.setEnabled(false);

//        Ensures the GPS is fixed before the user starts recording
            switch (event) {
                case GpsStatus.GPS_EVENT_SATELLITE_STATUS:
                    if (myLastLocation != null)
                        isGPSFixed = (SystemClock.elapsedRealtime() - myLastLocationMillis) < 3000;

                    if (isGPSFixed) {

                        if (!startButton.isEnabled()) {
//                        Updates text to tell user they can start recording
                            String recordGPS = "You may now start recording.";
                            infoDisplay.setText(recordGPS);
                        }

                        startButton.setEnabled(true);
                    } else {
                        startButton.setEnabled(false);
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

    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }

    @Override
    public void onProviderEnabled(String provider) {

    }

    @Override
    public void onProviderDisabled(String provider) {

    }

}