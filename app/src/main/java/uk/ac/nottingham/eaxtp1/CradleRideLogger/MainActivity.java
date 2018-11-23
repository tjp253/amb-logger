package uk.ac.nottingham.eaxtp1.CradleRideLogger;

import android.Manifest;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.widget.Toolbar;
import android.view.ContextThemeWrapper;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

import static uk.ac.nottingham.eaxtp1.CradleRideLogger.AmbSelect.forcedStopAmb;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.AmbSelect.selectingAmb;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.GPSService.gpsData;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.GPSService.sGPS;

public class MainActivity extends Activity implements View.OnClickListener {

    NotificationUtilities notUtils;

    static boolean gpsOff, // Only used in Test Mode - can choose to test accelerometers (and audio) only
            phoneDead; // Boolean which becomes true if the phone shut down during recording.

//    For Ambulance Mode. Intents start the ambulance-specific services. Ints and String are for onActivityResult.
    Intent ambSelect, ambGPS;    final static String ambExtra = "EndSelecting";

//    Declare intents to start services. Self-explanatory.
    Intent uploadService, audioService, gpsService, imuService, loggingService, gpsTimerService;

//    Declare variables needed for the preferences / settings
    SharedPreferences preferences;
    SharedPreferences.Editor prefEditor;
    ContextThemeWrapper dialogWrapper = new ContextThemeWrapper(this, R.style.MyAlertDialog);
    AlertDialog disclosureDialog, policyDialog;
    Button adButt;
    final static String KEY_G = "Gravity Present", KEY_FS = "MaxFS", KEY_F_CHECK = "CheckFS";
    final String user_ID = "User ID", KEY_DISC = "NotSeenDisclosure2", KEY_INST = "FirstInstance", KEY_FIRST = "firstLogin";

    static int userID;  // Declare unique user ID

//    Initialise ints for app permissions
    final int PERMISSION_GPS = 2, PERMISSION_AUDIO = 25;

//    Declare UI objects.
    Button recordButt, cancelButt;
    TextView instructDisplay, versionView;

    private ProgressBar loadingAn;  // Declare the progress circle for GPS search.

    boolean initialising, buttPressed, displayOn, buffing, fileEmpty;
    static boolean recording, moving, crashed, forcedStop;    // forcedStop set to true when AutoStop has been used.

    @SuppressLint("WifiManagerLeak")    // Android Studio stuff
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main); // Associates this activity with the stated layout

        notUtils = new NotificationUtilities(this);

//        Initialise textboxes and buttons
        instructDisplay = findViewById(R.id.instructDisplay);
        recordButt = findViewById(R.id.button_Record);
        recordButt.setOnClickListener(this);
        cancelButt = findViewById(R.id.butt_Cancel);
        cancelButt.setOnClickListener(this);
        loadingAn = findViewById(R.id.initProgress);
        loadingAn.setVisibility(View.GONE);

//        Initialise preferences and open the editor
        preferences = getSharedPreferences(getString(R.string.pref_main), MODE_PRIVATE);
        prefEditor = preferences.edit();
//        prefEditor.putInt(user_ID, 13319625).apply(); // Set ID for Dev's phone

//        Initialises a Unique ID for each user on first start-up. Probably could replace this
// with a check for User ID preference...
        if (preferences.getBoolean(KEY_FIRST, true)) {
            Random random = new Random();
            int rndUserID = 10000000 + random.nextInt(90000000);

            prefEditor.putBoolean(KEY_FIRST, false);    // Tell preferences ID has been made
            prefEditor.putInt(user_ID, rndUserID);      // Add User ID to preferences
            prefEditor.apply();                         // Save preferences
        }

//        Shows Disclosure Agreement if not seen (and accepted) before.
        if (preferences.getBoolean(KEY_DISC, true)) {
//        TODO: remove the '!' below when code is finalised.
            prefEditor.putBoolean(KEY_DISC, true);
            prefEditor.commit();
            showDisclosure();   // Starts the 'showDisclosure' method to deal with usage agreement.
        }

        userID = preferences.getInt(user_ID, 1);    // Initialises the User ID

//        Initialise User ID / app version viewer and sets text.
        versionView = findViewById(R.id.versionView);
        String version = getString(R.string.id_string) + String.valueOf(userID) + getString(R.string.version_string) ;
