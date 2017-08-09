package uk.ac.nottingham.eaxtp1.CradleRideLogger;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.MediaRecorder;
import android.os.IBinder;
import android.os.PowerManager;

import java.io.IOException;

import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.recording;

public class AudioService extends Service {
    public AudioService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    PowerManager.WakeLock wakeLock;

    Thread audioThread;

    private MediaRecorder noiseDetector;

    static int amp;

    @Override
    public void onCreate() {
        super.onCreate();

        noiseDetector = new MediaRecorder();
        noiseDetector.setAudioSource(MediaRecorder.AudioSource.MIC);
        noiseDetector.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        noiseDetector.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
        noiseDetector.setOutputFile("/dev/null");
        try {
            noiseDetector.prepare();
            noiseDetector.start();
        } catch (IOException e) {
            e.printStackTrace();
        }

        audioThread = new Thread() {
            @Override
            public void run() {

                while (recording) {

                    amp = noiseDetector.getMaxAmplitude();

                    try {
                        sleep(8);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        };

        PowerManager myPowerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = myPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "My WakeLock");
        wakeLock.acquire();

        audioThread.start();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        try {
            noiseDetector.stop();
            noiseDetector.reset();
            noiseDetector.release();
        } catch (Exception e) {
            e.printStackTrace();
        }

        wakeLock.release();

    }
}
