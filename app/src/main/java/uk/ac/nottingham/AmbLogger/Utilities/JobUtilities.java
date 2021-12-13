package uk.ac.nottingham.AmbLogger.Utilities;

import android.annotation.SuppressLint;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.preference.PreferenceManager;

import java.io.File;
import java.util.concurrent.TimeUnit;

import uk.ac.nottingham.AmbLogger.FileHandling.DeletingJobService;
import uk.ac.nottingham.AmbLogger.R;
import uk.ac.nottingham.AmbLogger.Recording.WifiCheckService;
import uk.ac.nottingham.AmbLogger.FileHandling.UploadJobService;

@SuppressLint("NewApi") // This is checked for with the boolean 'nAPI'.
public class JobUtilities extends ContextWrapper {

    // Class to handle all job scheduler duties. This class establishes the JobScheduler and each
    // job.

    Resources res = getResources();

    private JobScheduler scheduler;
    public final int DELETING_JOB_INT = res.getInteger(R.integer.deletingJobID),
            UPLOAD_JOB_INT = res.getInteger(R.integer.uploadJobID),
            WIFI_CHECK_JOB_INT = res.getInteger(R.integer.wifiJobID);

    public JobUtilities(Context context) {
        super(context);
    }

    JobScheduler getScheduler() {
        if (scheduler == null) {
            scheduler = (JobScheduler) getSystemService(Context.JOB_SCHEDULER_SERVICE);
        }
        return scheduler;
    }

    public void checkFiles() {
        // Check if there are files available to upload, and schedule the job if need be.
        File finishedFolder = new File(String.valueOf(getExternalFilesDir("Finished")));
        File uploadedFolder = new File(String.valueOf(getExternalFilesDir("Uploaded")));
        File[] finishedList = finishedFolder.listFiles();
        File[] uploadedList = uploadedFolder.listFiles();
        if (finishedList != null && finishedList.length != 0) scheduleUpload();
        if (uploadedList != null && uploadedList.length != 0) scheduleDelete();
    }

    public void scheduleUpload() {
        getScheduler().schedule(uploadJob());
    }

    public void scheduleDelete() {
        if (jobNeedsCreating(DELETING_JOB_INT)) {
            getScheduler().schedule(deletingJob());
        }
    }

    public void scheduleWifi() {
        getScheduler().schedule(wifiJob());
    }

    // Check if the job needs creating (if it does NOT exist)
    public boolean jobNeedsCreating(int jobID) {

        if (jobID == DELETING_JOB_INT) {
            SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
            long currTime = System.currentTimeMillis(),
                    prevTime = pref.getLong(getString(R.string.key_pref_delete_time),0);

            // Check if a day has passed since the last FileCheck job
            if ( currTime - prevTime < TimeUnit.DAYS.toMillis(1)) {
                return false;
            }

            // Store the latest Job Time & tell app to create new job.
            pref.edit().putLong(getString(R.string.key_pref_delete_time), currTime).apply();
            return true;
        }

        if (scheduler == null) {
            scheduler = getScheduler();
        }

        JobInfo job = scheduler.getPendingJob(jobID);

        // No job found that matches the JobID
        // Old version of job executed once a week, when idle. Now removed the idle
        // requirement and increased the frequency to once a day.
        return job == null;
    }

    // Create job to upload files when connected to wifi
    public JobInfo uploadJob() {
        ComponentName jobName = new ComponentName(this, UploadJobService.class);

        return new JobInfo.Builder(UPLOAD_JOB_INT,jobName)
                .setPersisted(true)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED) // Only execute on Wi-Fi
                .build();
    }

    // Create deleting job for once a day, when connected to wifi
    public JobInfo deletingJob() {
        ComponentName jobName = new ComponentName(this, DeletingJobService.class);

        JobInfo.Builder newJob = new JobInfo.Builder(DELETING_JOB_INT, jobName)
                .setPersisted(true)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED); // Only execute on Wi-Fi

        return newJob.build();
    }

    // Create a simple job to tell GPS Service if wifi is connected or not (when checking if
    // stationary)
    public JobInfo wifiJob() {
        ComponentName jobName = new ComponentName(this, WifiCheckService.class);

        return new JobInfo.Builder(WIFI_CHECK_JOB_INT, jobName)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED) // Start on Wi-Fi
                .build();
    }
}
