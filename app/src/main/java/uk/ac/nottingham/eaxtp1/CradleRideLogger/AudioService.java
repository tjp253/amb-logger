package uk.ac.nottingham.eaxtp1.CradleRideLogger;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.preference.PreferenceManager;

import java.io.File;
import java.util.Timer;
import java.util.TimerTask;

import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.crashed;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.recording;

public class AudioService extends Service {
    public AudioService() {}

    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    NotificationUtilities notUtils;

    PowerManager.WakeLock wakeLock;
    long wakelockTimeout = 5 * 60 * 60 * 1000;  // 5 hour timeout to remove Android Studio warning.

    private MediaRecorder noiseDetector;

    static int amp; // Declare the global MaxAmplitude variable

    private int audio_codec;

    String output_file = "/dev/null"; // temp location for Android 10 and below

//    Declare timer for MaxAmplitude check
    Timer timer;

    @Override
    public void onCreate() {
        super.onCreate();

        if (crashed) {
            onDestroy();
        } else {
            if (BuildConfig.TEST_MODE) {
                audio_codec = PreferenceManager.getDefaultSharedPreferences(this)
                        .getInt("codec", MediaRecorder.AudioEncoder.AMR_NB);
            } else {
                audio_codec = MediaRecorder.AudioEncoder.AMR_NB;
            }
            prepAudio();
        }

        notUtils = new NotificationUtilities(this);

        startForeground(notUtils.FOREGROUND_INT,notUtils.getForegroundNotification().build());
    }

    public void prepAudio() {
//        Initialise microphone and start it listening
        noiseDetector = new MediaRecorder();
        noiseDetector.setAudioSource(MediaRecorder.AudioSource.MIC);
        noiseDetector.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        noiseDetector.setAudioEncoder(audio_codec);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) { // Android 11 and above
            output_file = getExternalCacheDir().getAbsolutePath() + "/temp.3gp";
        }
        noiseDetector.setOutputFile(output_file);
        try { // need to use try/catch to handle exception, even though if caught the app fails!
            noiseDetector.prepare();
            noiseDetector.start();
        } catch (Exception e) {
            e.printStackTrace();
        }

        timer = new Timer();

//        Previous tests showed that MaxAmplitude only gave a result every 20 millis. No point
// calling it quicker.
        timer.schedule(timerTask, 0, 20);

//        Stop the service from being destroyed
        PowerManager myPowerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (myPowerManager != null) {
            wakeLock = myPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AudioService:WakeLock");
            wakeLock.acquire(wakelockTimeout);
        }
    }

//    Initialise the timer task
    public TimerTask timerTask = new TimerTask() {
            @Override
            public void run() {
            if (recording || BuildConfig.AMB_MODE) {
                amp = noiseDetector.getMaxAmplitude();
            }
        }
    };

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (timer != null) {
            timer.cancel();
        }

        if (noiseDetector != null) {
            try {
                noiseDetector.release();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // if using Android 11 or later, file should be manually deleted from the storage area.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            new File(output_file).delete();
        }

        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }

    }
}
