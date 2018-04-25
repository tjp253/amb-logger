package uk.ac.nottingham.eaxtp1.CradleRideLogger;

import android.Manifest;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
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
import android.os.CountDownTimer;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.NumberPicker;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static uk.ac.nottingham.eaxtp1.CradleRideLogger.GPSService.gpsData;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.GPSService.sGPS;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.NetworkReceiver.wifiConnected;

public class MainActivity extends Activity implements View.OnClickListener, LocationListener, GpsStatus.Listener {

    final String testPref = "TestingMode", initPref = "StillInitialise";
    boolean initTest, testMode = false;  // TODO: Set 'testMode' to false.
    static boolean testing;

    boolean ambMode = true;
    static String amb, troll;

    String TAG = "CRL_MainActivity";

    Intent uploadService;
    Intent audioService, gpsService, imuService, loggingService;

    SharedPreferences preferences;
    SharedPreferences.Editor prefEditor;
    AlertDialog disclosureDialog, policyDialog, checkDialog, delayDialog;
    Button adButt;
    MenuItem autoStopCheckbox, buttDelay, buttTimeout, testCheck, initB4Test;
    // Strings for SharedPreferences. TODO: NOTHING! DO NOT EDIT!
    final String keyDelay = "DelayTime", keyAS = "AutoStop", keyTimeout = "GPS Timeout";
    final String user_ID = "User ID", keyDisc = "NotSeenDisclosure2", keyInst = "FirstInstance", keyFirst = "firstLogin";

    int timeDelay, newValue, posDelay, timeOut;
    boolean delayNotTimeout;
    static int userID;

    final int PERMISSION_GPS = 2, PERMISSION_AUDIO = 25;

    CountDownTimer timeoutTimer, removalTimer;

    public Button recordButt, cancelButt;
    public TextView instructDisplay, versionView;

    protected LocationManager myLocationManager;

    //    Sets up variables for the GPS fix-check
    boolean gpsFixed;
    long myLastLocationMillis;
    Location myLastLocation;
    private ProgressBar loadingAn;

    boolean initialising, positioned, buttPressed, gGranted, aGranted, cancelGPS, displayOn;
    static boolean recording, compressing, moving,
            crashed, forcedStop, gravityPresent,    // forcedStop set to true when AutoStop has been used.
            autoStopOn;

    @SuppressLint("WifiManagerLeak")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        preferences = getSharedPreferences("myPreferences", MODE_PRIVATE);
        prefEditor = preferences.edit();
//        Initialises a Unique ID for each user.
        if (preferences.getBoolean(keyFirst, true)) {

            showDisclosure();

            Random random = new Random();
            int rndUserID = 10000000 + random.nextInt(90000000);

            prefEditor.putBoolean(keyFirst, false);
            prefEditor.putInt(user_ID, rndUserID);
            prefEditor.commit();
        } else {
//        Shows Disclosure Agreement.
//        TODO: remove the '!' below when code is finalised.
            if (preferences.getBoolean(keyDisc, true)) {
                prefEditor.putBoolean(keyDisc, true);
                prefEditor.commit();
                showDisclosure();
            }
        }

        userID = preferences.getInt(user_ID, 1);

        instructDisplay = findViewById(R.id.instructDisplay);
        versionView = findViewById(R.id.versionView);

        String version = getString(R.string.id_string) + String.valueOf(userID) + getString(R.string.version_string) ;
//        Gets the versionName from the app gradle to display.
        try {
            PackageInfo packageInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            version = version + packageInfo.versionName;
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (ambMode) {
            version += getString(R.string.centre_ntt);
        }
        versionView.setText(version);

        recordButt = findViewById(R.id.button_Record);
        recordButt.setOnClickListener(this);
        cancelButt = findViewById(R.id.butt_Cancel);
        cancelButt.setOnClickListener(this);
        cancelButt.setVisibility(View.GONE);

        instructDisplay.setText(R.string.startGPS);

        recording = false;
        initialising = false;
        compressing = false;
        crashed = false;

//        Checks (and asks for) permission on app start-up
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !preferences.getBoolean(keyDisc, true)) {

            permissionCheck();

        }

        if (ambMode) {
            Intent ambSelect = new Intent(this, AmbSelect.class);
            startActivity(ambSelect);
        }

