package uk.ac.nottingham.eaxtp1.CradleRideLogger;

import android.Manifest;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.widget.Toolbar;
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
import java.util.Random;

import static uk.ac.nottingham.eaxtp1.CradleRideLogger.GPSService.gpsData;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.GPSService.sGPS;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.NetworkReceiver.wifiConnected;

public class MainActivity extends Activity implements View.OnClickListener {

    final String testPref = "TestingMode";
    static boolean gpsOff;

    Intent ambSelect;   final int ambStart = 1132, ambEnd = 1133;    final static String ambExtra = "EndLogging";

    String TAG = "CRL_MainActivity";
    static int foreID = 1992;   //static NotificationChannel foreChannel = new NotificationChannel("NotChannel", "myNotChannel", 1);

    Intent uploadService;
    Intent audioService, gpsService, imuService, loggingService, gpsTimerService;

    SharedPreferences preferences;
    SharedPreferences.Editor prefEditor;
    AlertDialog disclosureDialog, policyDialog, checkDialog, delayDialog;
    Button adButt;  int nItems = 2;
    MenuItem autoStopCheckbox, buttDelay, buttTimeout, testCheck, buttBuffS, buttBuffE;
    // Strings for SharedPreferences. TODO: NOTHING! DO NOT EDIT!
    final static String keyAS = "AutoStop", keyDelay = "DelayTime", keyTimeout = "GPS Timeout",
            keyFCheck = "CheckFS", keyG = "Gravity Present", keyFS = "MaxFS",
            keyBuffStart = "BuffStart", keyBuffEnd = "BuffEnd";
    final String user_ID = "User ID", keyDisc = "NotSeenDisclosure2", keyInst = "FirstInstance", keyFirst = "firstLogin";

    int selectValue;    int[] prefTimes;
    static int userID;

    final int PERMISSION_GPS = 2, PERMISSION_AUDIO = 25;

    public Button recordButt, cancelButt;
    public TextView instructDisplay, versionView;

    private ProgressBar loadingAn;

    boolean initialising, buttPressed, displayOn, buffing, fileEmpty;
    static boolean recording, compressing, moving,
            crashed, forcedStop,    // forcedStop set to true when AutoStop has been used.
            autoStopOn, buffEnd;

    @SuppressLint("WifiManagerLeak")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        instructDisplay = findViewById(R.id.instructDisplay);

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
        } else if (preferences.getBoolean(keyDisc, true)) {
//        Shows Disclosure Agreement.
//        TODO: remove the '!' below when code is finalised.
                prefEditor.putBoolean(keyDisc, true);
                prefEditor.commit();
                showDisclosure();
        }

        userID = preferences.getInt(user_ID, 1);

        versionView = findViewById(R.id.versionView);
        String version = getString(R.string.id_string) + String.valueOf(userID) + getString(R.string.version_string) ;
//        Gets the versionName from the app gradle to display.
        try {
            PackageInfo packageInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            version = version + packageInfo.versionName;
        } catch (Exception e) {
            e.printStackTrace();
        }
        versionView.setText(version);

        recordButt = findViewById(R.id.button_Record);
        recordButt.setOnClickListener(this);
        cancelButt = findViewById(R.id.butt_Cancel);
        cancelButt.setOnClickListener(this);

        instructDisplay.setText(R.string.startGPS);

        recording = false;
        initialising = false;
        compressing = false;
        crashed = false;

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

        gpsTimerService = new Intent(getApplicationContext(), GPSTimerService.class);

        setUpToolbar();

        loadingAn = findViewById(R.id.initProgress);
        loadingAn.setVisibility(View.GONE);

        if (BuildConfig.AMB_MODE) {
            ambSelect = new Intent(this, AmbSelect.class);
        }

        if (preferences.getBoolean(keyFCheck, true)) {
            Intent fsService = new Intent(getApplicationContext(), FSChecker.class);
            startService(fsService);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !preferences.getBoolean(keyDisc, true)) {

            permissionCheck();

        }

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
        recordButt.setEnabled(false);
        recordButt.setText(R.string.butt_init);
        loadingAn.setVisibility(View.VISIBLE);      cancelButt.setVisibility(View.VISIBLE);
        gpsData = "";
        startService(gpsTimerService);
        LocalBroadcastManager.getInstance(this).registerReceiver(BReceiver, new IntentFilter(timerFilter));

        instructDisplay.setText(R.string.initialising);

        startService(audioService);
        Log.i(TAG, "startInitialising");
    }

    public void stopInitialising() {
        loadingAn.setVisibility(View.GONE);     cancelButt.setVisibility(View.GONE);
        stopService(gpsTimerService);
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

        if (!gpsOff) {
            startService(gpsService);
        } else {
            sGPS = "";
        }
        startService(loggingService);
        startService(imuService);
        LocalBroadcastManager.getInstance(this).registerReceiver(BReceiver, new IntentFilter(loggingFilter));
    }

