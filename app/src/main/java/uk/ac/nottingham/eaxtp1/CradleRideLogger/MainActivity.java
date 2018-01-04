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
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.v4.app.ActivityCompat;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

import static uk.ac.nottingham.eaxtp1.CradleRideLogger.GPSService.gpsData;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.IMUService.myQ;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.NetworkReceiver.wifiConnected;

public class MainActivity extends Activity implements View.OnClickListener, LocationListener, GpsStatus.Listener {

    String TAG = "Main Activity";

    Intent uploadService;//, recordingService;
    Intent audioService, gpsService, imuService, loggingService;

    SharedPreferences preferences;
    SharedPreferences.Editor prefEditor;
    String user_ID = "User ID";
    static int userID;

    int PERMISSION_GPS = 2, PERMISSION_AUDIO = 25;

    Timer shutoffTimer, timeoutTimer, positioningTimer;

    public Button recordButton;
    public TextView instructDisplay, versionView;

    protected LocationManager myLocationManager;

    //    Sets up variables for the GPS fix-check
    boolean gpsFixed;
    long myLastLocationMillis;
    Location myLastLocation;

    boolean initialising, positioned;
    static boolean recording, compressing, moving,
            crashed, forcedStop, gravityPresent,    // forcedStop set to true when AutoStop has been used.
            autoStopOn;

    static String mainPath, finishedPath;

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
            if (preferences.getBoolean("NotSeenDisclosure2", true)) {
//                Log.e("ID","1");
                prefEditor.putBoolean("NotSeenDisclosure2", true);
                prefEditor.commit();
                showDisclosure();
            }
        }

        userID = preferences.getInt(user_ID, 1);

        instructDisplay = findViewById(R.id.instructDisplay);
        versionView = findViewById(R.id.versionView);

        String version = "Unique ID: " + String.valueOf(userID) +"\n"+ "Version: " ;
//        Gets the versionName from the app gradle to display.
        try {
            PackageInfo packageInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            version = version + packageInfo.versionName;
        } catch (Exception e) {
            e.printStackTrace();
        }
        versionView.setText(version);

        recordButton = findViewById(R.id.button_Record);
        recordButton.setOnClickListener(this);

//        Disables the Start button
        recordButton.setEnabled(true); // TODO: set 'false' when creating signed build.

        instructDisplay.setText(R.string.startGPS);

        recording = false;
        initialising = false;
        compressing = false;
        crashed = false;

        mainPath = String.valueOf(getExternalFilesDir(""));
        finishedPath = mainPath + "/Finished";