//        Get the versionName from the app gradle to display.
        try {
            PackageInfo packageInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            version = version + packageInfo.versionName;
        } catch (Exception e) {
            e.printStackTrace();
        }
        versionView.setText(version);
        instructDisplay.setText(R.string.startGPS);

//        Initialise process booleans.
        recording = false;
        initialising = false;
        crashed = false;

//        Initialise the Service Intents
        audioService = new Intent(getApplicationContext(), AudioService.class);
        gpsService = new Intent(getApplicationContext(), GPSService.class);
        imuService = new Intent(getApplicationContext(), IMUService.class);
        loggingService = new Intent(getApplicationContext(), LoggingService.class);
        gpsTimerService = new Intent(getApplicationContext(), GPSTimerService.class);
        uploadService = new Intent(this, UploadService.class);

        new JobUtilities(this).checkFiles();

        setUpToolbar(); // Sets up the toolbar - method lower down.

        if (BuildConfig.AMB_MODE) { // If AMB Build, initialise AMB service intents.
            ambSelect = new Intent(this, AmbSelect.class);
            ambSelect.putExtra(getString(R.string.extra_dead),false);
            ambGPS = new Intent(this,AmbGPSService.class);
        } else if (BuildConfig.TEST_MODE) { // If Test Build check whether GPS is on or off.
            gpsOff = !PreferenceManager.getDefaultSharedPreferences(this)
                    .getBoolean(getString(R.string.key_pref_test),false);
        }

//        If the Sample Frequency and Gyro have not been assessed, start the FSChecker service
        if (preferences.getBoolean(KEY_F_CHECK, true)) {
            startService(new Intent(getApplicationContext(), FSChecker.class));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

//        Cancels 'GPS Failed [to lock]' notification when User resumes app
        notUtils.getManager().cancel(getResources().getInteger(R.integer.failedID));
        displayOn = true;   // Tells the app this Activity is being used

        if (crashed) {
            onCrash();      // Fail-safe. Stop any recordings if app crashes.
        }

        if (forcedStop) {   // If the recording is stopped due to inactivity
            if (!BuildConfig.AMB_MODE) {    // If NOT in AMB Mode.
                displayOnFinish();      // Notify user that logging has stopped.
            } else if (forcedStopAmb) {     // If the user has inputted AMB info after app stops.
                displayOnFinish();      // Notify user that logging has stopped.
                forcedStop = false;
                forcedStopAmb = false;
                stopService(loggingService);
            } else {    // Listen to logging service responses
                registerReceiver(loggingReceiver, new IntentFilter(loggingFilter));
            }
        }

        if (BuildConfig.AMB_MODE) {

            // Initialise the phoneDead boolean from the AMB SharedPrefs. If the phone was shut
            // down during recording, this will return as true and the user will be asked for the
            // AMB options as they normally would at the end of recordings. After selecting, the
            // file is written and sent to upload.
            phoneDead = getSharedPreferences(getString(R.string.pref_amb),MODE_PRIVATE)
                    .getBoolean(getString(R.string.key_dead),false);
            if (phoneDead) {
                ambSelect.putExtra(getString(R.string.extra_dead),true);
                startActivityForResult(ambSelect, getResources().getInteger(R.integer.ambDead));
            } else {
                ambSelect.putExtra(getString(R.string.extra_dead),false);
            }

            if (selectingAmb && !recording) {
                // Stop GPS initialising if SOMEHOW get back to MainActivity with 'selectingAmb'
                // true. Crash?
                stopService(ambGPS);
            }
        }

    }

    @Override
    protected void onPause() {
        super.onPause();
        displayOn = false;   // Tells the app this Activity is not being used

        if (crashed) {
            onCrash();      // Fail-safe. Stop any recordings if app crashes.
        }

    }

    @SuppressWarnings("MissingPermission")
//    Start initialising sensors. GPS takes a while to lock sometimes. Audio takes a while to
// start outputting MaxAmplitudes
    public void startInitialising() {
        initialising = true;     recording = false;
        recordButt.setEnabled(false);   // User cannot start / stop recording, as it's not started
        // Inform user app is searching for GPS
        recordButt.setText(R.string.butt_init);
        instructDisplay.setText(R.string.initialising);
//        Display the searching (loading) animation and a 'cancel recording' button
        loadingAn.setVisibility(View.VISIBLE);      cancelButt.setVisibility(View.VISIBLE);
        gpsData = "";   // Initialise the GPS data
        startService(gpsTimerService);  // Start service to search for GPS and buffer the start
//        Listen to GPSTimerService to find out what to do next
        registerReceiver(timerReceiver, new IntentFilter(timerFilter));

        startService(audioService); // Start the Audio Service so MaxAmplitude is available
    }

