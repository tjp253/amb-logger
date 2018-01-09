package uk.ac.nottingham.eaxtp1.CradleRideLogger;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Intent;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.util.Log;

import java.io.File;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class UploadJobService extends JobService {
    public UploadJobService() {
    }

    Intent uploadService;
    String TAG = "CRL_JobService";

    @Override
    public void onCreate() {
        super.onCreate();

        uploadService = new Intent(getApplicationContext(), UploadService.class);

    }

    @Override
    public boolean onStartJob(JobParameters params) {

        Log.i(TAG, "Starting Upload Job " + params.getJobId());

        String finishedPath = String.valueOf(getExternalFilesDir("Finished"));
        File finishedFolder = new File(finishedPath);
        if (finishedFolder.listFiles().length > 0) {

            Log.i(TAG, "No files to upload. Abandon ship!");

            startService(uploadService);

            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean onStopJob(JobParameters params) {

        Log.i(TAG, "Stopping Upload Job " + params.getJobId());

        stopService(uploadService);

        return false;
    }

}
