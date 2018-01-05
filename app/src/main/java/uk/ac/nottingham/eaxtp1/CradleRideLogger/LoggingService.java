package uk.ac.nottingham.eaxtp1.CradleRideLogger;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.os.PowerManager;
import android.text.TextUtils;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Timer;
import java.util.TimerTask;
import java.util.zip.Deflater;
import java.util.zip.GZIPOutputStream;

import static uk.ac.nottingham.eaxtp1.CradleRideLogger.GPSService.gpsData;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.IMUService.myQ;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.crashed;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.gravityPresent;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.recording;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.userID;

public class LoggingService extends Service {
    public LoggingService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    String TAG = "Logging Service";

    int loggingPeriod = 5;     // Set the logging period in seconds

    PowerManager.WakeLock wakelock;
    long wakelockTimeout = 5 * 60 * 60 * 1000;  // 5 hour timeout to remove AndroidStudio warning.
    boolean sentIntents;

    static boolean logging;

    Timer logTimer, sizeCheckTimer;
    TimerTask loggingTask, sizeCheckingTask;

    int uploadLimit = 10350000; // Restricts file size to ~9.9mb
    long /*sampleTime,*/ checkDelay = 5000;
    boolean nearLimit;

    File gzFile;

    String outputTitle;

    List<String> titleList;
    int qSize;

    boolean writingToFile;

    //    Creates a string of the current date and time
    @SuppressLint("SimpleDateFormat")
    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy_MM_dd-HH_mm");
    Date todayDate = new Date();
    public String date = dateFormat.format(todayDate);

    OutputStream myOutputStream;
    int zipPart = 1;
    String filepath = "Recording";
    String filename = date + "-ID" + String.valueOf(userID) + "-" + zipPart + ".csv.gz";
    static String mainPath, gzipPath;

    StringBuilder stringBuilder = new StringBuilder("");
    String toFile;

    @Override
    public void onCreate() {
        super.onCreate();

        Log.i(TAG, "Created.");
        logging = true;

        crashCheck();

        if (!crashed) {
            initialiseLogging();
        }
    }

    public void crashCheck() {
//        if (userID == 13319625 || userID == 0) {
        if (userID == 0) {
            crashed = true;
            onDestroy();
        }
    }

    public void initialiseLogging() {

        mainPath = String.valueOf(getExternalFilesDir(filepath)) + "/";
        gzipPath = mainPath + filename;

        try {
            myOutputStream = new FileOutputStream(gzipPath);
            myOutputStream = new GZIPOutputStream(myOutputStream)
            {{def.setLevel(Deflater.BEST_COMPRESSION);}};
        } catch (IOException e) {
            e.printStackTrace();
        }

        gzFile = new File(gzipPath);

        logTitle();

        logTimer = new Timer();
        loggingTT();
        logTimer.schedule(loggingTask, 1000 * loggingPeriod, 1000 * loggingPeriod);

        PowerManager myPowerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (myPowerManager != null) {
            wakelock = myPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Logging WakeLock");
            wakelock.acquire(wakelockTimeout);
        }

        sizeCheckTimer = new Timer();
        sizeCheckTT();
        sizeCheckTimer.schedule(sizeCheckingTask, 5000, checkDelay);
    }

    public void logTitle() {
        if (gravityPresent) {
            titleList = Arrays.asList("id", "X", "Y", "Z", "Time", "GX", "GY", "GZ",
                    "North", "East", "Down", "GPS Sample", "Audio",
                    "Lat", "Long", "Speed", "GPS Time", "Acc", "Alt", "Bearing", "ERT");
        } else {
            titleList = Arrays.asList("id", "X", "Y", "Z", "Time", "GPS Sample", "Audio",
                    "Lat", "Long", "Speed", "GPS Time", "Acc", "Alt", "Bearing", "ERT");
        }

        outputTitle = TextUtils.join(",", titleList);
        outputTitle = outputTitle + "\n";

        try {
            myOutputStream.write(outputTitle.getBytes("UTF-8"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void loggingTT() {
        loggingTask = new TimerTask() {
            @Override
            public void run() {
                if (recording) {
                    writingToFile = true;
                    writeToFile();
                    writingToFile = false;
                }
            }
        };
    }

    public void writeToFile() {
        stringBuilder.setLength(0);

        qSize = myQ.size();
        try {

            for (int i = 0; i < qSize; i++) {
                stringBuilder.append(myQ.remove());
            }

        } catch (NoSuchElementException e) {    // If queue is found to be prematurely empty, exit for loop.

            Log.i(TAG, "Queue empty. Supposed size: " + qSize /*+ ". Remove attempt number: " + i */+ ".");
            e.getMessage();
        }

        toFile = stringBuilder.toString();

        try {
            myOutputStream.write(toFile.getBytes("UTF-8"));
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (nearLimit) {
            zipPart++;
            nearLimit = false;
            fileSplitter(zipPart);
        }
    }

    public void sizeCheckTT() {
        sizeCheckingTask = new TimerTask() {
            @Override
            public void run() {
                File gzFile = new File(gzipPath);
                if (gzFile.length() > uploadLimit) {
                    nearLimit = true;
                }
            }
        };
    }

    public void fileSplitter(int filePart) {

        gzipPath = mainPath + date + "-ID" + String.valueOf(userID) + "-" + filePart + ".csv.gz";

        try {
            myOutputStream.close();
            myOutputStream = new FileOutputStream(gzipPath);
            myOutputStream = new GZIPOutputStream(myOutputStream)
            {{def.setLevel(Deflater.BEST_COMPRESSION);}};
        } catch (IOException e) {
            e.printStackTrace();
        }

        gzFile = new File(gzipPath);

        Log.i(TAG, "fileSplitter: Split File");

    }

    @Override
    public void onDestroy() {
        super.onDestroy();

//        noinspection StatementWithEmptyBody
        while (writingToFile) {
//            Wait it out.
        }

        if (logTimer != null) {
            logTimer.cancel();
            logTimer.purge();
            logTimer = null;
        }

        if (myQ.size() > 0) {
            Log.i(TAG, "Writing final outputs.");
            writeToFile();
        }
        myQ = null;

        // Clear the GPS data for the next recording.
        gpsData = "";

        if (myOutputStream != null) {
            try {
                myOutputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if (sizeCheckTimer != null) {
            sizeCheckTimer.cancel();
        }

        if (!sentIntents) {
            if (crashed) {

                Intent stopAudio = new Intent(getApplicationContext(), AudioService.class);
                Intent stopGPS = new Intent(getApplicationContext(), GPSService.class);
                Intent stopIMU = new Intent(getApplicationContext(), IMUService.class);
                this.stopService(stopAudio);
                this.stopService(stopGPS);
                this.stopService(stopIMU);

                Intent deletingService = new Intent(getApplicationContext(), FileDeletingService.class);
                this.startService(deletingService);

            } else {

                Intent movingService = new Intent(getApplicationContext(), MovingService.class);
                this.startService(movingService);

            }
            sentIntents = true;
        }

        if (wakelock != null && wakelock.isHeld()) {
            wakelock.release();
        }

        logging = false;
        Log.i(TAG, "Destroyed!");
    }
}