        uploadService = new Intent(this, UploadService.class);
        if (!wifiConnected) {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) {
                NetworkInfo info = cm.getActiveNetworkInfo();
                if (info != null && info.getType() == ConnectivityManager.TYPE_WIFI) {
                    wifiConnected = true;
                    Log.i(TAG, "Wifi Connected.");

                    File finishedFolder = new File(String.valueOf(getExternalFilesDir("Finished")));
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

        loadingAn = findViewById(R.id.initProgress);
        loadingAn.setVisibility(View.GONE);

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

        if (notMan != null) {
            notMan.cancel(notID);
        }
        displayOn = true;

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
        displayOn = false;

        if (crashed) {
            onCrash();
        }

    }

    @SuppressWarnings("MissingPermission")
    public void startInitialising() {
        initialising = true;     recording = false;
        positioned = false;
        recordButt.setEnabled(false);
        recordButt.setText(R.string.butt_init);
        loadingAn.setVisibility(View.VISIBLE);      cancelButt.setVisibility(View.VISIBLE);
        gpsData = "";
        if (!testing) {
            myLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);
            myLocationManager.addGpsStatusListener(this);
        }

        instructDisplay.setText(R.string.initialising);

        startService(audioService);
        Log.i(TAG, "startInitialising");
        gpsTimer();
    }

    public void stopInitialising() {
        loadingAn.setVisibility(View.GONE);     cancelButt.setVisibility(View.GONE);
        stopListening();
        if (timeoutTimer != null) {
            timeoutTimer.cancel();
        }
        stopAll();
        recordButt.setText(R.string.butt_start);
    }

    public void onCrash() {
        stopAll();
        initialising = false;
        recordButt.setEnabled(false);
        instructDisplay.setText(R.string.crashed);
    }

    public void startAll() {
        recording = true;
        initialising = false;
        forcedStop = false;
        loadingAn.setVisibility(View.GONE);     cancelButt.setVisibility(View.GONE);

        instructDisplay.setText(R.string.recording);
        startService(audioService);
        if (!testing) {
            startService(gpsService);
        } else {
            sGPS = "";
        }
        startService(loggingService);
        startService(imuService);

        recordButt.setText(R.string.butt_stop);
        recordButt.setEnabled(true);

        if (timeoutTimer != null) {
            timeoutTimer.cancel();
        }

        gpsRemoval();
    }

    public void stopAll() {
        stopService(imuService);
        stopService(audioService);
        stopService(gpsService);
        stopService(loggingService);

        recording = false;
        positioned = false;

        recordButt.setText(R.string.butt_start);
        recordButt.setEnabled(true);
    }

    public void stopLogging() {
        instructDisplay.setText(R.string.finished);

        recordButt.setEnabled(true);
    }

    public void stopListening() {
        //noinspection MissingPermission
        myLocationManager.removeUpdates(this);
    }

