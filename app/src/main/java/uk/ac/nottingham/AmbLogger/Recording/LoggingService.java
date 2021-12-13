package uk.ac.nottingham.AmbLogger.Recording;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.IBinder;
import android.os.PowerManager;
import android.preference.PreferenceManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.NoSuchElementException;
import java.util.Timer;
import java.util.TimerTask;
import java.util.zip.Deflater;
import java.util.zip.GZIPOutputStream;

import static uk.ac.nottingham.AmbLogger.MainActivity.LOGGING_BROADCAST_CANCEL;
import static uk.ac.nottingham.AmbLogger.MainActivity.LOGGING_BROADCAST_PROBLEM;
import static uk.ac.nottingham.AmbLogger.MainActivity.LOGGING_BROADCAST_RECORDING;
import static uk.ac.nottingham.AmbLogger.Recording.GPSService.gpsData;
import static uk.ac.nottingham.AmbLogger.Recording.IMUService.date;
import static uk.ac.nottingham.AmbLogger.Recording.IMUService.myQ;
import static uk.ac.nottingham.AmbLogger.MainActivity.crashed;
import static uk.ac.nottingham.AmbLogger.MainActivity.KEY_G;
import static uk.ac.nottingham.AmbLogger.MainActivity.gpsOff;
import static uk.ac.nottingham.AmbLogger.MainActivity.loggingFilter;
import static uk.ac.nottingham.AmbLogger.MainActivity.loggingInt;
import static uk.ac.nottingham.AmbLogger.MainActivity.phoneDead;
import static uk.ac.nottingham.AmbLogger.MainActivity.recording;
import static uk.ac.nottingham.AmbLogger.MainActivity.userID;

import uk.ac.nottingham.AmbLogger.AmbSpecific.MetaLoggingService;
import uk.ac.nottingham.AmbLogger.BuildConfig;
import uk.ac.nottingham.AmbLogger.Utilities.DateFormatter;
import uk.ac.nottingham.AmbLogger.FileHandling.MovingService;
import uk.ac.nottingham.AmbLogger.Utilities.NotificationUtilities;
import uk.ac.nottingham.AmbLogger.R;
import uk.ac.nottingham.AmbLogger.Utilities.TextUtils;

public class LoggingService extends Service {
    public LoggingService() {}

//    This service logs the data from the queue to a file and ensures the file size does not go
// above the 10 MB PHP upload limit.

    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    NotificationUtilities notUtils;
    Resources res;

    SharedPreferences pref;
    PowerManager.WakeLock wakelock;
    long wakelockTimeout = 5 * 60 * 60 * 1000;  // 5 hour timeout to remove AndroidStudio warning.

    static boolean logging;
    boolean sentIntents, writingToFile, dataInFile;
    long logPeriod;

    Timer logTimer;
    TimerTask loggingTask;

    public static String mainPath, gzipPath;
    String filename, toFile, outputTitle;

    StringBuilder stringBuilder = new StringBuilder();  // You don't need to say the string is empty

    OutputStream myOutputStream;
    File gzFile;

    @Override
    public void onCreate() {
        super.onCreate();

        logging = true;
        dataInFile = false;

        crashCheck();

        res = getResources();
        logPeriod = res.getInteger(R.integer.logging_period);

        if (date == null) {
            date = new DateFormatter(this).formDate();
        }
        filename = date + res.getString(R.string.id_spacer) + userID + res.getString(R.string.suffix) + res.getString(R.string.file_type);

        if (!crashed) {
            pref = PreferenceManager.getDefaultSharedPreferences(this);

            initialiseLogging();
        }

        notUtils = new NotificationUtilities(this);

        startForeground(notUtils.FOREGROUND_INT,notUtils.getForegroundNotification().build());
    }

    public void crashCheck() { // Check if the app has crashed and restarted the activity falsely.
        // Also checks if the phone is being shut down during recording, and logs this as a crash
        // to allow AMB Options to be entered correctly upon restart.
        if (userID == null || phoneDead) {
            crashed = true;
            stopSelf();
        }
    }

    public void initialiseLogging() { // Does what it says on the tin

//        Stop the service from being destroyed
        PowerManager myPowerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (myPowerManager != null) {
            wakelock = myPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"LoggingService:WakeLock");
            wakelock.acquire(wakelockTimeout);
        }

        mainPath = getExternalFilesDir(res.getString(R.string.fol_rec)) + "/";
        gzipPath = mainPath + filename;

        try { // to open a file-writing stream
            myOutputStream = new GZIPOutputStream(new FileOutputStream(gzipPath)) {{def.setLevel(Deflater.BEST_COMPRESSION);}};
        } catch (IOException e) {
            e.printStackTrace();
        }

        gzFile = new File(gzipPath);    // Initialise file variable for size check

