package uk.ac.nottingham.eaxtp1.CradleRideLogger;

import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.MediaRecorder;
import android.os.IBinder;
import android.os.PowerManager;
import java.util.Timer;
import java.util.TimerTask;

import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.crashed;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.foreID;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.recording;

public class AudioService extends Service {
    public AudioService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    PowerManager.WakeLock wakeLock;
    long wakelockTimeout = 5 * 60 * 60 * 1000;  // 5 hour timeout to remove AndroidStudio warning.

    private MediaRecorder noiseDetector;

    static int amp;

    Timer timer;
    TimerTask timerTask;

    @Override
    public void onCreate() {
        super.onCreate();

        if (crashed) {
            onDestroy();
        } else {
            prepAudio();
        }

        Notification notification = new Notification.Builder(this)
                .setSmallIcon(R.drawable.ambulance_symb)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.recording_data)).build();

        startForeground(foreID, notification);

    }

    public void prepAudio() {
        noiseDetector = new MediaRecorder();
        noiseDetector.setAudioSource(MediaRecorder.AudioSource.MIC);
        noiseDetector.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        noiseDetector.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
        noiseDetector.setOutputFile("/dev/null");
        try {
            noiseDetector.prepare();
            noiseDetector.start();
        } catch (Exception e) {
            e.printStackTrace();
        }

        timer = new Timer();

        initialiseTT();

        timer.schedule(timerTask, 0, 20);

        PowerManager myPowerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (myPowerManager != null) {
            wakeLock = myPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Audio WakeLock");
            wakeLock.acquire(wakelockTimeout);
        }
    }

    public void initialiseTT() {
        timerTask = new TimerTask() {
            @Override
            public void run() {
                if (recording || BuildConfig.AMB_MODE) {
                    amp = noiseDetector.getMaxAmplitude();
                }
            }
        };
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (timer != null) {
            timer.cancel();
        }

        if (noiseDetector != null) {
            try {
//                noiseDetector.stop();
//                noiseDetector.reset();
                noiseDetector.release();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }

    }
}
