package uk.ac.nottingham.eaxtp1.CradleRideLogger;

import android.app.IntentService;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.moving;

public class MovingService extends IntentService {
    public MovingService() { super("MovingService");
    }

    // Class to move files after recording to a "Finished" folder. It may be better practice to
    // log to a single folder and then keep a database of files uploaded, but this way seems
    // foolproof. I started off as a complete rookie; cut me some slack.

    String mainPath, folderPath, finishedPath;
    String TAG = "CRL_MovingService";

    @Override
    public void onCreate() {
        super.onCreate();

        mainPath = String.valueOf(getExternalFilesDir(""));
        folderPath = mainPath + "/Recording";
        finishedPath = mainPath + "/Finished";

//        Ensures there's a folder to move the recorded files to.
        File finishedDirectory = new File(finishedPath);
        if (!finishedDirectory.exists()) {
            finishedDirectory.mkdir();
        }

//        moveFiles(folderPath, finishedPath);

    }

    @Override
    protected void onHandleIntent(Intent intent) {
        moveFiles(folderPath, finishedPath);
    }

    public void moveFiles(String oldPath, String newPath) {

        moving = true;

        InputStream in;
        OutputStream out;
        byte[] buffer = new byte[2048];

        File oldFolder = new File(oldPath);

        File[] fileList = oldFolder.listFiles();

        for (File file : fileList) {

            int filesRemaining = oldFolder.listFiles().length;
            if (filesRemaining == 0) {
                return;
            }

            try {

                in = new FileInputStream(file);
                String fileName = file.getPath();
                fileName = fileName.substring(file.getParent().length());
                out = new FileOutputStream(newPath + "/" + fileName);

                int read;
                while((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0 ,read);
                }

                in.close();

                out.flush();
                out.close();

                new File(String.valueOf(file)).delete();

            } catch (IOException e) {
                e.printStackTrace();
                return;
            }

        }

        moving = false;

        sendJob();

    }

    // Send an upload job to try and get the files just recorded to upload, rather than wait for
    // the period upload job.
    public void sendJob() {
        int jobInt = getResources().getInteger(R.integer.postRecordJobID);
        ComponentName jobName = new ComponentName(this, UploadJobService.class);
        JobInfo newUploadJob = new JobInfo.Builder(jobInt,jobName)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED)     // Only execute on Wi-Fi
                .build();

//        Schedule job:
        JobScheduler js = (JobScheduler) getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (js != null) {
            js.schedule(newUploadJob);
        }

        Log.i(TAG, "New upload job prepared.");
    }

}