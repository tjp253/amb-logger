package uk.ac.nottingham.AmbLogger;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.appcompat.widget.Toolbar;
import androidx.preference.PreferenceManager;

import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import static uk.ac.nottingham.AmbLogger.AmbSpecific.MetaSelectionActivity.forcedStopAmb;
import static uk.ac.nottingham.AmbLogger.AmbSpecific.MetaSelectionActivity.selectingAmb;
import static uk.ac.nottingham.AmbLogger.Recording.GPSService.gpsData;
import static uk.ac.nottingham.AmbLogger.Recording.GPSService.sGPS;

import uk.ac.nottingham.AmbLogger.AmbSpecific.InitialiseGPS;
import uk.ac.nottingham.AmbLogger.AmbSpecific.MetaSelectionActivity;
import uk.ac.nottingham.AmbLogger.AmbSpecific.LogMetaAfterReboot;
import uk.ac.nottingham.AmbLogger.FileHandling.UploadService;
import uk.ac.nottingham.AmbLogger.Recording.AudioService;
import uk.ac.nottingham.AmbLogger.Recording.GPSService;
import uk.ac.nottingham.AmbLogger.Recording.GPSTimerService;
import uk.ac.nottingham.AmbLogger.Recording.IMUService;
import uk.ac.nottingham.AmbLogger.Recording.LoggingService;
import uk.ac.nottingham.AmbLogger.Settings.Settings;
import uk.ac.nottingham.AmbLogger.Utilities.DialogHandler;
import uk.ac.nottingham.AmbLogger.Utilities.FSChecker;
import uk.ac.nottingham.AmbLogger.Utilities.JobUtilities;
import uk.ac.nottingham.AmbLogger.Utilities.NotificationUtilities;
import uk.ac.nottingham.AmbLogger.Utilities.PermissionHandler;

public class MainActivity extends Activity implements View.OnClickListener {

    NotificationUtilities notUtils;
    PermissionHandler perms;
    DialogHandler dialogHandler;

    public static boolean gpsOff, // Only used in Test Mode - can choose to test accelerometers (and audio) only
            phoneDead; // Boolean which becomes true if the phone shut down during recording.

    public static String versionNum = ""; // Used to aid both debugging and future processing

//    For Ambulance Mode. Intents start the ambulance-specific services. Ints and String are for onActivityResult.
    Intent ambSelect, ambGPS;    public final static String ambExtra = "EndSelecting";

//    Declare intents to start services. Self-explanatory.
    Intent uploadService, audioService, gpsService, imuService, loggingService, gpsTimerService;

//    Declare variables needed for the preferences / settings
    SharedPreferences preferences;
    SharedPreferences.Editor prefEditor;
    AlertDialog disclosureDialog, launcherDialog;
    Button adButt;
    public static String KEY_G, KEY_FS;
    String KEY_ID, KEY_DISCLOSURE, KEY_INSTANCE;

    public static String userID;  // Declare unique user ID

//    Declare UI objects.
    Button recordButt, cancelButt;
    TextView instructDisplay, versionView;

    private ProgressBar loadingAn;  // Declare the progress circle for GPS search.

    boolean buttPressed, displayOn, fileEmpty;
    public static boolean initialising, recording, moving;
    public static boolean forcedStop, crashed;    // forcedStop set to true when AutoStop has been used.

    @SuppressLint("WifiManagerLeak")    // Android Studio stuff
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main); // Associates this activity with the stated layout

        notUtils = new NotificationUtilities(this);
        perms = new PermissionHandler(this);
        dialogHandler = new DialogHandler(this);

//        Initialise textboxes and buttons
        instructDisplay = findViewById(R.id.instructDisplay);
        recordButt = findViewById(R.id.button_Record);
        recordButt.setOnClickListener(this);
        cancelButt = findViewById(R.id.butt_Cancel);
        cancelButt.setOnClickListener(this);
        loadingAn = findViewById(R.id.initProgress);
        loadingAn.setVisibility(View.GONE);
        
        KEY_G = getString(R.string.key_gravity);
        KEY_FS = getString(R.string.key_fs);
        KEY_ID = getString(R.string.key_user_id);
        KEY_DISCLOSURE = getString(R.string.key_disclosure);
        KEY_INSTANCE = getString(R.string.key_instance);

