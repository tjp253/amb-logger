package uk.ac.nottingham.eaxtp1.CradleRideLogger;

import android.Manifest;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
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
//import android.util.Log;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TextView;

import java.io.File;
import java.util.Random;

@SuppressWarnings("deprecation")
public class MainActivity extends Activity implements View.OnClickListener, LocationListener, GpsStatus.Listener {

    WifiManager wifiManager;
    WifiInfo wifiInfo;

    Intent uploadService, recordingService;
    Intent audioService;

    SharedPreferences preferences;
    SharedPreferences.Editor prefEditor;
    String user_ID = "User ID";
    static int userID;

    int PERMISSION_GPS = 2, PERMISSION_AUDIO = 25;

    public Button recordButton, initialiseButton;
    public TextView instructDisplay, versionView;

    protected LocationManager myLocationManager;

//    Sets up variables for the GPS fix-check
    boolean isGPSFixed;
    long myLastLocationMillis;
    Location myLastLocation;

    boolean initialising;
    static boolean recording, compressing, moving, crashed;

    //    Initialise strings for the zipping
    static String mainPath, folderPath, zipPath;

    @SuppressLint("WifiManagerLeak")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        preferences = getSharedPreferences("myPreferences", MODE_PRIVATE);
        prefEditor = preferences.edit();
//        Initialises a Unique ID for each user.
        if (preferences.getBoolean("firstLogin", true)) {

            showDisclosure();

            Random random = new Random();
            int rndUserID = 10000000 + random.nextInt(90000000);

            prefEditor.putBoolean("firstLogin", false);
            prefEditor.putInt(user_ID, rndUserID);
            prefEditor.commit();
        } else {
//        Shows Disclosure Agreement.
//        TODO: remove the '!' below when code is finalised.
            if (preferences.getBoolean("NotSeenDisclosure", true)) {
//                Log.e("ID","1");
                prefEditor.putBoolean("NotSeenDisclosure", true);
                prefEditor.commit();
                showDisclosure();
            }
        }

        userID = preferences.getInt(user_ID, 1);

        instructDisplay = (TextView) findViewById(R.id.instructDisplay);
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

        initialiseButton.setOnClickListener(this);
        recordButton.setOnClickListener(this);

//        Disables the Start button
        recordButton.setEnabled(false); // TODO: set 'false' when creating signed build.

        instructDisplay.setText(R.string.startGPS);

        recording = false;
        initialising = false;
        compressing = false;
        crashed = false;

        mainPath = String.valueOf(getExternalFilesDir(""));
        folderPath = mainPath + "/Recording";
        zipPath = mainPath + "/Finished";

//        Checks (and asks for) permission on app start-up
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !preferences.getBoolean("NotSeenDisclosure", true)) {

            permissionCheck();

        }

        wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        uploadService = new Intent(this, UploadService.class);
        recordingService = new Intent(this, RecordingService.class);
        audioService = new Intent(this, AudioService.class);

        myLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        toolbar.inflateMenu(R.menu.main_menu);
        toolbar.setOnMenuItemClickListener(new Toolbar.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                switch (item.getItemId()) {
                    case R.id.privacyPolicy:
                        showPolicy();
                }
                return false;
            }
        });
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

            instructDisplay.setText(R.string.initialising);
        }

        if (crashed) {

            if (myLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                //noinspection MissingPermission
                myLocationManager.removeUpdates(this);
            }
            stopService(recordingService);
            stopService(audioService);
            recording = false;
            initialising = false;
            recordButton.setText(R.string.button_Start);
            recordButton.setEnabled(false);
            initialiseButton.setEnabled(false);
            instructDisplay.setText(R.string.crashed);
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
        if (!recording && !moving) {
            File zipFolder = new File(zipPath);
            File[] zipList = zipFolder.listFiles();

            if (zipList != null && zipList.length != 0) {

                wifiInfo = wifiManager.getConnectionInfo();

                if (wifiInfo != null && wifiInfo.getNetworkId() != -1) {

                    this.startService(uploadService);

                }
            }
        }

        if (crashed) {

            if (myLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                //noinspection MissingPermission
                myLocationManager.removeUpdates(this);
            }
            stopService(recordingService);
            stopService(audioService);
            recording = false;
            initialising = false;
            recordButton.setText(R.string.button_Start);
            recordButton.setEnabled(false);
            initialiseButton.setEnabled(false);
            instructDisplay.setText(R.string.crashed);
        }

    }

    @Override
    public void onClick(View v) {

        if (v == initialiseButton) {
            recording = false;

            recordButton.setEnabled(false);

//        Re-checks (and asks for) the GPS permission needed
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

                if (!(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                        checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                        checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)) {
                    permissionCheck();

                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

                if (!(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                        checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                        checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)) {
                    instructDisplay.setText(R.string.permissions);
                    return;
                }

            }

            initialising = true;

            initialiseButton.setEnabled(false);

            myLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);
            myLocationManager.addGpsStatusListener(this);

//        Updates text to ask user to wait for GPS fix
            instructDisplay.setText(R.string.initialising);
            
        } else if (v == recordButton) {

            if (!recording) { // Start recording data
                instructDisplay.setText(R.string.recording);
                startService(recordingService);
                startService(audioService);

                //noinspection MissingPermission
                myLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);
                myLocationManager.addGpsStatusListener(this);

                recording = true;
                initialising = false;

                initialiseButton.setEnabled(false);

                recordButton.setText(R.string.button_Stop);
                
            } else { // Stop recording data

                stopService(recordingService);
                stopService(audioService);

                instructDisplay.setText(R.string.finished);

                recording = false;

                recordButton.setText(R.string.button_Start);
                recordButton.setEnabled(false);
                initialiseButton.setEnabled(true);

                if (myLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    //noinspection MissingPermission
                    myLocationManager.removeUpdates(this);
                }

            }
            
        }

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
                            instructDisplay.setText(R.string.locked);
                        }

                        recordButton.setEnabled(true);
                    } else {
                        recordButton.setEnabled(false);

                        instructDisplay.setText(R.string.initialising);
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

        if (crashed) {
            myLocationManager.removeUpdates(this);
        }

    }

    @TargetApi(Build.VERSION_CODES.M)
    public void permissionCheck() {
//            Checks if GPS permission is NOT granted. Asks for permission if it isn't.
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSION_GPS);

        }