    @Override
    public void onClick(View v) {

        if (v == recordButt) {

            if (!recording && !forcedStop) { // Start recording data
                //        Re-checks (and asks for) the GPS permission needed
                if ( Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                        !(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                                checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                                checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) ) {

                    buttPressed = true;

                    permissionCheck();

                } else {

                    if (!testing || initTest) {
                        startInitialising();
                    } else {
                        startAll();
                    }
                }

            } else { // Stop recording data

                stopAll();

                stopLogging();

            }

        } else if (v == cancelButt && !recording) {
            stopInitialising();
            instructDisplay.setText(R.string.startGPS);
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
                        Log.i(TAG, "Fixed and in Position");
                        startAll();
                    }

                    break;
                case GpsStatus.GPS_EVENT_FIRST_FIX:

                    gpsFixed = true;

                    break;
            }
        }
    }

    // Allow time for phone positioning before recording - less cut-off needed in analysis?
    public void gpsRemoval() {
        removalTimer = new CountDownTimer(1000,1000) {
            public void onTick(long millisUntilFinished) {}

            @Override
            public void onFinish() {
                Log.i(TAG, "Stop GPS");
                cancelGPS = true;
            }
        }.start();
    }

    public void gpsTimer() {
        if (timeDelay == 0) {
            Log.i(TAG, "Positioned");
            positioned = true;
            posDelay = 60;
        } else {
            posDelay = timeDelay;
        }

        timeoutTimer = new CountDownTimer(timeOut*1000, posDelay*1000) {    // TODO: Set to timeOut & posDelay

            public void onTick(long millisUntilFinished) {
                if (millisUntilFinished <= (timeOut-timeDelay) * 1000 && !positioned) {
                    Log.i(TAG, "Positioned");
                    positioned = true;          // Allow time for phone positioning before recording
                    if (testing) {
                        startAll();
                    }
                }
            }

            @Override
            public void onFinish() {
                if (initialising && !recording) {
                    Log.i(TAG, "GPS Timed Out");
                    stopInitialising();     // Cancels recording if the GPS can't get a fix within a reasonable time.
                    instructDisplay.setText(R.string.failed);
                    if (!displayOn) {
                        gpsFailNotify();
                    }
                }
            }
        }.start();
    }

    @Override
    public void onLocationChanged(Location location) {

        if (!cancelGPS && !crashed) {
            if (location == null) return;

            if (!recording) {
                myLastLocationMillis = SystemClock.elapsedRealtime();

                myLastLocation = location;

                String sLat = String.valueOf(location.getLatitude());
                String sLong = String.valueOf(location.getLongitude());
                String sSpeed = String.valueOf(location.getSpeed());
                String sGTime = String.valueOf(location.getTime());
                String sAcc = String.valueOf(location.getAccuracy());
                String sAlt = String.valueOf(location.getAltitude());
                String sBear = String.valueOf(location.getBearing());
                String sRT = String.valueOf(location.getElapsedRealtimeNanos());

                List<String> dataList = Arrays.asList(sLat, sLong, sSpeed, sGTime, sAcc, sAlt, sBear, sRT);
                gpsData = TextUtils.join(",", dataList);
            }
        } else {
            myLocationManager.removeUpdates(this);
            cancelGPS = false;
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

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_GPS:
                gGranted = (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED);
                break;
            case PERMISSION_AUDIO:
                aGranted = (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED);
                break;
        }

        if ( !(gGranted && aGranted) && buttPressed ) {
            instructDisplay.setText(R.string.permissions);
        } else if (buttPressed) {
            startInitialising();
        }
        buttPressed = false;
    }

    public void showDisclosure() {
        View checkboxView = View.inflate(this, R.layout.disclosure, null);
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
                        prefEditor.putBoolean(keyDisc, false);
                        prefEditor.putBoolean(keyInst, true);
                        prefEditor.commit();
                    }
                });
        disclosureDialog = builder.create();

        if (preferences.getBoolean(keyInst, true)) {
            disclosureDialog.show();
            adButt = disclosureDialog.getButton(AlertDialog.BUTTON_POSITIVE);
            adButt.setEnabled(false);
            prefEditor.putBoolean(keyInst, false);
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
                        prefEditor.putBoolean(keyAS, autoStopOn).commit();
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

    public void timePicker() {
        if (delayNotTimeout) {
            newValue = timeDelay;
        } else {
            newValue = timeOut;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View delayView = View.inflate(this, R.layout.delay_picker, null);
        builder .setTitle("Start Delay")
                .setView(delayView)
                .setPositiveButton(R.string.butt_ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (delayNotTimeout) {
                            if (timeDelay != newValue) {
                                timeDelay = newValue;
                                changeDelay();
                            }
                        } else {
                            if (timeOut != newValue) {
                                timeOut = newValue;
                                changeTimeout();
                            }
                        }
                    }
                })
                .setNegativeButton(R.string.butt_cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                    }
                });

        NumberPicker dP = delayView.findViewById(R.id.numberPicker);
        if (delayNotTimeout) {
            dP.setMinValue(0);
            dP.setMaxValue(timeOut - 1);
            dP.setValue(timeDelay);
        } else {
            dP.setMinValue(30);
            dP.setMaxValue(300);
            dP.setValue(timeOut);
        }
        dP.setWrapSelectorWheel(false);
        dP.setOnValueChangedListener(new NumberPicker.OnValueChangeListener() {
            @Override
            public void onValueChange(NumberPicker picker, int oldVal, int newVal) {
                newValue = newVal;
            }
        });

        delayDialog = builder.create();
        delayDialog.show();

    }

    public void changeDelay() {
        prefEditor.putInt(keyDelay, timeDelay).commit();
        buttDelay.setTitle(getString(R.string.menu_delay) + timeDelay + getString(R.string.menu_seconds) );
    }

    public void changeTimeout() {
        prefEditor.putInt(keyTimeout, timeOut).commit();
        buttTimeout.setTitle(getString(R.string.menu_timeout) + timeOut + getString(R.string.menu_seconds) );
        if (timeDelay >= timeOut) {
            timeDelay = timeOut - 1;
            changeDelay();
        }
    }

    public void setUpToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.inflateMenu(R.menu.main_menu);

        if (preferences.contains(keyAS)) {
            autoStopOn = preferences.getBoolean(keyAS, true);
        } else {
            autoStopOn = true;
            prefEditor.putBoolean(keyAS, true).commit();
        }

        autoStopCheckbox = toolbar.getMenu().findItem(R.id.autoStop);
        autoStopCheckbox.setChecked(autoStopOn);

        buttDelay = toolbar.getMenu().findItem(R.id.delayTime);
        buttTimeout = toolbar.getMenu().findItem(R.id.timeOutItem);

        if (preferences.contains(keyDelay)) {
            timeDelay = preferences.getInt(keyDelay, 10);
        } else {
            timeDelay = 0;
        }
        changeDelay();

        if (preferences.contains(keyTimeout)) {
            timeOut = preferences.getInt(keyTimeout, 60);
        } else {
            timeOut = 60;
        }
        changeTimeout();

        if (testMode) {
            testCheck = toolbar.getMenu().findItem(R.id.testingItem);
            testCheck.setVisible(true);
            if (preferences.contains(testPref)) {
                testing = preferences.getBoolean(testPref, false);
            }
            testingMode();

            initB4Test = toolbar.getMenu().findItem(R.id.testInitItem);
            initB4Test.setVisible(true);
            if (preferences.contains(initPref)) {
                initTest = preferences.getBoolean(initPref, false);
            }
            testInitMode();
        }

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
                            prefEditor.putBoolean(keyAS, autoStopOn).commit();
                            autoStopToast();
                        }
                        return true;
                    case R.id.delayTime:
                        delayNotTimeout = true;
                        timePicker();
                        return true;
                    case R.id.timeOutItem:
                        delayNotTimeout = false;
                        timePicker();
                        return true;
                    case R.id.testingItem:
                        testing = !item.isChecked();
                        testingMode();
                        return true;
                    case R.id.testInitItem:
                        initTest = !item.isChecked();
                        testInitMode();
                        return true;
                }
                return false;
            }
        });
    }

    public void testingMode() {
        prefEditor.putBoolean(testPref, testing).commit();
        testCheck.setChecked(testing);
        String message;
        if (testing) {
            message = getString(R.string.test_on);
        } else {
            message = getString(R.string.test_off);
        }
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    public void testInitMode() {
        prefEditor.putBoolean(initPref, initTest).commit();
        initB4Test.setChecked(initTest);
        String message;
        if (initTest) {
            message = getString(R.string.test_init_on);
        } else {
            message = getString(R.string.test_init_off);
        }
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
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

    static NotificationManager notMan;     int notID = 2525;  static int foreID = 1992;
    Notification.Builder notBuild;
    Intent restartApp;
    PendingIntent goToApp;
    public void gpsFailNotify() {
        restartApp = new Intent(this, MainActivity.class)
                .setAction(Intent.ACTION_VIEW)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .putExtra("clicked", true);

        goToApp = PendingIntent.getActivity(this, 0, restartApp, PendingIntent.FLAG_UPDATE_CURRENT);

        notBuild = new Notification.Builder(this)
                .setSmallIcon(R.drawable.info_symb)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.failed))
                .setContentIntent(goToApp)
                .setAutoCancel(true);

        notMan = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (notMan != null) {
            notMan.notify(notID, notBuild.build());
        }
    }

    // Stops all services if left on incorrectly (by AndroidStudio, usually)
    @Override
    protected void onStart() {
        super.onStart();
        if (!initialising && !recording) {
            stopListening();
            stopAll();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
//        Ensures only one instance in normal usage. App restarts differently with Ctrl-F10 in Studio...
        prefEditor.putBoolean(keyInst, true).commit();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopAll();
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {}

    @Override
    public void onProviderEnabled(String provider) {}

    @Override
    public void onProviderDisabled(String provider) {}

}