//    Stops the app initialising - if user decides to cancel
    public void stopInitialising() {
        loadingAn.setVisibility(View.GONE);     cancelButt.setVisibility(View.INVISIBLE);
        stopAll();  // Stop all services (doesn't matter if they're not all running)
        if (BuildConfig.AMB_MODE) {
            stopService(ambGPS);    // Stop the AMB-specific GPS initialising service
        }
    }

    // Fail-safe. Stop any recordings if app crashes.
    public void onCrash() {
        stopAll();  // Stop all services (doesn't matter if they're not all running)
        initialising = false;
        recordButt.setEnabled(false);
        instructDisplay.setText(R.string.crashed);
    }

//    Start the recording proper
    public void startAll() {
        recording = true;
        initialising = false;
        forcedStop = false;
        sGPS = "";  // Initialise the GPS sample number string - save space in files before locked.
        if (!gpsOff) {  // Start GPS service unless GPS turned off
            startService(gpsService);
            registerReceiver(gpsReceiver, new IntentFilter(gpsFilter));
        }
        startService(loggingService);   // Start the logging service
        if (!BuildConfig.AMB_MODE) {
            startService(imuService);   // Unless AMB, start IMU - AMB starts it earlier
        } else {
            registerReceiver(shutdownReceiver,new IntentFilter(Intent.ACTION_SHUTDOWN));
        }
//        Register for feedback from Logging Service
        registerReceiver(loggingReceiver, new IntentFilter(loggingFilter));
        // Unregister feedback from GPSTimerService
        unregisterReceiver(timerReceiver);
    }

//    Separate the display / UI changes - to enable buffer to work nicely.
    public void changeDisplay() {
        loadingAn.setVisibility(View.GONE);             // Hide the animation
        cancelButt.setVisibility(View.INVISIBLE);       // Hide the cancel button
        instructDisplay.setText(R.string.recording);    // Change display info
        recordButt.setText(R.string.butt_stop);         // Change recording button to 'Stop'
        recordButt.setEnabled(true);                    // Enable 'Stop' button
    }

//    Stop all services to finish recording / initialising
    public void stopAll() {
        stopService(gpsTimerService);
        stopService(imuService);
        stopService(audioService);
        stopService(gpsService);
        stopService(loggingService);

        recording = false;

        recordButt.setText(R.string.butt_start);
        recordButt.setEnabled(true);

        initialising = false;
    }

//    Change the info displayed depending on what was recorded (how much)
    public void displayOnFinish() {
        if (fileEmpty) {
            instructDisplay.setText(R.string.file_empty);
        } else {
            instructDisplay.setText(R.string.finished);
        }

        recordButt.setText(R.string.butt_start);
        recordButt.setEnabled(true);
    }

    @Override
    public void onClick(View v) {   // Handle all main screen button presses

        if (v == recordButt) {  // If user presses the main Start/Stop button

            if (!recording && !forcedStop) { // Handle whether to start recording

                //        Re-checks (and asks for) the GPS and audio permissions required
                if ( Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                        !(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                                checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                                checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) ) {

                    buttPressed = true; // Tells permission handler to record if permission given

                    permissionCheck();  // Checks / asks for GPS & audio permissions

                } else if (buffing) {   // Cancel recording if the time hasn't gone past Start Buffer
                    stopInitialising();
                    instructDisplay.setText(R.string.file_empty);   // Tell user recording was pointless
                    buffing = false;
                } else if (BuildConfig.AMB_MODE) {
                    startActivityForResult(ambSelect, getResources().getInteger(R.integer.ambStart)); // Start the ambulance / trolley selection
                    startService(imuService);   // Start IMU logging straight away - for entrance matting, etc.
                } else {
                    startInitialising();    // Recording is go!
                }

            } else { // Stop recording data

                if (BuildConfig.AMB_MODE) {
                    startActivityForResult(ambSelect, getResources().getInteger(R.integer.ambEnd));  // Get user selecting transport reasons
                } else {
                    stopAll();  // Stop all services (doesn't matter if they're not all running)

                    displayOnFinish();      // Notify user that logging has stopped.

                    if (BuildConfig.TEST_MODE) {
                        unregisterReceiver(loggingReceiver);
                    }
                }
            }

        } else if (v == cancelButt && !recording) { // Stop if user cancels while initialising
            stopInitialising();
            instructDisplay.setText(R.string.startGPS);
        }

    }

