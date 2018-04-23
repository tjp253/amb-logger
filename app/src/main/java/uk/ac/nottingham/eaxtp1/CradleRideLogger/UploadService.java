package uk.ac.nottingham.eaxtp1.CradleRideLogger;

import android.annotation.SuppressLint;
import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
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
import java.net.SocketException;
import java.net.URL;
import java.net.UnknownHostException;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.foreID;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.moving;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.NetworkReceiver.wifiConnected;

@SuppressWarnings("ResultOfMethodCallIgnored")
public class UploadService extends IntentService {

    public UploadService() {
        super("UploadService");
    }

    Notification.Builder mBuilder, mBuilder2, mBuilder3;
    static NotificationManager nm1, nm2, nm3;
    int id1 = 2, id2 = 3, id3 = 4;

    URL url;
    String urlString = "https://optics.eee.nottingham.ac.uk/~tp/upload.php";

    String TAG = "CRL_UploadService";

    String mainPath, finishedPath, movedPath, uploadFilePath, fileName, parse, oversizedPath, failedPath;

    int uploadFileCount = 0, oversizedFileCount = 0, failedFileCount = 0;

    int filesLeft;
    ComponentName myComponent;
    int jobID = 253;
    boolean jobSent;

    @Override
    public void onCreate() {
        super.onCreate();

        Log.i(TAG, "Upload Service started.");
        myComponent = new ComponentName(this, UploadJobService.class);

        mainPath = String.valueOf(getExternalFilesDir(""));
        finishedPath = mainPath + "/Finished";
        movedPath = mainPath + "/Uploaded";
        oversizedPath = mainPath + "/Oversized";
        failedPath = mainPath + "/FailedUploads";

        if (nm1 != null) {
            nm1.cancel(id1);
        }
        if (nm2 != null) {
            nm2.cancel(id2);
        }
        if (nm3 != null) {
            nm3.cancel(id3);
        }

        Notification notification = new Notification.Builder(this)
                .setSmallIcon(R.drawable.ambulance_symb)
                .setContentTitle(getString(R.string.app_name))
                .setContentText("Uploading files.").build();

        startForeground(foreID, notification);

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        Log.i(TAG, "Upload Service being destroyed.");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        handleUploads();
    }
    
