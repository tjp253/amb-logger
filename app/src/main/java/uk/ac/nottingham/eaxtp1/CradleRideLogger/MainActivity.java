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
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.File;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

import static uk.ac.nottingham.eaxtp1.CradleRideLogger.AmbSelect.forcedStopAmb;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.AmbSelect.selectingAmb;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.GPSService.gpsData;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.GPSService.sGPS;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.NetworkReceiver.wifiConnected;

public class MainActivity extends Activity implements View.OnClickListener {

    static boolean gpsOff;

    Intent ambSelect, ambGPS;   final int ambStart = 1132, ambEnd = 1133;    final static String ambExtra = "EndLogging";

    String TAG = "CRL_MainActivity";
    static int foreID = 1992;   //static NotificationChannel foreChannel = new NotificationChannel("NotChannel", "myNotChannel", 1);

    Intent uploadService;
    Intent audioService, gpsService, imuService, loggingService, gpsTimerService;

    SharedPreferences preferences;
    SharedPreferences.Editor prefEditor;
    ContextThemeWrapper dialogWrapper = new ContextThemeWrapper(this, R.style.MyAlertDialog);
    AlertDialog disclosureDialog, policyDialog;
    Button adButt;
    final static String KEY_G = "Gravity Present", KEY_FS = "MaxFS", KEY_F_CHECK = "CheckFS";
    final String user_ID = "User ID", KEY_DISC = "NotSeenDisclosure2", KEY_INST = "FirstInstance", KEY_FIRST = "firstLogin";

    static int userID;

    final int PERMISSION_GPS = 2, PERMISSION_AUDIO = 25;

    public Button recordButt, cancelButt;
    public TextView instructDisplay, versionView;

    private ProgressBar loadingAn;

    boolean initialising, buttPressed, displayOn, buffing, fileEmpty;
    static boolean recording, moving,
            crashed, forcedStop;    // forcedStop set to true when AutoStop has been used.

    @SuppressLint("WifiManagerLeak")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        instructDisplay = findViewById(R.id.instructDisplay);

