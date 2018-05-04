package uk.ac.nottingham.eaxtp1.CradleRideLogger;

import android.annotation.SuppressLint;
import android.app.Notification;
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
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.amb;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.ambMode;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.buffEnd;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.crashed;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.emerge;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.foreID;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.gravityPresent;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.pat;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.recording;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.trans;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.troll;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.userID;

public class LoggingService extends Service {
    public LoggingService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    String TAG = "CRL_LoggingService";

    int loggingPeriod = 5;     // Set the logging period in seconds

    PowerManager.WakeLock wakelock;
    long wakelockTimeout = 5 * 60 * 60 * 1000;  // 5 hour timeout to remove AndroidStudio warning.
    boolean sentIntents;

    static boolean logging;

    Timer logTimer, sizeCheckTimer;
    TimerTask loggingTask, sizeCheckingTask;

    int uploadLimit = 10350000; // TODO: Set to 10350000 to restrict file size to ~9.9mb
    long checkDelay = 5000;
    boolean nearLimit;

    File gzFile, endFile;
    boolean multiFile;
    String outputTitle, endName;

    List<String> titleList;
    int qSize, samplesInFile;

    boolean writingToFile;

    //    Creates a string of the current date and time
    @SuppressLint("SimpleDateFormat")
    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy_MM_dd-HH_mm");
    Date todayDate = new Date();
    public String date = dateFormat.format(todayDate);

    OutputStream myOutputStream, myAmbStream;
    int zipPart = 1;
    String filepath = "Recording", digitAdjuster = "-0", suffix = "-END";
    String filename = date + "-ID" + String.valueOf(userID) + digitAdjuster + zipPart + ".csv.gz";
    String mainPath, gzipPath, ambPath;

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

        Notification notification = new Notification.Builder(this)
                .setSmallIcon(R.drawable.ambulance_symb)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.recording_data)).build();

        startForeground(foreID, notification);
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

        if (ambMode) {
            prepAmb();
        }

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
        samplesInFile = 0;
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

        int i = 0;
        try {

            for (i = 1; i <= qSize; i++) {
                stringBuilder.append(myQ.remove());
            }

        } catch (NoSuchElementException e) {    // If queue is found to be prematurely empty, exit for loop.

            Log.i(TAG, "Queue empty. Supposed size: " + qSize + "." + " Actual size: " + i);
            e.getMessage();
        }

        samplesInFile += i;

        toFile = stringBuilder.toString();

        try {
            myOutputStream.write(toFile.getBytes("UTF-8"));
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (nearLimit) {
            zipPart++;
            if (zipPart == 10) {
                digitAdjuster = "-";
            }
            nearLimit = false;
            fileSplitter(zipPart);
            multiFile = true;
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

        gzipPath = mainPath + date + "-ID" + String.valueOf(userID) + digitAdjuster + filePart + ".csv.gz";

        try {
            myOutputStream.close();
            myOutputStream = new FileOutputStream(gzipPath);
            myOutputStream = new GZIPOutputStream(myOutputStream)
            {{def.setLevel(Deflater.BEST_COMPRESSION);}};
        } catch (IOException e) {
            e.printStackTrace();
        }

        gzFile = new File(gzipPath);
        samplesInFile = 0;

        Log.i(TAG, "fileSplitter: Split File");

    }

    @Override
    public void onDestroy() {
        super.onDestroy();

//        noinspection StatementWithEmptyBody, WhileLoopSpinsOnField
        while (writingToFile) {
//            Wait it out.
        }

        if (!crashed) {
            if (ambMode) {
                logAmb(false);  // Log the entries at the end of ambulance journey.
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

            if (multiFile || ambMode) {
                endName = mainPath + date + "-ID" + String.valueOf(userID) + digitAdjuster + zipPart + suffix + ".csv.gz";
            } else {
                endName = mainPath + date + "-ID" + String.valueOf(userID) + ".csv.gz";
            }
            endFile = new File(endName);
            gzFile.renameTo(endFile);

            if (sizeCheckTimer != null) {
                sizeCheckTimer.cancel();
            }
        }

        if (!sentIntents) {
            if (crashed) {

                Intent stopAudio = new Intent(getApplicationContext(), AudioService.class);
                Intent stopGPS = new Intent(getApplicationContext(), GPSService.class);
                Intent stopIMU = new Intent(getApplicationContext(), IMUService.class);
                this.stopService(stopAudio);
                this.stopService(stopGPS);
                this.stopService(stopIMU);

                if (gzipPath != null) {
                    new File(gzipPath).delete();
                }

            } else if (buffEnd){

                Intent bufferService = new Intent(getApplicationContext(), BufferService.class);
                bufferService.putExtra("Samples", samplesInFile)
                        .putExtra("FileParts", zipPart)
                        .putExtra("Folder", mainPath);
                this.startService(bufferService);

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

    public void prepAmb() {
        suffix = "-AMB";
        ambPath = mainPath + date + "-ID" + String.valueOf(userID) + "-00.csv.gz";
        try {
            myAmbStream = new FileOutputStream(ambPath);
            myAmbStream = new GZIPOutputStream(myAmbStream)
            {{def.setLevel(Deflater.BEST_COMPRESSION);}};
        } catch (IOException e) {
            e.printStackTrace();
        }
        logAmb(true);
    }

    public void logAmb(boolean atStart) {
        String ambList;

        if (atStart) {
            ambList = TextUtils.join(",", Arrays.asList("Ambulance", amb, "Trolley", troll, "Patient", pat,""));
        } else {
            ambList = TextUtils.join(",", Arrays.asList("Reason for Transfer", trans, "Emergency driving used", emerge)) + "\n";
        }

        try {
            myAmbStream.write(ambList.getBytes("UTF-8"));
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (!atStart) {
            if (myAmbStream != null) {
                try {
                    myAmbStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
