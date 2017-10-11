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
import java.util.Timer;
import java.util.TimerTask;
import java.util.zip.Deflater;
import java.util.zip.GZIPOutputStream;

import static uk.ac.nottingham.eaxtp1.CradleRideLogger.AudioService.amp;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.GPSService.gpsSample;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.GPSService.sGPS;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.GPSService.sLat;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.GPSService.sLong;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.GPSService.sSpeed;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.IMUService.sD;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.IMUService.sE;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.IMUService.sGX;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.IMUService.sGY;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.IMUService.sGZ;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.IMUService.sN;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.IMUService.sX;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.IMUService.sY;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.IMUService.sZ;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.IMUService.sampleTime;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.crashed;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.gravityPresent;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.userID;

public class LoggingService extends Service {
    public LoggingService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    int loggingFrequency = 200;     // Set the frequency in Hz

    PowerManager.WakeLock wakelock;

    Timer logTimer, sizeCheckTimer;
    TimerTask loggingTask, sizeCheckingTask;

    int uploadLimit = 10350000; // Restricts file size to ~9.9mb
    long /*sampleTime,*/ checkDelay = 5000;
    boolean nearLimit;

    File gzFile;

    int id;
    String sID, /*sX, sY, sZ,*/ sTime;
//    String sGX, sGY, sGZ;
//    String sEast, sNorth, sDown;
    String sAmp = "";
    int prevAmp;
    long prevSample;

    String outputToData, outputTitle;

    List<String> outputList, titleList;

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

    static boolean newSample;

    @Override
    public void onCreate() {
        super.onCreate();

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
        logTimer.schedule(loggingTask, 0, 1000/loggingFrequency);

        PowerManager myPowerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakelock = myPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Logging WakeLock");
        wakelock.acquire();

        sizeCheckTimer = new Timer();
        sizeCheckTT();
        sizeCheckTimer.schedule(sizeCheckingTask, 5000, checkDelay);
    }

    public void logTitle() {
        if (gravityPresent) {
            titleList = Arrays.asList("id", "X", "Y", "Z", "Time", "GX", "GY", "GZ",
                    "North", "East", "Down", "GPS Sample", "Lat", "Long", "Noise", "Speed");
        } else {
            titleList = Arrays.asList("id", "X", "Y", "Z", "Time",
                    "GPS Sample", "Lat", "Long", "Noise", "Speed");
        }

        outputTitle = TextUtils.join(",", titleList);

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
//                sampleTime = sampleTime + 5;
                if (newSample) {
                    newSample = false;
                    id++;
                    sID = String.valueOf(id);
                    sTime = String.valueOf(sampleTime);

                    if (amp != 0 && amp != prevAmp) {
                        sAmp = String.valueOf(amp);
                        prevAmp = amp;
                    } else {
                        sAmp = "";
                    }

                    if (gravityPresent) {
                        if (gpsSample > prevSample) {
                            outputList = Arrays.asList(sID, sX, sY, sZ, sTime, sGX, sGY, sGZ,
                                    sN, sE, sD, sGPS, sLat, sLong, sAmp, sSpeed);
                            prevSample = gpsSample;
                        } else {
                            outputList = Arrays.asList(sID, sX, sY, sZ, sTime, sGX, sGY, sGZ,
                                    sN, sE, sD, sGPS, "", "", sAmp);
                        }
                    } else {
                        if (gpsSample > prevSample) {
                            outputList = Arrays.asList(sID, sX, sY, sZ, sTime, sGPS, sLat, sLong, sAmp, sSpeed);
                            prevSample = gpsSample;
                        } else {
                            outputList = Arrays.asList(sID, sX, sY, sZ, sTime, sGPS, "", "", sAmp);
                        }
                    }

                    outputToData = "\n" + TextUtils.join(",", outputList);

                    try {
                        myOutputStream.write(outputToData.getBytes("UTF-8"));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    if (nearLimit) {
                        zipPart++;
                        nearLimit = false;
                        fileSplitter(zipPart);
                    }
                }
            }
        };
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

    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (logTimer != null) {
            logTimer.cancel();
        }

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

        if (crashed) {

            Intent stopAudio = new Intent(this, AudioService.class);
            Intent stopGPS = new Intent(this, GPSService.class);
            Intent stopIMU = new Intent(this, IMUService.class);
            this.stopService(stopAudio);
            this.stopService(stopGPS);
            this.stopService(stopIMU);

            Intent deleteCrashFile = new Intent(this, MovingService.class);
            this.startService(deleteCrashFile);

        } else {

            Intent movingService = new Intent(this, MovingService.class);
            this.startService(movingService);

        }

        if (wakelock != null) {
            wakelock.release();
        }
    }
}