//    For Marshmallow and above (APIs 23+) check whether the user has granted permission to use
// the GPS and audio, and asks for permission if not.
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

//    Handles the result of the permission request
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (grantResults.length != 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (requestCode == PERMISSION_GPS) {

                permissionCheck();  // Now ask for audio

            } else if (buttPressed) {   // Start the recording process
                if (BuildConfig.AMB_MODE) {
                    startActivityForResult(ambSelect, getResources().getInteger(R.integer.ambStart));
                } else {
                    startInitialising();
                }
            }
        } else {    // Inform user permissions are required to record data
            instructDisplay.setText(R.string.permissions);
        }
    }

//    Show the disclosure agreement on start-up, and disable app until user agrees to it.
    public void showDisclosure() {
//        Initialise the 'consent' checkbox
        View checkboxView = View.inflate(this, R.layout.disclosure, null);
        CheckBox checkBox = checkboxView.findViewById(R.id.checkbox);
        checkBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
//                Enable the 'accept' button only when checkbox is checked
                if (isChecked) {
                    adButt.setEnabled(true);
                } else {
                    adButt.setEnabled(false);
                }
            }
        });

//        Set up the disclosure window
        AlertDialog.Builder builder = new AlertDialog.Builder(dialogWrapper);
        builder .setTitle(R.string.ad_title)
                .setView(checkboxView)  // Layout used by the window
                .setCancelable(false)   // User cannot back out of it. Only way is to accept.
                .setPositiveButton(R.string.ad_button, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int BUTTON_POSITIVE) {
                        prefEditor.putBoolean(KEY_DISC, false); // Accept the disclosure agreement!
                        prefEditor.putBoolean(KEY_INST, true);  // Ensure only one instance.
                        prefEditor.commit();

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            permissionCheck();  // Asks for GPS & audio permissions
                        }
                    }
                });
        disclosureDialog = builder.create();    // Create the disclosure window

//        If there are no other instances of the disclosure window, show the window and
// initialise the 'accept' button.
        if (preferences.getBoolean(KEY_INST, true)) {
            disclosureDialog.show();
            adButt = disclosureDialog.getButton(AlertDialog.BUTTON_POSITIVE);
            adButt.setEnabled(false);
            prefEditor.putBoolean(KEY_INST, false);
            prefEditor.commit();
        }

    }

//    Show the privacy policy for the app
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

//    Initialises the toolbar and handles any toolbar interactions
    public void setUpToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.inflateMenu(R.menu.main_menu);
        toolbar.setOnMenuItemClickListener(new Toolbar.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                switch (item.getItemId()) {
                    case R.id.setButt:          // Go to the settings screen
                        startActivity(new Intent(getApplicationContext(), Settings.class));
                        return true;
                    case R.id.privacyPolicy:    // SHow privacy policy
                        showPolicy();
                        return true;
                }
                return false;
            }
        });
    }

//    Stops all services if left on incorrectly (by Android Studio, usually)
    @Override
    protected void onStart() {
        super.onStart();
        if (!initialising && !recording && !forcedStop) {
            stopAll();  // Stop all services (doesn't matter if they're not all running)
        }
    }