//    Separate the display / UI changes - to enable buffer to work nicely.
    public void changeDisplay() {
        loadingAn.setVisibility(View.GONE);
        cancelButt.setVisibility(View.GONE);
        instructDisplay.setText(R.string.recording);
        recordButt.setText(R.string.butt_stop);
        recordButt.setEnabled(true);
    }

    public void stopAll() {
        stopService(gpsTimerService);

        stopService(imuService);
        stopService(audioService);
        stopService(gpsService);
        stopService(loggingService);

        recording = false;

        recordButt.setText(R.string.butt_start);
        recordButt.setEnabled(true);
    }

    public void stopLogging() {
        if (fileEmpty) {
            instructDisplay.setText(R.string.file_empty);
        } else {
            instructDisplay.setText(R.string.finished);
        }

        recordButt.setEnabled(true);
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

                } else if (buffing) {
                    stopInitialising();
                    instructDisplay.setText(R.string.file_empty);
                    buffing = false;
                } else if (BuildConfig.AMB_MODE) {
                    startActivityForResult(ambSelect, ambStart);
                } else {
                    startInitialising();
                }

            } else { // Stop recording data

                if (BuildConfig.AMB_MODE) {
                    startActivityForResult(ambSelect, ambEnd);
                } else {
                    stopAll();

                    stopLogging();
                }
            }

        } else if (v == cancelButt && !recording) {
            stopInitialising();
            instructDisplay.setText(R.string.startGPS);
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
        if (grantResults.length != 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (requestCode == PERMISSION_GPS) {
                permissionCheck();
            } else {
                if (buttPressed) {
                    startInitialising();
                }
            }
        } else {
            instructDisplay.setText(R.string.permissions);
        }
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

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            permissionCheck();
                        }
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
    boolean timeChanged;
    public void timePicker(final int whichTime) {

        selectValue = prefTimes[whichTime];

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View delayView = View.inflate(this, R.layout.delay_picker, null);
        builder .setTitle( getResources().getStringArray(R.array.timePickerArray)[whichTime] )
                .setView(delayView)
                .setPositiveButton(R.string.butt_ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (timeChanged) {
                            prefTimes[whichTime] = selectValue;
                            changeTime(whichTime);
                        }
                    }
                })
                .setNegativeButton(R.string.butt_cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {}
                });

        NumberPicker dP = delayView.findViewById(R.id.numberPicker);
        int minVal = 0, maxVal = 5;
        switch (whichTime) {
            case 0:
                maxVal = prefTimes[1] - 1;
                break;
            case 1:
                minVal = 30;    maxVal = 300;
                break;
        }
        dP.setMinValue(minVal);
        dP.setMaxValue(maxVal);
        dP.setValue(prefTimes[whichTime]);
        dP.setWrapSelectorWheel(false);
        dP.setOnValueChangedListener(new NumberPicker.OnValueChangeListener() {
            @Override
            public void onValueChange(NumberPicker picker, int oldVal, int newVal) {
                timeChanged = newVal != oldVal;
                selectValue = newVal;
            }
        });

        delayDialog = builder.create();
        delayDialog.show();

    }

    public void changeTime(int choice) {
        switch (choice) {
            case 0:
                prefEditor.putInt(keyDelay, prefTimes[choice]).commit();
                buttDelay.setTitle(getString(R.string.menu_delay) + prefTimes[choice] + getString(R.string.menu_seconds) );
                break;
            case 1:
                prefEditor.putInt(keyTimeout, prefTimes[choice]).commit();
                buttTimeout.setTitle(getString(R.string.menu_timeout) + prefTimes[choice] + getString(R.string.menu_seconds) );
                if (prefTimes[0] >= prefTimes[choice]) {
                    prefTimes[0] = prefTimes[choice] - 1;
                    changeTime(0);
                }
                break;
            case 2:
                prefEditor.putInt(keyBuffStart, prefTimes[choice]).commit();
                buttBuffS.setTitle(getString(R.string.menu_buff_start) + prefTimes[choice]);
                break;
            case 3:
                prefEditor.putInt(keyBuffEnd, prefTimes[choice]).commit();
                buttBuffE.setTitle(getString(R.string.menu_buff_end) + prefTimes[choice]);
                buffEnd = prefTimes[choice] != 0;
                break;
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

        if (BuildConfig.CROWD_MODE) {
            nItems = 4;
        }
        prefTimes = new int[nItems];

        buttDelay = toolbar.getMenu().findItem(R.id.delayTime);
        buttTimeout = toolbar.getMenu().findItem(R.id.timeOutItem);
        if (preferences.contains(keyDelay)) {
            prefTimes[0] = preferences.getInt(keyDelay, 10);
        } else {
            prefTimes[0] = 0;
        }

        if (preferences.contains(keyTimeout)) {
            prefTimes[1] = preferences.getInt(keyTimeout, 60);
        } else {
            prefTimes[1] = 60;
        }

        if (BuildConfig.CROWD_MODE) {
            toolbar.getMenu().setGroupVisible(R.id.menuBuffGroup, true);
            buttBuffS = toolbar.getMenu().findItem(R.id.buffStart);
            buttBuffE = toolbar.getMenu().findItem(R.id.buffEnd);

            if (preferences.contains(keyBuffStart)) {
                prefTimes[2] = preferences.getInt(keyBuffStart, 0);
            } else {
                prefTimes[2] = 0;
            }

            if (preferences.contains(keyBuffEnd)) {
                prefTimes[3] = preferences.getInt(keyBuffEnd, 0);
            } else {
                prefTimes[3] = 0;
            }
        } else if (BuildConfig.TEST_MODE) {
            testCheck = toolbar.getMenu().findItem(R.id.testingItem);
            testCheck.setVisible(true);
            if (preferences.contains(testPref)) {
                gpsOff = preferences.getBoolean(testPref, false);
            }
            changeTestMode();
        }

        for (int x = 0; x<nItems; x++) {
            changeTime(x);
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
                        timePicker(0);
                        return true;
                    case R.id.timeOutItem:
                        timePicker(1);
                        return true;

                    case R.id.buffStart:
                        timePicker(2);
                        return true;
                    case R.id.buffEnd:
                        timePicker(3);
                        return true;

                    case R.id.testingItem:
                        gpsOff = !item.isChecked();
                        changeTestMode();
                        return true;
                }
                return false;
            }
        });
    }

    public void changeTestMode() {
        int stringID;
        prefEditor.putBoolean(testPref, gpsOff).commit();
        testCheck.setChecked(gpsOff);
        if (gpsOff) {
            stringID = R.string.test_on;
        } else {
            stringID = R.string.test_off;
        }
        Toast.makeText(this, getString(stringID), Toast.LENGTH_SHORT).show();
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

    static NotificationManager notMan;     int notID = 2525;
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
//            stopListening();
            stopAll();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK && data.getBooleanExtra(ambExtra, false)) {
            switch (requestCode) {
                case ambStart:
                    startInitialising();
                    break;
                case ambEnd:
                    stopAll();  stopLogging();
                    break;
            }
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

    static final String timerFilter = "TimerResponse", timerInt = "tResponse",
            loggingFilter = "LoggingResponse", loggingInt = "lResponse";
    private BroadcastReceiver BReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (initialising) {
                switch (intent.getIntExtra(timerInt, 1)) {
                    case 1:
                        buffing = false;
                        startAll();
                        changeDisplay();
                        break;
                    case 0:
                        stopInitialising();     // Cancels recording if the GPS can't get a fix within a reasonable time.
                        instructDisplay.setText(R.string.failed);
                        if (!displayOn) {
                            gpsFailNotify();
                        }
                        cancelBM();
                        break;
                    case 2:
                        buffing = true;
                        changeDisplay();
                        break;
                    case 3:
                        cancelBM();
                        break;
                }
            } else {
                switch (intent.getIntExtra(loggingInt,1)) {
                    case 0:
                        fileEmpty = true;
                        break;
                    case 1:
                        fileEmpty = false;
                        cancelBM();
                        break;
                }
            }
        }
    };

    public void cancelBM() {
        LocalBroadcastManager.getInstance(MainActivity.this).unregisterReceiver(BReceiver);
    }

}