        preferences = getSharedPreferences(getString(R.string.pref_main), MODE_PRIVATE);
        prefEditor = preferences.edit();
//        Initialises a Unique ID for each user.
        if (preferences.getBoolean(KEY_FIRST, true)) {

            showDisclosure();

            Random random = new Random();
            int rndUserID = 10000000 + random.nextInt(90000000);

            prefEditor.putBoolean(KEY_FIRST, false);
            prefEditor.putInt(user_ID, rndUserID);
            prefEditor.apply();
        } else if (preferences.getBoolean(KEY_DISC, true)) {
//        Shows Disclosure Agreement.
//        TODO: remove the '!' below when code is finalised.
                prefEditor.putBoolean(KEY_DISC, true);
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
        loadingAn = findViewById(R.id.initProgress);
        loadingAn.setVisibility(View.GONE);

        instructDisplay.setText(R.string.startGPS);

        recording = false;
        initialising = false;
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

        if (BuildConfig.AMB_MODE) {
            ambSelect = new Intent(this, AmbSelect.class);
            ambGPS = new Intent(this,AmbGPSService.class);
        } else if (BuildConfig.TEST_MODE) {
            gpsOff = !PreferenceManager.getDefaultSharedPreferences(this).getBoolean(getString(R.string.key_pref_test),false);
        }

        if (preferences.getBoolean(KEY_F_CHECK, true)) {
            Intent fsService = new Intent(getApplicationContext(), FSChecker.class);
            startService(fsService);
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
            if (!BuildConfig.AMB_MODE) {
                stopLogging();
                forcedStop = false;
            } else if (forcedStopAmb) {
                stopLogging();
                forcedStop = false;
                forcedStopAmb = false;
                stopService(loggingService);
            } else {
                LocalBroadcastManager.getInstance(this).registerReceiver(BReceiver, new IntentFilter(loggingFilter));
            }
        }

        if (BuildConfig.AMB_MODE && selectingAmb && !recording) {
            stopService(ambGPS);
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
        loadingAn.setVisibility(View.GONE);     cancelButt.setVisibility(View.INVISIBLE);
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
        if (!BuildConfig.AMB_MODE) {
            startService(imuService);
        }
        LocalBroadcastManager.getInstance(this).registerReceiver(BReceiver, new IntentFilter(loggingFilter));
    }

//    Separate the display / UI changes - to enable buffer to work nicely.
    public void changeDisplay() {
        loadingAn.setVisibility(View.GONE);
        cancelButt.setVisibility(View.INVISIBLE);
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

        recordButt.setText(R.string.butt_start);
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
                    startService(imuService);   // Start IMU logging straight away - for entrance matting, etc.
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

            if (BuildConfig.AMB_MODE) {
                Intent stopAmbGPS = new Intent(getApplicationContext(), AmbGPSService.class);
                stopService(stopAmbGPS);
            }
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
                    if (BuildConfig.AMB_MODE) {
                        startActivityForResult(ambSelect, ambStart);
                    } else {
                        startInitialising();
                    }
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

        AlertDialog.Builder builder = new AlertDialog.Builder(dialogWrapper);
        builder .setTitle(R.string.ad_title)
                .setView(checkboxView)
                .setCancelable(false)
                .setPositiveButton(R.string.ad_button, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int BUTTON_POSITIVE) {
//                        Accept the disclosure agreement!
//                        Ensure only one instance.
                        prefEditor.putBoolean(KEY_DISC, false);
                        prefEditor.putBoolean(KEY_INST, true);
                        prefEditor.commit();

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            permissionCheck();
                        }
                    }
                });
        disclosureDialog = builder.create();

        if (preferences.getBoolean(KEY_INST, true)) {
            disclosureDialog.show();
            adButt = disclosureDialog.getButton(AlertDialog.BUTTON_POSITIVE);
            adButt.setEnabled(false);
            prefEditor.putBoolean(KEY_INST, false);
            prefEditor.commit();
        }

    }

    public void showPolicy() {
        AlertDialog.Builder builder = new AlertDialog.Builder(dialogWrapper);
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

    public void setUpToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.inflateMenu(R.menu.main_menu);

        toolbar.setOnMenuItemClickListener(new Toolbar.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                switch (item.getItemId()) {
                    case R.id.setButt:
                        Intent intent = new Intent(getApplicationContext(), Settings.class);
                        startActivity(intent);
                        return true;
                    case R.id.privacyPolicy:
                        showPolicy();
                        return true;
                }
                return false;
            }
        });
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
        if (!initialising && !recording && !forcedStop) {
//            stopListening();
            stopAll();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK) {
            if (data.getBooleanExtra(ambExtra, false)) {
                switch (requestCode) {
                    case ambStart:
                        startInitialising();
                        break;
                    case ambEnd:
                        stopAll();
                        stopLogging();
                        break;
                }
            } else if (!data.getBooleanExtra(ambExtra, false) && requestCode == ambStart) {
                stopInitialising();
            }
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
//        Ensures only one instance in normal usage. App restarts differently with Ctrl-F10 in Studio...
        prefEditor.putBoolean(KEY_INST, true).commit();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopAll();
    }

    static final String timerFilter = "TimerResponse", timerInt = "tResponse",
            loggingFilter = "LoggingResponse", loggingInt = "lResponse";
    PowerManager.WakeLock screenLock;
    Timer screenFlashTimer;
    TimerTask flashingTask;
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
                    case 9:
                        stopLogging();
                        forcedStop = false;
                        stopService(loggingService);
                        cancelBM();
                        break;
                    case 99:
                        Log.i(TAG, "Turn screen on?");
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                            setTurnScreenOn(true);
                            Log.i(TAG, "On 1");
                        } else {
                            screenLock = ((PowerManager)getSystemService(POWER_SERVICE)).newWakeLock
                                    (PowerManager.SCREEN_DIM_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "TAG");
                            if (screenFlashTimer == null) {
                                screenFlashTimer = new Timer();
                                flashingTask = new TimerTask() {
                                    @Override
                                    public void run() {
                                        if (!screenLock.isHeld()) {
                                            screenLock.acquire(500);
                                        } else {
                                            screenLock.release();
                                        }
                                        if (!recording) {
                                            screenFlashTimer.cancel();
                                        }
                                    }
                                };
                                screenFlashTimer.schedule(flashingTask, 0, 2000);
                            }

                            Log.i(TAG, "On 2");
                        }
                        break;
                }
            }
        }
    };

    public void cancelBM() {
        LocalBroadcastManager.getInstance(MainActivity.this).unregisterReceiver(BReceiver);
    }

    @Override
    public void onBackPressed() {
//        Disable Back button when used as HOME SCREEN
        if (!BuildConfig.AMB_MODE) {
            super.onBackPressed();
        }
    }
}