package uk.ac.nottingham.eaxtp1.CradleRideLogger;

//import android.app.IntentService;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;

import java.io.File;

import static uk.ac.nottingham.eaxtp1.CradleRideLogger.RecordingService.gzipPath;

public class FileDeletingService extends Service {
    public FileDeletingService() {
        super();
    }

    @Override
    public void onCreate() {
//        String filepath = intent.getStringExtra("filepath");

        if (gzipPath != null) {
            new File(gzipPath).delete();
        }

        onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