//        Initialise preferences and open the editor
        // first, set the default preferences, if never modified by the user
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        prefEditor = preferences.edit();
//        prefEditor.putInt(KEY_ID, 13319625).apply(); // Set ID for Dev's phone

//        Initialises a Unique ID for each user on first start-up. Probably could replace this
// with a check for User ID preference...
        try { // converting to String storage... need the try-catch for the first run after update
            if (preferences.getString(KEY_ID, "-1").equals("-1") && !inOldPreferences()) {
                Random random = new Random();
                int rndUserID = random.nextInt(99999999);

                // Add User ID to preferences
                prefEditor.putString(
                        KEY_ID, String.format(Locale.ENGLISH, "%08d", rndUserID)
                ).apply();
            }
        } catch (ClassCastException e) {  // thrown because 'KEY_ID' is an INT (OLD STORAGE)
            int userID = preferences.getInt(KEY_ID, 0);
            prefEditor.remove(KEY_ID);  // remove the integer value
            // store the UserID as a string instead
            prefEditor.putString(
                    KEY_ID, String.format(Locale.ENGLISH, "%08d", userID)
            ).apply();
        }

//        Shows Disclosure Agreement if not seen (and accepted) before.
        if (preferences.getBoolean(KEY_DISCLOSURE, true)) {
//        TODO: remove the '!' below when code is finalised.
            prefEditor.putBoolean(KEY_DISCLOSURE, true).apply();
            showDisclosure();   // Starts the 'showDisclosure' method to deal with usage agreement.
        } else {
            setAsLauncher(); // Check if app is launcher, and ask to set if it isn't.
        }

        userID = preferences.getString(KEY_ID, "");    // Initialises the User ID

//        Initialise User ID / app version viewer and sets text.
        versionView = findViewById(R.id.versionView);
        String version = getString(R.string.id_string) + userID + getString(R.string.version_string);