        if (!BuildConfig.TEST_MODE) { // Log the initial ambulance options
            startLoggingAmb(true);
        }

        logTitle();

        // Set a timer to log data to file every 5 seconds
        logTimer = new Timer();
        loggingTT();
        logTimer.schedule(loggingTask, logPeriod, logPeriod);

        dataInFile = true;
    }

    public void logTitle() { // Create, format and log the file header
        ArrayList<String> titleList, gpsTitleList;
        gpsTitleList = new ArrayList<>(Arrays.asList(res.getStringArray(R.array.title_gps)));

        if (pref.getBoolean(KEY_G, true)) {
            titleList = new ArrayList<>(Arrays.asList(res.getStringArray(R.array.title_main_gy)));
        } else {
            titleList = new ArrayList<>(Arrays.asList(res.getStringArray(R.array.title_main_no_gy)));
        }

        if (! (BuildConfig.TEST_MODE && gpsOff) ) {
            titleList.addAll(gpsTitleList);
        }

        outputTitle = TextUtils.joinCSV(titleList) + "\n";

        try {
            myOutputStream.write(outputTitle.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void loggingTT() {
        loggingTask = new TimerTask() {
            @Override
            public void run() {
                if (recording) {

                    if (myQ == null) {
                        return;
                    }

                    // Booleans used to prevent onDestroy killing the writing process prematurely.
                    writingToFile = true;
                    writeToFile(); // Write some data!
                    writingToFile = false;
                } else {
                    logTimer.cancel();
                }
            }
        };
    }

//    Write some data!
    public void writeToFile() {

        stringBuilder.setLength(0);

        int qSize = myQ.size();

        int i = 0;
        try {
//            Extract a chunk of data from the queue to write to the file
            for (i = 0; i < qSize; i++) {
                stringBuilder.append(myQ.remove());
            }

        } catch (NoSuchElementException e) {    // If queue is found to be prematurely empty, exit for loop.

            if (BuildConfig.TEST_MODE && i < 10) {
                writeDebug(System.currentTimeMillis()
                        + "- Queue empty. Supposed size: " + qSize + ". Actual size: " + i + "\n");
                sendBroadcast(LOGGING_BROADCAST_PROBLEM);
            }

            e.getMessage();
        }

        toFile = stringBuilder.toString();

        byte[] bytesToStream = toFile.getBytes(StandardCharsets.UTF_8);

        try {
            myOutputStream.write(bytesToStream);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

//        noinspection StatementWithEmptyBody, WhileLoopSpinsOnField
        while (writingToFile) {
//            Wait it out.
        }

        if (!crashed && dataInFile) {
            if (!BuildConfig.TEST_MODE && !phoneDead) {
                startLoggingAmb(false); // Log the entries at the end of ambulance journey.
            }

            if (logTimer != null) {
                logTimer.cancel();
                logTimer.purge();
                logTimer = null;
            }

            if (myQ.size() > 0) {
                writeToFile();
            }
            myQ = null;

            // Clear the GPS data for the next recording.
            gpsData.clear();

            if (myOutputStream != null) {
                try {
                    myOutputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        if (!sentIntents) {
            if (crashed) {

                this.stopService(new Intent(getApplicationContext(), AudioService.class));
                this.stopService(new Intent(getApplicationContext(), GPSService.class));
                this.stopService(new Intent(getApplicationContext(), IMUService.class));

                if (gzipPath != null) {
                    new File(gzipPath).delete();
                }

            } else if (dataInFile){

                this.startService(new Intent(getApplicationContext(), MovingService.class));
                sendBroadcast(LOGGING_BROADCAST_RECORDING);

            } else {

                sendBroadcast(LOGGING_BROADCAST_CANCEL);

            }
            sentIntents = true;
        }

        if (debugStream != null) {
            try {
                debugStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if (wakelock != null && wakelock.isHeld()) {
            wakelock.release();
        }

        logging = false;
        date = null;
    }

    private void sendBroadcast(int response) {
        Intent intent = new Intent(loggingFilter);
        intent.putExtra(loggingInt, response);
        sendBroadcast(intent);
    }

    private void startLoggingAmb(boolean atStart) {
        startService(new Intent(this, MetaLoggingService.class)
                .putExtra(getString(R.string.bool_at_start), atStart));
    }

//    DEBUGGING FILE ZONE
    File debugFile;
    OutputStream debugStream;
    String debugName = date + "-debug.txt.gz", debugPath;

    public void writeDebug(String error) {
        if (debugStream == null) {
            debugPath = getExternalFilesDir("") + "/" + debugName;
            try {
                debugStream = new GZIPOutputStream(new FileOutputStream(debugPath)) {{def.setLevel(Deflater.BEST_COMPRESSION);}};
            } catch (IOException e) {
                e.printStackTrace();
            }
            debugFile = new File(debugPath);
        }

        try {
            debugStream.write(error.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
