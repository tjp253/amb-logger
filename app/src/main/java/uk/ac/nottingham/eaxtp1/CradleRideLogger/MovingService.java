package uk.ac.nottingham.eaxtp1.CradleRideLogger;

import android.annotation.SuppressLint;
import android.app.IntentService;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.moving;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.NetworkReceiver.wifiConnected;

public class MovingService extends IntentService {
    public MovingService() { super("MovingService");
    }

    String mainPath, folderPath, finishedPath;

    int jobID = 24;
    String TAG = "Moving Service";
    private ComponentName myComponent;
    boolean jobSent, sentUploadIntent;

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

        myComponent = new ComponentName(this, UploadJobService.class);

        moveFiles(folderPath, finishedPath);

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

        if (!sentUploadIntent) {
            if (wifiConnected) {
                Intent uploadService = new Intent(getApplicationContext(), UploadService.class);
                this.startService(uploadService);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                buildJob();
            }
            sentUploadIntent = true;
        }

    }

    @SuppressLint("NewApi")
    public void buildJob() {
        if (!jobSent) {
            JobInfo.Builder builder = new JobInfo.Builder(jobID++, myComponent)
                    .setMinimumLatency(60*1000)     // Wait for at least a minute before executing job.
                    .setPersisted(true)             // Keeps job in system after system reboot BUT NOT IF APP IS FORCE CLOSED
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED);     // Only execute on Wi-Fi
//                .setRequiresDeviceIdle(false);    // Don't upload while device being used (yes? no?)

//        Schedule job:
            JobScheduler js = (JobScheduler) getSystemService(Context.JOB_SCHEDULER_SERVICE);
            if (js != null) {
                js.schedule(builder.build());
            }

            Log.i(TAG, "Job " + jobID + " prepared.");

            jobSent = true;
        }
    }

}