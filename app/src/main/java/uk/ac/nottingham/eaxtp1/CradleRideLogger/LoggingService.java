package uk.ac.nottingham.eaxtp1.CradleRideLogger;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.CountDownTimer;
import android.os.IBinder;
import android.os.PowerManager;
import android.support.v4.content.LocalBroadcastManager;
import android.text.TextUtils;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.NoSuchElementException;
import java.util.Timer;
import java.util.TimerTask;
import java.util.zip.Deflater;
import java.util.zip.GZIPOutputStream;

import static uk.ac.nottingham.eaxtp1.CradleRideLogger.AmbSelect.keyAmb;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.AmbSelect.keyEmerge;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.AmbSelect.keyPat;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.AmbSelect.keyTrans;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.AmbSelect.keyTroll;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.GPSService.gpsData;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.IMUService.myQ;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.crashed;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.foreID;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.KEY_FS;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.KEY_G;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.gpsOff;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.loggingFilter;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.loggingInt;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.recording;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.userID;

public class LoggingService extends Service {
    public LoggingService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    final String TAG = "CRL_LoggingService";

    int logPeriod = 5;     // Set the logging period in seconds

    PowerManager.WakeLock wakelock;
    long wakelockTimeout = 5 * 60 * 60 * 1000;  // 5 hour timeout to remove AndroidStudio warning.
    boolean sentIntents;

    SharedPreferences preferences;
    int buffBy, buffSamples, fs; boolean bufferOn;   CountDownTimer waitTimer;

    static boolean logging;

    Timer logTimer, sizeCheckTimer;
    TimerTask loggingTask, sizeCheckingTask;

    final int uploadLimit = 10350000; // TODO: Set to 10350000 to restrict file size to ~9.9mb
    boolean nearLimit;

    File gzFile, endFile;
    boolean multiFile;
    String outputTitle, endName;

    ArrayList<String> titleList, gpsTitleList;
    int qSize, samplesInFile;

    boolean writingToFile, dataInFile;

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
            preferences = getSharedPreferences(getString(R.string.pref_main), MODE_PRIVATE);
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

        dataInFile = false;

        PowerManager myPowerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (myPowerManager != null) {
            wakelock = myPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Logging WakeLock");
            wakelock.acquire(wakelockTimeout);
        }

        mainPath = String.valueOf(getExternalFilesDir(filepath)) + "/";
        gzipPath = mainPath + filename;

        try {
            myOutputStream = new GZIPOutputStream(new FileOutputStream(gzipPath)) {{def.setLevel(Deflater.BEST_COMPRESSION);}};
        } catch (IOException e) {
            e.printStackTrace();
        }

        gzFile = new File(gzipPath);

        fs = preferences.getInt(KEY_FS, 100);
//        if (fs > 250) {
//            logPeriod = (int) Math.ceil(logPeriod * fs / 200);    // Logs quicker at higher frequencies, to account for increased data size.
//        }

        if (BuildConfig.AMB_MODE) {
            prepAmb();
        } else if (BuildConfig.CROWD_MODE) {
            buffBy = preferences.getInt(getString(R.string.key_pref_buff_end),getResources().getInteger(R.integer.buff_default));
            bufferOn = buffBy != 0;
        }

        logTitle();