//        Checks (and asks for) permission on app start-up
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !preferences.getBoolean("NotSeenDisclosure2", true)) {

            permissionCheck();

        }

        uploadService = new Intent(this, UploadService.class);
        if (!wifiConnected) {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) {
                NetworkInfo info = cm.getActiveNetworkInfo();
                if (info != null && info.getType() == ConnectivityManager.TYPE_WIFI) {
                    wifiConnected = true;
                    Log.i(TAG, "Wifi Connected.");

                    File finishedFolder = new File(finishedPath);
                    File[] finishedList = finishedFolder.listFiles();

                    if (finishedList != null && finishedList.length != 0) {
                        this.startService(uploadService);
                    }
                }
            }
        }
        audioService = new Intent(getApplicationContext(), AudioService.class);
        gpsService = new Intent(getApplicationContext(), GPSService.class);
        imuService = new Intent(getApplicationContext(), IMUService.class);
        loggingService = new Intent(getApplicationContext(), LoggingService.class);

        myLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        setUpToolbar();

        gravityCheck();
    }

    public void gravityCheck() {
        SensorManager manager = (SensorManager) getSystemService(SENSOR_SERVICE);
        if (manager != null) {      // Mandatory check to remove AndroidStudio NullPointer warning
            Sensor gravity = manager.getDefaultSensor(Sensor.TYPE_GRAVITY);
            SensorEventListener listener = new SensorEventListener() {
                @Override
                public void onSensorChanged(SensorEvent event) {
//                Log.i("Main", "Gravity sensing");
                }

                @Override
                public void onAccuracyChanged(Sensor sensor, int accuracy) {

                }
            };
            if (manager.registerListener(listener, gravity, SensorManager.SENSOR_DELAY_NORMAL)) {
                gravityPresent = true;
                manager.unregisterListener(listener);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

////        Resumes GPS when initialising.
//        if (initialising) {
//            startInitialising();
//        }

        if (crashed) {
            onCrash();
        }

        if (forcedStop) {
            stopLogging();
            forcedStop = false;
        }

    }

    @Override
    protected void onPause() {
        super.onPause();

////        Stops GPS from draining the battery.
//        if (initialising && !recording) {
//            stopInitialising();
//        }

        if (crashed) {
            onCrash();
        }

    }

    @SuppressWarnings("MissingPermission")
    public void startInitialising() {
        initialising = true;
        recording = false;
        recordButton.setEnabled(false);

        myLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);
        myLocationManager.addGpsStatusListener(this);

        instructDisplay.setText(R.string.initialising);

        startService(audioService);

        gpsTimeOut();

        allowPositioning();
    }

    public void stopInitialising() {
        stopListening();
        if (positioningTimer != null) {
            positioningTimer.cancel();
        }
        stopAll();
    }

    public void onCrash() {
        stopAll();
        initialising = false;
        recordButton.setText(R.string.button_Start);
        recordButton.setEnabled(false);
        instructDisplay.setText(R.string.crashed);
    }

    public void startAll() {
        recording = true;
        initialising = false;
        forcedStop = false;

        instructDisplay.setText(R.string.recording);

//        myQ.clear();
//      Remove all elements from the queue.
        for (int i = 0; i < myQ.size(); i++) {
            try {
                myQ.remove();
            } catch (NoSuchElementException e) {    // If queue is found to be prematurely empty, exit for loop.
                Log.i(TAG, "Queue empty.");
                break;
            }
        }

        startService(gpsService);
        startService(loggingService);
        startService(imuService);

        recordButton.setText(R.string.button_Stop);
        recordButton.setEnabled(true);

        gpsShutOff();

        if (timeoutTimer != null) {
            timeoutTimer.cancel();
        }
    }

    public void stopAll() {
        stopService(imuService);
        stopService(audioService);
        stopService(gpsService);
        stopService(loggingService);

        recording = false;
        positioned = false;
    }

    public void stopLogging() {
        instructDisplay.setText(R.string.finished);

        recordButton.setText(R.string.button_Start);
        recordButton.setEnabled(true);
    }

    public void stopListening() {
        //noinspection MissingPermission
        myLocationManager.removeUpdates(this);
    }

    @Override
    public void onClick(View v) {

        if (v == recordButton) {

            if (!recording && !forcedStop) { // Start recording data
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

                startInitialising();

            } else { // Stop recording data

                stopAll();

                stopLogging();

            }

        }

    }

    @Override
    public void onGpsStatusChanged(int event) {

        if (initialising) {

//        Ensures the GPS is fixed before the user starts recording
            switch (event) {
                case GpsStatus.GPS_EVENT_SATELLITE_STATUS:
                    if (myLastLocation != null)
                        gpsFixed = (SystemClock.elapsedRealtime() - myLastLocationMillis) < 3000;

                    if (gpsFixed && positioned) {

                        startAll();

                    } else {
                        instructDisplay.setText(R.string.initialising);
                    }

                    break;
                case GpsStatus.GPS_EVENT_FIRST_FIX:

                    gpsFixed = true;

                    break;
            }
        }
    }

//    Allow time for phone positioning before recording - less cut-off needed in analysis?
    public void allowPositioning() {
        positioningTimer = new Timer();
        TimerTask timerTask = new TimerTask() {
            @Override
            public void run() {
                positioned = true;
            }
        };
        positioningTimer.schedule(timerTask, 10*1000);  // Allow ten seconds to position phone before recording data.
    }

//    Stop listening to GPS in Main Activity after 1 second. Allows GPS Service to get running while still locked.
    public void gpsShutOff() {
        shutoffTimer = new Timer();
        TimerTask timerTask = new TimerTask() {
            @Override
            public void run() {
                stopListening();
//                shutoffTimer.cancel();
            }
        };
        shutoffTimer.schedule(timerTask, 1000);
    }

//    Cancels recording if the GPS can't get a fix within a reasonable time.
    public void gpsTimeOut() {
        timeoutTimer = new Timer();
        TimerTask timerTask = new TimerTask() {
            @Override
            public void run() {
                if (initialising && !recording) {
                    stopInitialising();
                }
            }
        };
        timeoutTimer.schedule(timerTask, 60*1000);
    }

    @Override
    public void onLocationChanged(Location location) {

        if (location == null) return;

        myLastLocationMillis = SystemClock.elapsedRealtime();

        myLastLocation = location;

        if (gpsFixed && !recording) {
            String sLat = String.valueOf(location.getLatitude());
            String sLong = String.valueOf(location.getLongitude());
            String sSpeed = String.valueOf(location.getSpeed());
            String sGTime = String.valueOf(location.getTime());
            String sAcc = String.valueOf(location.getAccuracy());
            String sAlt = String.valueOf(location.getAltitude());
            String sBear = String.valueOf(location.getBearing());
            String sRT = String.valueOf(location.getElapsedRealtimeNanos());

            List<String> dataList = Arrays.asList(sLat,sLong,sSpeed,sGTime,sAcc,sAlt,sBear,sRT);
            gpsData = TextUtils.join(",", dataList);
        }

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

    AlertDialog disclosureDialog, policyDialog, checkDialog;
    Button adButt;
    MenuItem autoStopCheckbox;

    public void showDisclosure() {
        View checkboxView = View.inflate(this, R.layout.checkbox, null);
        CheckBox checkBox = checkboxView.findViewById(R.id.checkbox);
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
                        prefEditor.putBoolean("NotSeenDisclosure2", false);
                        prefEditor.putBoolean("FirstInstance", true);
                        prefEditor.commit();
                    }
                });
        disclosureDialog = builder.create();

        if (preferences.getBoolean("FirstInstance", true)) {
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
                .setPositiveButton(R.string.butt_ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int buttInt) {
//                  Close the Privacy Policy
                    }
                });
        policyDialog = builder.create();
        policyDialog.show();
    }

    public void checkAutoStopRemoval() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder .setTitle(R.string.as_check_title)
                .setMessage(R.string.as_check_message)
                .setPositiveButton(R.string.butt_ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        autoStopOn = !autoStopCheckbox.isChecked();
                        autoStopCheckbox.setChecked(autoStopOn);
                        prefEditor.putBoolean("AutoStop", autoStopOn);
                        prefEditor.commit();
                        autoStopToast();
                    }
                })
                .setNegativeButton(R.string.butt_cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
//                        Do nothing.
                    }
                });
        checkDialog = builder.create();
        checkDialog.show();
    }

    public void setUpToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.inflateMenu(R.menu.main_menu);

        if (preferences.contains("AutoStop")) {
            autoStopOn = preferences.getBoolean("AutoStop", true);
        } else {
            autoStopOn = true;
            prefEditor.putBoolean("AutoStop", true);
            prefEditor.commit();
        }

        autoStopCheckbox = toolbar.getMenu().findItem(R.id.autoStop);
        autoStopCheckbox.setChecked(autoStopOn);

        toolbar.setOnMenuItemClickListener(new Toolbar.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                switch (item.getItemId()) {
                    case R.id.privacyPolicy:
                        showPolicy();
                        return true;
                    case R.id.autoStop:
                        if (autoStopOn) {
                            checkAutoStopRemoval();
                        } else {
                            autoStopOn = !item.isChecked();
                            item.setChecked(autoStopOn);
                            prefEditor.putBoolean("AutoStop", autoStopOn);
                            prefEditor.commit();
                            autoStopToast();
                        }
                        return true;
                }
                return false;
            }
        });
    }

    public void autoStopToast() {
        String message;
        if (autoStopOn) {
            message = getString(R.string.as_on);
        } else {
            message = getString(R.string.as_off);
        }
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onStop() {
        super.onStop();
//        Ensures only one instance in normal usage. App restarts differently with Ctrl-F10 in Studio...
        prefEditor.putBoolean("FirstInstance", true);
        prefEditor.commit();
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {}

    @Override
    public void onProviderEnabled(String provider) {}

    @Override
    public void onProviderDisabled(String provider) {}

}