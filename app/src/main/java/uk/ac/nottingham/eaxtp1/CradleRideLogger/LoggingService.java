package uk.ac.nottingham.eaxtp1.CradleRideLogger;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Build;
import android.os.CountDownTimer;
import android.os.IBinder;
import android.os.PowerManager;
import android.text.TextUtils;

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

import static uk.ac.nottingham.eaxtp1.CradleRideLogger.GPSService.gpsData;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.IMUService.date;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.IMUService.myQ;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.crashed;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.KEY_FS;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.KEY_G;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.gpsOff;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.loggingFilter;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.loggingInt;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.phoneDead;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.recording;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.userID;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.versionNum;

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

    SharedPreferences preferences;
    CountDownTimer waitTimer;
    PowerManager.WakeLock wakelock;
    long wakelockTimeout = 5 * 60 * 60 * 1000;  // 5 hour timeout to remove AndroidStudio warning.

    static boolean logging;
    boolean sentIntents, bufferOn, writingToFile, dataInFile;
    int buffBy, buffSamples;
    long logPeriod;

    Timer logTimer;
    TimerTask loggingTask;

    static String mainPath, gzipPath;
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
        filename = date + res.getString(R.string.id_spacer) + userID + getString(R.string.file_type);
        if (BuildConfig.AMB_MODE) {
            filename = filename.replace(res.getString(R.string.file_type), res.getString(R.string.suffix) + res.getString(R.string.file_type));
        }

        if (!crashed) {
            preferences = getSharedPreferences(getString(R.string.pref_main), MODE_PRIVATE);

            if (BuildConfig.CROWD_MODE) {    // Get end buffer details
                buffBy = preferences.getInt(getString(R.string.key_pref_buff_end),res.getInteger(R.integer.buff_default));
                bufferOn = buffBy != 0;
            }

            // If the Start Buffer is enabled, wait until the Buffer Time has passed before
            // creating a file. Otherwise, create the file now.
            if (bufferOn) {
                sendBroadcast(0);   // Tell Main Activity no data has been written
                int fs = preferences.getInt(KEY_FS, 100);   // Read the IMU sample frequency
//        if (fs > 250) {
//            logPeriod = (int) Math.ceil(logPeriod * fs / 200);    // Logs quicker at higher frequencies, to account for increased data size.
//        }
                buffSamples = buffBy*60*fs; buffBy = buffBy*60000;
                startTimer();
            } else {
                initialiseLogging();
            }
        }

        notUtils = new NotificationUtilities(this);

        startForeground(notUtils.FOREGROUND_INT,notUtils.getForegroundNotification().build());
    }

    public void crashCheck() { // Check if the app has crashed and restarted the activity falsely.
        // Also checks if the phone is being shut down during recording, and logs this as a crash
        // to allow AMB Options to be entered correctly upon restart.
//        if (userID == 13319625 || userID == 0) {
        if (userID == 0 || phoneDead) {
            crashed = true;
            stopSelf();
        }
    }

    public void startTimer() { // Countdown timer to wait for buffer time to pass before logging.
        waitTimer = new CountDownTimer(buffBy,buffBy) {
            @Override
            public void onTick(long millisUntilFinished) {}

            @Override
            public void onFinish() {
                initialiseLogging();
            }
        }.start();
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

        if (BuildConfig.AMB_MODE) { // Log the initial ambulance options
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

        if (preferences.getBoolean(KEY_G, true)) {
            titleList = new ArrayList<>(Arrays.asList(res.getStringArray(R.array.title_main_gy)));
        } else {
            titleList = new ArrayList<>(Arrays.asList(res.getStringArray(R.array.title_main_no_gy)));
        }

        if (! (BuildConfig.TEST_MODE && gpsOff) ) {
            titleList.addAll(gpsTitleList);
        }

        outputTitle = TextUtils.join(",", titleList);
        // Include ID number, version and model as comments to aid processing
        outputTitle = outputTitle
                + res.getString(R.string.com_id) + userID
                + res.getString(R.string.com_vers) + versionNum
                + res.getString(R.string.com_model) + Build.MODEL
                + "\n";

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

        int qSize = myQ.size() - buffSamples;   // Don't record anything within the end buffer time

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
                sendBroadcast(99);
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
            if (BuildConfig.AMB_MODE && !phoneDead) {
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
                sendBroadcast(1);

            } else {

                sendBroadcast(0);

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
        startService(new Intent(this, AmbLoggingService.class)
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