        if (!bufferOn) {
            startLogging();
        } else {
            sendBroadcast(0);
            buffSamples = buffBy*60*fs; buffBy = buffBy*60000;
            startTimer();
        }
    }

    public void startLogging() {
        logTimer = new Timer();
        loggingTT();
        logTimer.schedule(loggingTask, 1000 * logPeriod, 1000 * logPeriod);

        sizeCheckTimer = new Timer();
        sizeCheckTT();
        sizeCheckTimer.schedule(sizeCheckingTask, 5000, 1000 * logPeriod);
    }

    public void startTimer() {
        waitTimer = new CountDownTimer(buffBy,buffBy) {
            @Override
            public void onTick(long millisUntilFinished) {}

            @Override
            public void onFinish() {
                startLogging();
            }
        }.start();
    }

    public void logTitle() {
        gpsTitleList = new ArrayList<> ( Arrays.asList("Lat", "Long", "Speed", "GPS Time", "Acc", "Alt", "Bearing", "ERT") );

        if (preferences.getBoolean(KEY_G, true)) {
            titleList = new ArrayList<> ( Arrays.asList("id", "X", "Y", "Z", "Time", "GyX", "GyY", "GyZ",
                    "North", "East", "Down", "GPS Sample", "Audio") );
        } else {
            titleList = new ArrayList<> ( Arrays.asList("id", "X", "Y", "Z", "Time", "GPS Sample", "Audio") );
        }

        if (! (BuildConfig.TEST_MODE && gpsOff) ) {
            titleList.addAll(gpsTitleList);
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
                    if (!dataInFile) {
                        sendBroadcast(1);
                        dataInFile = true;
                    }
                } else {
                    logTimer.cancel();
                }
            }
        };
    }

    public void writeToFile() {
        if (nearLimit) {
            zipPart++;
            if (zipPart == 10) {
                digitAdjuster = "-";
            }
            nearLimit = false;
            fileSplitter(zipPart);
            multiFile = true;
        }

        stringBuilder.setLength(0);

        qSize = myQ.size() - buffSamples;

        int i = 0;
        try {

            for (i = 0; i < qSize; i++) {
                stringBuilder.append(myQ.remove());
            }

        } catch (NoSuchElementException e) {    // If queue is found to be prematurely empty, exit for loop.

            String errMess = "Queue empty. Supposed size: " + qSize + "." + " Actual size: " + i;
            if (BuildConfig.TEST_MODE && i < 10) {
                writeDebug(String.valueOf(System.currentTimeMillis()) + "-" + errMess + "\n");
                sendBroadcast(99);
            }

            Log.i(TAG, errMess);
            e.getMessage();
        }

        samplesInFile += i;

        toFile = stringBuilder.toString();

        try {
            myOutputStream.write(toFile.getBytes("UTF-8"));
        } catch (IOException e) {
            e.printStackTrace();
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
            myOutputStream = new GZIPOutputStream(new FileOutputStream(gzipPath))
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
            if (BuildConfig.AMB_MODE) {
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

            if (multiFile || BuildConfig.AMB_MODE) {
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

            } else if (dataInFile){

                Intent movingService = new Intent(getApplicationContext(), MovingService.class);
                this.startService(movingService);
                sendBroadcast(1);

            } else {
                endFile.delete();
                if (BuildConfig.AMB_MODE) {
                    new File(ambPath).delete();
                }
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
        Log.i(TAG, "Destroyed!");
    }

    private void sendBroadcast(int response) {
        Intent intent = new Intent(loggingFilter);
        intent.putExtra(loggingInt, response);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
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
        String ambList, amb, troll, pat, trans, emerge;

        SharedPreferences ambPref = getSharedPreferences(getString(R.string.pref_amb), MODE_PRIVATE);

        if (atStart) {
            amb = ambPref.getString(keyAmb,"");
            troll = ambPref.getString(keyTroll,"");
            if (ambPref.getBoolean(keyPat, true)) {
                pat = getString(R.string.yesButt);
            } else {
                pat = getString(R.string.noButt);
            }
            ambList = TextUtils.join(",", Arrays.asList("Ambulance", amb, "Trolley", troll, "Patient", pat,""));
        } else {
            trans = ambPref.getString(keyTrans,"");
            emerge = ambPref.getString(keyEmerge,"");
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

//    DEBUGGING FILE ZONE
    File debugFile;
    OutputStream debugStream;
    String debugName = date + "-debug.txt.gz", debugPath;

    public void writeDebug(String error) {
        if (debugStream == null) {
            debugPath = String.valueOf(getExternalFilesDir("")) + "/" + debugName;
            try {
                debugStream = new GZIPOutputStream(new FileOutputStream(debugPath)) {{def.setLevel(Deflater.BEST_COMPRESSION);}};
            } catch (IOException e) {
                e.printStackTrace();
            }
            debugFile = new File(debugPath);
        }

        try {
            debugStream.write(error.getBytes("UTF-8"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
