package uk.ac.nottingham.eaxtp1.CradleRideLogger;

import android.app.IntentService;
import android.app.Notification;
import android.content.Intent;
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
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.moving;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.UploadJobService.uploadFilter;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.UploadJobService.uploading;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.UploadJobService.uploadSuccess;

@SuppressWarnings("ResultOfMethodCallIgnored")
public class UploadService extends IntentService {

    public UploadService() {
        super("UploadService");
    }

    // Securely uploads the recorded files to the Optics server.

    NotificationUtilities notUtils;

    URL url;

    String TAG = "CRL_UploadService";

    String mainPath, finishedPath, movedPath, uploadFilePath, fileName, parse, oversizedPath, failedPath;

    File sourceFolder; // Have the sourceFolder available to the whole class to enable
    // 'NotificationSender' to count the amount of files left to be uploaded.

    int uploadFileCount = 0, oversizedFileCount = 0, failedFileCount = 0;

    int filesLeft;

    @Override
    public void onCreate() {
        super.onCreate();

        Log.i(TAG, "Upload Service started.");

        mainPath = String.valueOf(getExternalFilesDir(""));
        finishedPath = mainPath + "/Finished";
        movedPath = mainPath + "/Uploaded";
        oversizedPath = mainPath + "/Oversized";
        failedPath = mainPath + "/FailedUploads";

        notUtils = new NotificationUtilities(this);
        // Cancel uploaded / oversized / failed notifications when starting a new lot of uploads
        notUtils.getManager().cancel(getResources().getInteger(R.integer.uploadedID));
        notUtils.getManager().cancel(getResources().getInteger(R.integer.oversizedID));
        notUtils.getManager().cancel(getResources().getInteger(R.integer.failedUploadID));

        Notification.Builder notBuild = notUtils.getUploadingNotification();
        startForeground(getResources().getInteger(R.integer.foregroundID),notBuild.build());
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
                Thread.sleep(5000);   // Wait for 5 seconds to finish moving files. Rather than kill straight away.
            } catch (Exception e) {
                Log.i(TAG, "Doesn't like sleeping..");
                sendBroadcast(false);
                return;
            }
            if (moving) {
                sendBroadcast(false);
                return;
            }
        }

        sourceFolder = new File(finishedPath);
        File[] fileList = sourceFolder.listFiles();
        filesLeft = fileList.length;
        if (filesLeft == 0) {
            Log.i(TAG, "Files are already uploaded. Abandon ship!");
            sendBroadcast(true);
            return;
        }

        try {
            url = new URL(getResources().getString(R.string.uploadURL));
        } catch (Exception e) {
            e.printStackTrace();
        }

        int sourceLength = sourceFolder.getParent().length() + 9;

        for (File file : fileList) { // For each file in the 'finished' folder

            if (uploading) {

                uploadFilePath = file.getAbsolutePath();
                fileName = uploadFilePath.substring(sourceLength);
                parse = fileName.substring(0, fileName.length() - 2) + "/gz";

                try {

                    OkHttpClient okHttpClient = new OkHttpClient.Builder()
                            .writeTimeout((long) 60, TimeUnit.SECONDS)
                            .build();
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
                        Log.i(TAG, "Host Connection Lost.");
                        throw new IOException("UnknownHostException - Upload connection failed.");
                    } catch (SocketException se) {
                        throw new IOException("Socket Exception");
                    }

                    // Receives the PHP response code (programmed myself) to feedback any
                    // problems / tell the app this file was uploaded successfully.
                    // Using IF-ELSE instead of SWITCH to enable use of resource INTs
                    if (response.code() == getResources().getInteger(R.integer.successfullyUp)) {
                        uploadFileCount++;

                    } else if (response.code() == getResources().getInteger(R.integer
                                .oversizedUp)) {
                        moveOversized(fileName);
                        oversizedFileCount++;
                        fileName = null;
                        Log.i(TAG, "File too large to upload.");

                    } else if (response.code() == getResources().getInteger(R.integer.alreadyUp)) {
                        Log.i(TAG, "File already uploaded.");

                    } else {
                        fileName = null;
                        failedFileCount++;
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
            } else {
                sendBroadcast(sourceFolder.listFiles().length == 0);
            }

//      Displays notification once the last file has been uploaded
            if (filesLeft == 0) {
                notificationSender();
            }

        }

    }

    public void notificationSender() {

        sendBroadcast(sourceFolder.listFiles().length == 0);

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

        Notification.Builder notBuild = notUtils.getUploadedNotification(true, uText);
        notUtils.getManager().notify(getResources().getInteger(R.integer.uploadedID),notBuild.build());
    }

    public void oversizedNotification() {
        String oText;
        if (oversizedFileCount == 1) {
            oText = oversizedFileCount + " file too large to upload.";
        } else {
            oText = oversizedFileCount + " files too large to upload.";
        }

        oversizedFileCount = 0;

        Notification.Builder notBuild = notUtils.getUploadedNotification(false, oText);
        notUtils.getManager().notify(getResources().getInteger(R.integer.oversizedID),notBuild.build());
    }

    private void failedNotification() {
        String fText;
        if (failedFileCount == 1) {
            fText = failedFileCount + " file failed to upload.\nPlease check the Finished folder.";
        } else {
            fText = failedFileCount + " files too large to upload.\nPlease check the Finished folder.";
        }

        failedFileCount = 0;

        Notification.Builder notBuild = notUtils.getUploadedNotification(false, fText);
        notUtils.getManager().notify(getResources().getInteger(R.integer.failedUploadID),notBuild.build());
    }

    private void moveFile(String fileToMove) { // Moves the uploaded files to the 'uploaded' folder

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

    private void sendBroadcast(boolean successfullyUploaded) {
        Intent intent = new Intent(uploadFilter);
        intent.putExtra(uploadSuccess, successfullyUploaded);
        sendBroadcast(intent);
    }

}