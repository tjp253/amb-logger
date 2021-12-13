package uk.ac.nottingham.AmbLogger.FileHandling;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import java.io.File;

import static uk.ac.nottingham.AmbLogger.MainActivity.recording;

import uk.ac.nottingham.AmbLogger.R;

public class UploadJobService extends JobService {
    public UploadJobService() {}

    // If the phone is not connected to wifi when it wants to upload, a "job" is created. This
    // service handles that job when the criteria are met to begin the upload.

    JobParameters parameters;

    // Boolean to tell UploadService it can go ahead with uploading files. An extra, possibly
    // unnecessary, fail-safe to prevent using mobile data.
    static boolean uploading;

    int numOfFiles() {

        String finishedPath = String.valueOf(getExternalFilesDir(getApplicationContext().getResources().getString(R.string.fol_fin)));
        File finishedFolder = new File(finishedPath);

        if (!finishedFolder.isDirectory()) {
            return 0; // Folder not a directory, so no files
        }
        File[] fileList = finishedFolder.listFiles();
        if (fileList == null) {
            return 0; // Possible error thrown, so skip uploads for now
        }
        return fileList.length;
    }

    @Override
    public boolean onStartJob(JobParameters params) {

        // If there are files to upload and the app is not currently recording data start uploading.
        if (!recording && numOfFiles() > 0) {

            registerReceiver(receiver,new IntentFilter(uploadFilter));

            // Move the JobParameters out of the method so the BroadcastReceiver can handle
            // 'jobFinished' and possibly reschedule the upload job.
            parameters = params;

            uploading = true;

            Intent uploadService = new Intent(getApplicationContext(), UploadService.class);
            startService(uploadService);

            return true;
        }

        return false;
    }

    @Override
    public boolean onStopJob(JobParameters params) {

        uploading = false;

        unregisterReceiver(receiver);

        // If onStopJob is called, it means uploading has been interrupted. Therefore, it needs
        // to be restarted again when possible.
        return true;
    }

    static String uploadSuccess = "UploadResponse", uploadFilter = "UploadService";

    // This receives communication from the UploadService, feeding back whether all files were
    // uploaded or not. If files remain to be uploaded, the built-in 'jobFinished' method
    // reschedules the job for the next time parameters are met (I think) rather than waiting for
    // the regular period.
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            boolean needsReschedule = !intent.getBooleanExtra(uploadSuccess,false);
            jobFinished(parameters, needsReschedule);
            unregisterReceiver(receiver);
        }
    };

}
