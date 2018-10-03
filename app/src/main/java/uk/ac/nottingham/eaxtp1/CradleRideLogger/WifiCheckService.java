package uk.ac.nottingham.eaxtp1.CradleRideLogger;

import android.app.job.JobParameters;
import android.app.job.JobService;

import java.util.Timer;
import java.util.TimerTask;

import static uk.ac.nottingham.eaxtp1.CradleRideLogger.GPSService.wifiCheckOn;

// This service starts when wifi first connects, and keeps running until either wifi disconnects
// (rescheduling the job) or the stationary timer expires. This is a bit of a work around to find
// 'wifiConnected' for the stationary-detector now that manifest-registered BroadcastReceivers
// have been disabled.
public class WifiCheckService extends JobService {

    static boolean wifiConnected;
    Timer wifiTimer;
    TimerTask wifiTimerTask;

    // Called when first connecting to wifi
    @Override
    public boolean onStartJob(JobParameters jobParameters) {

        wifiConnected = true;

        wifiTimer = new Timer();
        wifiTT(jobParameters);
        wifiTimer.schedule(wifiTimerTask,1000,1000);

        return true;
    }

    // Checks every second if the wifi check is still needed
    public void wifiTT(final JobParameters parameters) {
        wifiTimerTask = new TimerTask() {
            @Override
            public void run() {
                if (!wifiCheckOn) {
                    // Cancel the job and timer if the check is not needed
                    jobFinished(parameters,false);
                    wifiConnected = false;
                    wifiTimer.cancel();
                }
            }
        };
    }

    // Called if wifi disconnects
    @Override
    public boolean onStopJob(JobParameters jobParameters) {

        wifiConnected = false;
        wifiTimer.cancel();

        return wifiCheckOn; // Should reschedule only if stationary
    }
}
