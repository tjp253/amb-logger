package uk.ac.nottingham.eaxtp1.CradleRideLogger;

import android.annotation.SuppressLint;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.os.Build;

import java.util.List;
import java.util.concurrent.TimeUnit;

@SuppressLint("NewApi") // This is checked for with the boolean 'newAPI'.
public class JobUtilities extends ContextWrapper {

    // Class to handle all job scheduler duties. This class establishes the JobScheduler and each
    // job.

    // Check if the Android version supports notification channels
    final boolean newAPI = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N;

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

        if (scheduler == null) {
            scheduler = getScheduler();
        }

        JobInfo job = null;

        if (newAPI) {
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
        if (job == null) {
            return true;
        }

        // Old version of job executed once a week, when idle. Now removed the idle
        // requirement and increased the frequency to once a day.
        return job.isRequireDeviceIdle();
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

        return new JobInfo.Builder(DELETING_JOB_INT, jobName)
                .setPeriodic(TimeUnit.DAYS.toMillis(1)) // Schedule it for once a day
                .setPersisted(true)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED) // Only execute on Wi-Fi
                .build();
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