    public void handleUploads() {

        if (moving) {
            try {
                Thread.sleep(5000);     // Wait for 5 seconds to finish moving files. Rather than kill straight away.
            } catch (Exception e) {
                Log.i(TAG, "Doesn't like sleeping..");
                return;
            }
            if (moving) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    buildJob();             // If moving is taking AGES, generate job to upload later.
                }
                return;
            }
        }

        File finishedFolder = new File(finishedPath);
        if (finishedFolder.listFiles().length == 0) {
            Log.i(TAG, "Files are already uploaded. Abandon ship!");
            return;
        }

        if (!wifiConnected) {
            return;
        }

        try {
            url = new URL(urlString);
        } catch (Exception e) {
            e.printStackTrace();
        }

        File sourceFolder = new File(finishedPath);
        int sourceLength = sourceFolder.getParent().length();
        sourceLength = sourceLength + 9;

        File[] fileList = sourceFolder.listFiles();
        filesLeft = fileList.length;

        for (File file : fileList) {

            if (wifiConnected) {

                uploadFilePath = file.getAbsolutePath();
                fileName = uploadFilePath.substring(sourceLength);
                parse = fileName.substring(0, fileName.length() - 2) + "/gz";

                try {

                    OkHttpClient okHttpClient = new OkHttpClient();
                    File fileToUpload = new File(uploadFilePath);

                    RequestBody requestBody = new MultipartBody.Builder()
                            .setType(MultipartBody.FORM)
                            .addFormDataPart("fileToUpload", fileName,
                                    RequestBody.create(MediaType.parse(parse), fileToUpload))

                            .build();

                    Request request = new Request.Builder().url(url).post(requestBody).build();
                    Response response;
                    try {
                        response = okHttpClient.newCall(request).execute();
                    } catch (UnknownHostException uhe) {
//                        failedFileCount++;
                        Log.i(TAG, "Host Connection Lost.");
                        throw new IOException("UnknownHostException - Upload connection failed.");
                    } catch (SocketException se) {
                        throw new IOException("Socket Exception");
                    }

                    switch (response.code()) {
                        case 900:
                            moveOversized(fileName);
                            oversizedFileCount++;
                            fileName = null;
                            Log.i(TAG, "File too large to upload.");
                            break;
                        case 901:
                            Log.i(TAG, "File already uploaded.");
                            break;
                        case 910:
                            uploadFileCount++;
                            break;
                        default:
                            fileName = null;
                            failedFileCount++;
                            break;
                    }

                } catch (IOException e) {
                    e.printStackTrace();
                    notificationSender();
                    return;
                }

            if (fileName != null) {
                moveFile(fileName);
            }

                filesLeft--;
            }

//      Displays notification once the last file has been uploaded
            if (filesLeft == 0) {
                notificationSender();
            }

        }

    }

    public void notificationSender() {
        if (uploadFileCount > 0) {
            uploadNotification();
        }

        if (oversizedFileCount > 0) {
            oversizedNotification();
        }

        if (failedFileCount > 0) {
            failedNotification();
        }
    }

    public void uploadNotification() {
        String uText;
        if (uploadFileCount == 1) {
            uText = uploadFileCount + " file uploaded.";
        } else {
            uText = uploadFileCount + " files uploaded.";
        }

        uploadFileCount = 0;

        mBuilder = new Notification.Builder(this)
                .setSmallIcon(R.drawable.upload_symb)
                .setContentTitle("CradleRide Logger")
                .setContentText(uText);

        nm1 = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm1 != null) {
            nm1.notify(id1, mBuilder.build());
        }
    }

    public void oversizedNotification() {
        String oText;
        if (oversizedFileCount == 1) {
            oText = oversizedFileCount + " file too large to upload.";
        } else {
            oText = oversizedFileCount + " files too large to upload.";
        }

        oversizedFileCount = 0;

        mBuilder2 = new Notification.Builder(this)
                .setSmallIcon(R.drawable.oversize_symb)
                .setContentTitle("CradleRide Logger")
                .setContentText(oText);

        nm2 = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm2 != null) {
            nm2.notify(id2, mBuilder2.build());
        }
    }

    private void failedNotification() {
        String oText;
        if (failedFileCount == 1) {
            oText = failedFileCount + " file failed to upload.\nPlease check the Finished folder.";
        } else {
            oText = failedFileCount + " files too large to upload.\nPlease check the Finished folder.";
        }

        failedFileCount = 0;

        mBuilder3 = new Notification.Builder(this)
                .setSmallIcon(R.drawable.oversize_symb)
                .setContentTitle("CradleRide Logger")
                .setContentText(oText);

        nm3 = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm3 != null) {
            nm3.notify(id3, mBuilder3.build());
        }
    }

    private void moveFile(String fileToMove) {

        InputStream in;
        OutputStream out;

        try {

            //create output directory if it doesn't exist
            File dir = new File (movedPath);
            if (!dir.exists())
            {
                dir.mkdirs();
            }

            in = new FileInputStream(finishedPath + fileToMove);
            out = new FileOutputStream(movedPath + fileToMove);

            byte[] buffer = new byte[1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            in.close();

            // write the output file
            out.flush();
            out.close();

            // delete the original file
            new File(finishedPath + fileToMove).delete();


        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public void moveOversized(String oversizeFile) {

        InputStream in;
        OutputStream out;

        try {

            //create output directory if it doesn't exist
            File dir = new File (oversizedPath);
            if (!dir.exists())
            {
                dir.mkdirs();
            }


            in = new FileInputStream(finishedPath + oversizeFile);
            out = new FileOutputStream(oversizedPath + oversizeFile);

            byte[] buffer = new byte[1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            in.close();

            // write the output file
            out.flush();
            out.close();

            // delete the original file
            new File(finishedPath + oversizeFile).delete();


        } catch (Exception e) {
            e.printStackTrace();
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
