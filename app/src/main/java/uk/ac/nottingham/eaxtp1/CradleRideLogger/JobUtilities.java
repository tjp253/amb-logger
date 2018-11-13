package uk.ac.nottingham.eaxtp1.CradleRideLogger;

import android.annotation.SuppressLint;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.os.Build;

import java.util.List;
import java.util.concurrent.TimeUnit;

@SuppressLint("NewApi") // This is checked for with the booleans 'nAPI' and 'oAPI'.
public class JobUtilities extends ContextWrapper {

    // Class to handle all job scheduler duties. This class establishes the JobScheduler and each
    // job.

    // Check if the Android version supports notification channels
    final boolean nAPI = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N,
            oAPI = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O;

    private JobScheduler scheduler;
    public final int DELETING_JOB_INT = getResources().getInteger(R.integer.deletingJobID),
            UPLOAD_JOB_INT = getResources().getInteger(R.integer.uploadJobID),
            WIFI_CHECK_JOB_INT = getResources().getInteger(R.integer.wifiJobID);

    public JobUtilities(Context context) {
        super(context);
    }

    JobScheduler getScheduler() {
        if (scheduler == null) {
            scheduler = (JobScheduler) getSystemService(Context.JOB_SCHEDULER_SERVICE);
        }
        return scheduler;
    }

    // Check if the job needs creating (if it does NOT exist)
    public boolean jobNeedsCreating(int jobID) {

        if (jobID == DELETING_JOB_INT) {
            SharedPreferences preferences = getSharedPreferences(getString(R.string.pref_main),MODE_PRIVATE);
            long currTime = System.currentTimeMillis(),
                    prevTime = preferences.getLong(getString(R.string.key_pref_delete_time),0),
                    weekly = TimeUnit.DAYS.toMillis(7);

            // Check if a week has passed since the last FileCheck job
            if ( currTime - prevTime < weekly) {
                return false;
            } else {
                // Store the latest Job Time
                preferences.edit().putLong(getString(R.string.key_pref_delete_time), currTime).apply();
            }
        }

        if (scheduler == null) {
            scheduler = getScheduler();
        }

        JobInfo job = null;

        if (nAPI) {
            job = scheduler.getPendingJob(jobID);

        } else {
            List<JobInfo> pendingJobs = scheduler.getAllPendingJobs();
            for (JobInfo pendingJob : pendingJobs) {
                if (pendingJob.getId() == jobID) {
                    job = pendingJob;
                    break;
                }
            }

        }

        // No job found that matches the JobID
        // Old version of job executed once a week, when idle. Now removed the idle
        // requirement and increased the frequency to once a day.
        return job == null || job.isRequireDeviceIdle() || job.isPeriodic();
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

        if (oAPI) {
            newJob.setRequiresBatteryNotLow(true);
        } else {
            newJob.setRequiresCharging(true);
        }

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