//            Checks if audio permission is NOT granted. Asks for permission if it isn't.
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, PERMISSION_AUDIO);
        }
    }

    AlertDialog disclosureDialog, policyDialog;
    Button adButt;

    public void showDisclosure() {
//        Log.i("METHOD", "showDisclosure");
        View checkboxView = View.inflate(this, R.layout.checkbox, null);
        CheckBox checkBox = (CheckBox) checkboxView.findViewById(R.id.checkbox);
        checkBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    adButt.setEnabled(true);
                } else {
                    adButt.setEnabled(false);
                }
            }
        });

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder .setTitle(R.string.ad_title)
                .setView(checkboxView)
                .setCancelable(false)
                .setPositiveButton(R.string.ad_button, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int BUTTON_POSITIVE) {
//                        Accept the disclosure agreement!
//                        Ensure only one instance.
                        prefEditor.putBoolean("NotSeenDisclosure", false);
                        prefEditor.putBoolean("FirstInstance", true);
                        prefEditor.commit();
                    }
                });
        disclosureDialog = builder.create();

        if (preferences.getBoolean("FirstInstance", true)) {
//            Log.i("FirstInstance", "is TRUE");
            disclosureDialog.show();
            adButt = disclosureDialog.getButton(AlertDialog.BUTTON_POSITIVE);
            adButt.setEnabled(false);
            prefEditor.putBoolean("FirstInstance", false);
            prefEditor.commit();
        }

    }

    public void showPolicy() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder .setTitle(R.string.privacy_title)
                .setMessage(R.string.privacy_policy)
                .setPositiveButton(R.string.butt_policy, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int buttInt) {
//                  Close the Privacy Policy
                    }
                });
        policyDialog = builder.create();
        policyDialog.show();
    }

    @Override
    protected void onStop() {
        super.onStop();
//        Log.i("METHOD", "onStop");
//        Ensures only one instance in normal usage. App restarts differently with Ctrl-F10 in Studio...
        prefEditor.putBoolean("FirstInstance", true);
        prefEditor.commit();
    }

//    If true options button is required - in "App Bar"
//    @Override
//    public boolean onCreateOptionsMenu(Menu menu) {
//        getMenuInflater().inflate(R.menu.main_menu, menu);
//        Toast.makeText(this, "Creating menu", Toast.LENGTH_SHORT).show();
//        return true;
//    }
//
//    @Override
//    public boolean onOptionsItemSelected(MenuItem item) {
//        switch (item.getItemId()) {
//            case R.id.privacyPolicy:
//                Toast.makeText(this, "Open the privacy policy", Toast.LENGTH_SHORT).show();
//        }
//
//        return super.onOptionsItemSelected(item);
//    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {}

    @Override
    public void onProviderEnabled(String provider) {}

    @Override
    public void onProviderDisabled(String provider) {}

}