package uk.ac.nottingham.eaxtp1.CradleRideLogger;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Intent;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.util.Log;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class UploadJobService extends JobService {
    public UploadJobService() {
    }

    Intent uploadService;
    String TAG = "Job Service";

    @Override
    public void onCreate() {
        super.onCreate();

        uploadService = new Intent(this, UploadService.class);

    }

    @Override
    public boolean onStartJob(JobParameters params) {

        Log.i(TAG, "Starting Upload Job " + params.getJobId());

        startService(uploadService);

        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {

        Log.i(TAG, "Stopping Upload Job " + params.getJobId());

        stopService(uploadService);

        return false;
    }

}
