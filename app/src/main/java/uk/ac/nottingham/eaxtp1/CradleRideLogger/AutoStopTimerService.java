package uk.ac.nottingham.eaxtp1.CradleRideLogger;

import android.app.Service;
import android.content.Intent;
import android.os.CountDownTimer;
import android.os.IBinder;

import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.forcedStop;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.recording;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.WifiCheckService.wifiConnected;

public class AutoStopTimerService extends Service {
    public AutoStopTimerService() {}

    // This service handles a CountdownTimer used to cancel recordings if there isn't a GPS
    // sample within the required thresholds.

    static boolean timerServiceRunning, cancelRecording, wifiCheckOn;
    boolean firstTimerOn, finalTimerOn;

    CountDownTimer firstTimer, finalTimer;
    long limitStart, limitMax;

    NotificationUtilities notUtils;

    @Override
    public void onCreate() {
        super.onCreate();

        timerServiceRunning = true;

        limitStart = getResources().getInteger(R.integer.limit_start);
        limitMax = getResources().getInteger(R.integer.limit_max);

        startFirstTimer();

        // As of API 26, Manifest-registered BroadcastReceivers are essentially disabled.
        // Therefore, Wifi needs to be 'manually' searched for. When stationary, a Job is created
        // which starts if connected to wifi and stops if then disconnected.
        new JobUtilities(this).scheduleWifi();

        wifiCheckOn = true;

    }

    // Countdown Timer to report if stationary limits have been met. This is rather than counting
    // the number of GPS samples.
    public void startFirstTimer() {

        firstTimer = new CountDownTimer(limitStart*1000,limitStart*1000) {
            @Override
            public void onTick(long millisUntilFinish) {}

            @Override
            public void onFinish() {
                // For some reason, timer doesn't get cancelled.
                if (wifiConnected) {
                    cancelRecording();
                } else {
                    startFinalTimer();
                }
            }
        }.start();

        firstTimerOn = true;

    }

    public void startFinalTimer() {

        firstTimerOn = false;

        finalTimer = new CountDownTimer((limitMax-limitStart)*1000, 60*1000) {
            @Override
            public void onTick(long l) {
                if (recording && wifiConnected) {
                    cancelRecording();
                    stopSelf();
                }
            }

            @Override
            public void onFinish() {
                if (recording) {
                    cancelRecording();
                    finalTimerOn = false;
                }
            }
        }.start();

        finalTimerOn = true;

    }

    NotificationUtilities getNotUtils() {
        if (notUtils == null) {
            notUtils = new NotificationUtilities(this);
        }
        return notUtils;
    }

    public void cancelRecording() {

        recording = false;
        forcedStop = true;

        // Stop all services due to inactivity
        this.stopService(new Intent(this, AudioService.class));
        this.stopService(new Intent(this, IMUService.class));
        this.stopService(new Intent(this, GPSService.class));

        if (BuildConfig.AMB_MODE) {
            Intent ambSelect = new Intent(getApplicationContext(), AmbSelect.class);
            ambSelect.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ambSelect.putExtra(getString(R.string.forcedIntent), true);
            startActivity(ambSelect);
        } else {
            this.stopService(new Intent(this, LoggingService.class));
        }

        getNotUtils().getManager().notify(getNotUtils().STOPPED_INT,
                getNotUtils().getStoppedNotification().build());

        stopSelf();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (firstTimerOn) {
            firstTimer.cancel();
        }
        if (finalTimerOn) {
            finalTimer.cancel();
            finalTimerOn = false;
        }

        timerServiceRunning = false;
        wifiCheckOn = false;

    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