//        Get the versionName from the app gradle to display.
        try {
            PackageInfo packageInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            versionNum = packageInfo.versionName;
            version = version + versionNum;
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

        if (BuildConfig.TEST_MODE) { // If Test Build check whether GPS is on or off.
            gpsOff = !PreferenceManager.getDefaultSharedPreferences(this)
                    .getBoolean(getString(R.string.key_pref_test),false);
        } else { // initialise AMB service intents.
            ambSelect = new Intent(this, MetaSelectionActivity.class);
            ambSelect.putExtra(getString(R.string.extra_dead),false);
            ambGPS = new Intent(this, InitialiseGPS.class);
        }

//        If the Sample Frequency and Gyro have not been assessed, start the FSChecker service
        if (preferences.getInt(KEY_FS, -1) == -1) {
            startService(new Intent(getApplicationContext(), FSChecker.class));
        }
    }

    @SuppressWarnings("ConstantConditions")
    public boolean inOldPreferences() {
        // Copy all old preferences to the default SharedPreferences
        SharedPreferences prefOld = getSharedPreferences(getString(R.string.pref_main), MODE_PRIVATE);
        Map<String, ?> allOldPrefs = prefOld.getAll(); // grab all old preferences

        Set<String> oldKeys = allOldPrefs.keySet(), currentKeys = preferences.getAll().keySet();

        // if OldPrefs is empty, UserID has not been set
        if (oldKeys.isEmpty()) {
            return false;
        }

        SharedPreferences.Editor prefOldEditor = prefOld.edit();
        if (currentKeys.contains(KEY_ID)) {
            prefOldEditor.clear().apply();
            return true;
        }

        String nttPref = getString(R.string.key_pref_ntt);

        for (String key : allOldPrefs.keySet()) {
            if (key.equals(KEY_ID)) {
                prefEditor.putString(
                        KEY_ID, String.format(Locale.ENGLISH, "%08d", (Integer) allOldPrefs.get(key))
                ).apply();
            } else if (key.equals(nttPref)) {
                prefEditor.putString(key, getString(R.string.ntt_centre));
            } else if (allOldPrefs.get(key) instanceof  Boolean) {
                prefEditor.putBoolean(key, (Boolean) allOldPrefs.get(key));
            } else if (allOldPrefs.get(key) instanceof  Integer) {
                prefEditor.putInt(key, (Integer) allOldPrefs.get(key));
            } else if (allOldPrefs.get(key) instanceof  Long) {
                prefEditor.putLong(key, (Long) allOldPrefs.get(key));
            }
        }
        prefEditor.apply();
        prefOldEditor.clear().apply();

        return preferences.contains(KEY_ID);
    }

    @Override
    protected void onResume() {
        super.onResume();

//        Cancels 'GPS Failed [to lock]' notification when User resumes app
        notUtils.getManager().cancel(notUtils.FAILED_INT);
        displayOn = true;   // Tells the app this Activity is being used

        if (crashed) {
            onCrash();      // Fail-safe. Stop any recordings if app crashes.
        }

        if (forcedStop) {   // If the recording is stopped due to inactivity
            if (BuildConfig.TEST_MODE) {    // If NOT in AMB Mode.
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

        if (!BuildConfig.TEST_MODE) {

            // Initialise the phoneDead boolean from the AMB SharedPrefs. If the phone was shut
            // down during recording, this will return as true and the user will be asked for the
            // AMB options as they normally would at the end of recordings. After selecting, the
            // file is written and sent to upload.
            phoneDead = preferences.getBoolean(getString(R.string.key_dead),false);
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
        gpsData.clear();   // Initialise the GPS data
        startService(gpsTimerService);  // Start service to search for GPS
//        Listen to GPSTimerService to find out what to do next
        registerReceiver(timerReceiver, new IntentFilter(timerFilter));

        startService(audioService); // Start the Audio Service so MaxAmplitude is available
    }

//    Stops the app initialising - if user decides to cancel
    public void stopInitialising() {
        loadingAn.setVisibility(View.GONE);     cancelButt.setVisibility(View.INVISIBLE);
        stopAll();  // Stop all services (doesn't matter if they're not all running)
        if (!BuildConfig.TEST_MODE) {
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
        if (BuildConfig.TEST_MODE) {
            startService(imuService);   // Unless AMB, start IMU - AMB starts it earlier
        } else {
            registerReceiver(shutdownReceiver,new IntentFilter(Intent.ACTION_SHUTDOWN));
        }
//        Register for feedback from Logging Service
        registerReceiver(loggingReceiver, new IntentFilter(loggingFilter));
        // Unregister feedback from GPSTimerService
        unregisterReceiverAllInstances(timerReceiver);
    }

//    Separate the display / UI changes.
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

                // Re-checks (and asks for) the GPS and audio permissions required
                if (perms.needsPerms()) {

                    buttPressed = true; // Tells permission handler to record if permission given

                    permissionCheck();  // Checks / asks for GPS & audio permissions

                } else if (BuildConfig.TEST_MODE) {
                    startInitialising();    // Recording is go!
                } else {
                    startActivityForResult(ambSelect, getResources().getInteger(R.integer.ambStart)); // Start the ambulance / trolley selection
                    initialising = true;  // to prevent IMU thread being wrongly terminated
                    startService(imuService);   // Start IMU logging straight away - for entrance matting, etc.
                }

            } else { // Stop recording data

                if (BuildConfig.TEST_MODE) {
                    stopAll();  // Stop all services (doesn't matter if they're not all running)

                    displayOnFinish();      // Notify user that logging has stopped.

                    unregisterReceiverAllInstances(loggingReceiver);
                } else {
                    startActivityForResult(ambSelect, getResources().getInteger(R.integer.ambEnd));  // Get user selecting transport reasons
                }
            }

        } else if (v == cancelButt && !recording) { // Stop if user cancels while initialising
            stopInitialising();
            instructDisplay.setText(R.string.startGPS);
        }

    }

// For Marshmallow and above (APIs 23+) check whether the user has granted permission to use the
// GPS and audio, and asks for permission if not.
    public void permissionCheck() {
        if (perms.needsLocationPerms()) {
            ActivityCompat.requestPermissions(this,perms.locPerms, perms.PERMISSION_GPS);
        } else if (perms.needsAudioPerm()) {
            ActivityCompat.requestPermissions(this,perms.audPerm, perms.PERMISSION_AUDIO);
        }
    }

//    Handles the result of the permission request
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (grantResults.length != 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (requestCode == perms.PERMISSION_GPS) {

                if (perms.needsAudioPerm())
                    ActivityCompat.requestPermissions(this, perms.audPerm, perms.PERMISSION_AUDIO);

            } else if (buttPressed) {   // Start the recording process
                if (BuildConfig.TEST_MODE) {
                    startInitialising();
                } else {
                    startActivityForResult(ambSelect, getResources().getInteger(R.integer.ambStart));
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
        checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
//                Enable the 'accept' button only when checkbox is checked
            adButt.setEnabled(isChecked);
        });

        // Set up the disclosure window
        AlertDialog.Builder builder = dialogHandler.buildDisclosureDialog()
                .setView(checkboxView)  // Layout used by the window
                .setPositiveButton(R.string.ad_button, (dialog, BUTTON_POSITIVE) -> {
                    prefEditor.putBoolean(KEY_DISCLOSURE, false); // Accept the disclosure agreement!
                    prefEditor.putBoolean(KEY_INSTANCE, true);  // Ensure only one instance.
                    prefEditor.commit();

                    permissionCheck();  // Asks for GPS & audio permissions
                });
        disclosureDialog = builder.create();    // Create the disclosure window

//        If there are no other instances of the disclosure window, show the window and
// initialise the 'accept' button.
        if (preferences.getBoolean(KEY_INSTANCE, true)) {
            disclosureDialog.show();
            adButt = disclosureDialog.getButton(AlertDialog.BUTTON_POSITIVE);
            adButt.setEnabled(false);
            prefEditor.putBoolean(KEY_INSTANCE, false);
            prefEditor.commit();
        }

    }

//    Initialises the toolbar and handles any toolbar interactions
    public void setUpToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.inflateMenu(R.menu.main_menu);
        toolbar.setOnMenuItemClickListener(item -> {
            int itemID = item.getItemId();
            if (itemID == R.id.setButt) {
                // Go to the settings screen
                startActivity(new Intent(getApplicationContext(), Settings.class));
                return true;
            } else if (itemID == R.id.privacyPolicy) {
                // Show privacy policy
                dialogHandler.getPolicyDialog().show();
                return true;
            } else if (itemID == R.id.setAsLauncher) {
                // Shortcut to Launcher selector (only in AMB Build)
                startActivityForResult(new Intent(android.provider.Settings
                        .ACTION_HOME_SETTINGS), 0);
                return true;
            }
            return false; // Else...
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
        if (resultCode == RESULT_OK && requestCode != 0) {
            if (data.getBooleanExtra(ambExtra, false)) {
                if (requestCode == getResources().getInteger(R.integer.ambStart)) {
                    startInitialising();

                } else if (requestCode == getResources().getInteger(R.integer.ambEnd)) {
                    stopAll();  // Stop all services (doesn't matter if they're not all running)
                    unregisterReceiverAllInstances(loggingReceiver);
                    unregisterReceiverAllInstances(shutdownReceiver);
                    displayOnFinish();      // Notify user that logging has stopped.

                } else if (requestCode == getResources().getInteger(R.integer.ambDead)) {
                    // Amb options inputted after reboot. Recording can be finished.
                    getSharedPreferences(getString(R.string.pref_amb), MODE_PRIVATE).edit()
                            .putBoolean(getString(R.string.key_dead),false).apply();
                    stopAll(); // In case services are still running.
                    startService(new Intent(getApplicationContext(), LogMetaAfterReboot.class));

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
        prefEditor.putBoolean(KEY_INSTANCE, true).commit();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopAll();  // Stop all services (doesn't matter if they're not all running)
    }

//    Declare variables for use with the Broadcast Receivers. The Broadcast Receivers enable
// communication with other classes.
    public static final int TIMER_BROADCAST_START = 1, TIMER_BROADCAST_TIMEOUT = 0,
        TIMER_BROADCAST_KILLED = 3;

    public static final String timerFilter = "TimerResponse", timerInt = "tResponse",
            loggingFilter = "LoggingResponse", loggingInt = "lResponse",
            gpsFilter = "GPSResponse", gpsBool = "StationaryResponse";
    PowerManager.WakeLock screenLock;
    Timer screenFlashTimer;
    TimerTask flashingTask;
    private final BroadcastReceiver timerReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getIntExtra(timerInt, TIMER_BROADCAST_START)) {  // GPS Timer communication
                case TIMER_BROADCAST_START: // Start recording data
                    startAll();
                    changeDisplay();
                    break;

                case TIMER_BROADCAST_TIMEOUT: // Cancels recording if the GPS can't get a fix within a reasonable time.
                    stopInitialising();
                    instructDisplay.setText(R.string.failed);
                    if (!displayOn) {   // Send notification if user is not in MainActivity
                        notUtils.getManager().notify(notUtils.FAILED_INT,
                                notUtils.getFailedNotification().build());
                    }
                    unregisterReceiverAllInstances(timerReceiver);
                    break;

                case TIMER_BROADCAST_KILLED: // Cancel the Broadcast Receiver if GPSTimerService destroyed before lock
                    unregisterReceiverAllInstances(timerReceiver);
                    break;
            }
        }
    };

    public static final int LOGGING_BROADCAST_CANCEL = 0, LOGGING_BROADCAST_RECORDING = 1,
        LOGGING_BROADCAST_AMB = 25, LOGGING_BROADCAST_PROBLEM = 99;

    private final BroadcastReceiver loggingReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getIntExtra(loggingInt,LOGGING_BROADCAST_RECORDING)) { // Logging Service communication
                case LOGGING_BROADCAST_CANCEL: // Recording stopped before any data recorded
                    fileEmpty = true;
                    break;

                case LOGGING_BROADCAST_RECORDING: // Recording has some data
                    fileEmpty = false;
                    return;

                case LOGGING_BROADCAST_AMB: // Notify AMB user that logging has stopped (as AmbSelect screen will be up).
                    displayOnFinish();
                    forcedStop = false;
                    stopService(loggingService);
                    unregisterReceiverAllInstances(loggingReceiver);
                    unregisterReceiverAllInstances(shutdownReceiver);
                    break;

                case LOGGING_BROADCAST_PROBLEM: // Flash the screen if there's an error reading from the logging queue
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
            unregisterReceiverAllInstances(loggingReceiver);
        }
    };

    // Find out whether the recording was stopped due to lack of movement or not. If it was,
    // refresh the screen in case the screen was still on.
    private final BroadcastReceiver gpsReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getBooleanExtra(gpsBool,false)) {
                displayOnFinish();  // Notify user that logging has stopped.
                if (BuildConfig.TEST_MODE) {
                    forcedStop = false;
                }
                unregisterReceiverAllInstances(loggingReceiver);
            }
            unregisterReceiverAllInstances(gpsReceiver);
        }
    };

    private final BroadcastReceiver shutdownReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (recording) {
                phoneDead = true;
                prefEditor.putBoolean(getString(R.string.key_dead), true).apply();
            }
            unregisterReceiverAllInstances(shutdownReceiver);
        }
    };

// Disable Back button when used as the HOME SCREEN (AMB Mode)
    @Override
    public void onBackPressed() {
        if (BuildConfig.TEST_MODE) {
            super.onBackPressed();
        }
    }

    public void setAsLauncher() {
        if (!BuildConfig.TEST_MODE && !BuildConfig.DEBUG && dialogHandler.appNotLauncher()) {

            launcherDialog = dialogHandler.buildLauncherPrompt()
                    .setPositiveButton(R.string.butt_ok, (dialog, buttInt) -> {
                        // Take user to the relevant settings.
                        startActivityForResult(new Intent(android.provider.Settings
                                .ACTION_HOME_SETTINGS), 0);
                    }).create();
            launcherDialog.show();
        }
    }

// Simple looping method to remove all instances of a receiver, in case it was not cleared
    public void unregisterReceiverAllInstances(BroadcastReceiver receiver) {
        while (true) {
            try {
                unregisterReceiver(receiver);
            } catch (Exception e) {
                break;
            }
        }
    }
}