//    Handle the outcome of the AmbSelect screen
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK) {
            if (data.getBooleanExtra(ambExtra, false)) {
                if (requestCode == getResources().getInteger(R.integer.ambStart)) {
                    startInitialising();

                } else if (requestCode == getResources().getInteger(R.integer.ambEnd)) {
                    stopAll();  // Stop all services (doesn't matter if they're not all running)
                    displayOnFinish();      // Notify user that logging has stopped.

                } else if (requestCode == getResources().getInteger(R.integer.ambDead)) {
                    getSharedPreferences(getString(R.string.pref_amb), MODE_PRIVATE).edit()
                            .putBoolean(getString(R.string.key_dead),false).apply();
                    stopAll(); // In case services are still running.
                    startService(new Intent(getApplicationContext(), AmbWriteAfterReboot.class));

                }
            } else if (!data.getBooleanExtra(ambExtra, false) && requestCode == getResources().getInteger(R.integer.ambStart)) {
                stopInitialising(); // User has cancelled the recording
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
        stopAll();  // Stop all services (doesn't matter if they're not all running)
    }

//    Declare variables for use with the Broadcast Receivers. The Broadcast Receivers enable
// communication with other classes.
    static final String timerFilter = "TimerResponse", timerInt = "tResponse",
            loggingFilter = "LoggingResponse", loggingInt = "lResponse",
            gpsFilter = "GPSResponse", gpsBool = "StationaryResponse";
    PowerManager.WakeLock screenLock;
    Timer screenFlashTimer;
    TimerTask flashingTask;
    private BroadcastReceiver timerReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getIntExtra(timerInt, 1)) {  // GPS Timer communication
                case 1: // Start recording data
                    buffing = false;
                    startAll();
                    changeDisplay();
                    break;

                case 0: // Cancels recording if the GPS can't get a fix within a reasonable time.
                    stopInitialising();
                    instructDisplay.setText(R.string.failed);
                    if (!displayOn) {   // Send notification if user is not in MainActivity
                        Notification.Builder notBuild = notUtils.getFailedNotification();
                        notUtils.getManager().notify(getResources().getInteger(R.integer.failedID),notBuild.build());
                    }
                    unregisterReceiver(timerReceiver);
                    break;

                case 2: // Let User know GPS is fixed, but waiting for buffer before recording.
                    buffing = true;
                    changeDisplay();
                    break;

                case 3: // Cancel the Broadcast Receiver if GPSTimerService destroyed before lock
                    unregisterReceiver(timerReceiver);
                    break;
            }
        }
    };

    private BroadcastReceiver loggingReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getIntExtra(loggingInt,1)) { // Logging Service communication
                case 0: // Recording stopped before any data recorded (time < end buffer time)
                    fileEmpty = true;
                    break;

                case 1: // Recording has some data
                    fileEmpty = false;
                    if (BuildConfig.CROWD_MODE) {
                        unregisterReceiver(loggingReceiver); // Cancel Broadcast Receiver for regular users
                    }
                    return;

                case 9: // Notify AMB user that logging has stopped (as AmbSelect screen will be up).
                    displayOnFinish();
                    forcedStop = false;
                    stopService(loggingService);
                    unregisterReceiver(shutdownReceiver);
                    break;

                case 99: // Flash the screen if there's an error reading from the logging queue
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {   // For APIs 27+
                        setTurnScreenOn(true);  // Can't check as don't have latest API
                    } else {
                        PowerManager myPowerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
                        if (myPowerManager != null) {
                            // 'SCREEN_DIM_WAKE_LOCK' is deprecated, but works. The comment
                            // below disables the Android Studio warning.
                            //noinspection deprecation
                            screenLock = myPowerManager.newWakeLock
                                    (PowerManager.SCREEN_DIM_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP,
                                            "MainActivity:FlashingWakelock");
                        }
                        if (screenFlashTimer == null) {
                            screenFlashTimer = new Timer();
                            flashingTask = new TimerTask() {
                                @Override
                                public void run() {
                                    if (screenLock != null) {
                                        if (!screenLock.isHeld()) {
                                            screenLock.acquire(500);
                                        } else {
                                            screenLock.release();
                                        }
                                    }
                                    if (!recording) {
                                        screenFlashTimer.cancel();
                                    }
                                }
                            };
                            screenFlashTimer.schedule(flashingTask, 0, 2000);
                        }
                    }
                    return;
            }
            unregisterReceiver(loggingReceiver);
        }
    };

    // Find out whether the recording was stopped due to lack of movement or not. If it was,
    // refresh the screen in case the screen was still on.
    private BroadcastReceiver gpsReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getBooleanExtra(gpsBool,false)) {
                displayOnFinish();  // Notify user that logging has stopped.
                if (!BuildConfig.AMB_MODE) {
                    forcedStop = false;
                }
                try {
                    unregisterReceiver(loggingReceiver);
                } catch (Exception e) {
                    // Receiver is already unregistered... move along.
                }
            }
            unregisterReceiver(gpsReceiver);
        }
    };

    private BroadcastReceiver shutdownReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            phoneDead = true;
            SharedPreferences ambPref = getSharedPreferences(getString(R.string.pref_amb), MODE_PRIVATE);
            ambPref.edit().putBoolean(getString(R.string.key_dead),true).apply();
            unregisterReceiver(shutdownReceiver);
        }
    };

// Disable Back button when used as the HOME SCREEN (AMB Mode)
    @Override
    public void onBackPressed() {
        if (!BuildConfig.AMB_MODE) {
            super.onBackPressed();
        }
    }
}