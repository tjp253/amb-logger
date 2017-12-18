package uk.ac.nottingham.eaxtp1.CradleRideLogger;

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.ComponentName;
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

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.moving;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.WifiReceiver.wifiConnected;

@SuppressWarnings("ResultOfMethodCallIgnored")
public class UploadService extends IntentService {

    public UploadService() {
        super("UploadService");
    }

    Notification.Builder mBuilder, mBuilder2, mBuilder3;

    int jobNumber;

    URL url;
    String urlString = "https://optics.eee.nottingham.ac.uk/~tp/upload.php";

    String TAG = "Upload Service";

    String mainPath, recordPath, finishedPath, movedPath, uploadFilePath, fileName, parse, oversizedPath, failedPath;

    int uploadFileCount = 0, oversizedFileCount = 0, failedFileCount = 0;

    long uploadTime;
    boolean uploaded;

    int filesLeft;
    ComponentName myComponent;

    @Override
    public void onCreate() {
        super.onCreate();

        Log.i(TAG, "Upload Service started.");
        myComponent = new ComponentName(this, UploadJobService.class);

        mainPath = String.valueOf(getExternalFilesDir(""));
        recordPath = mainPath + "/Recording";
        finishedPath = mainPath + "/Finished";
        movedPath = mainPath + "/Uploaded";
        oversizedPath = mainPath + "/Oversized";
        failedPath = mainPath + "/FailedUploads";

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

        if (moving) {
            return;
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

        jobNumber = intent.getIntExtra("jobNumber", 1);

        File sourceFolder = new File(finishedPath);
        int sourceLength = sourceFolder.getParent().length();
        sourceLength = sourceLength + 9;

        File[] fileList = sourceFolder.listFiles();
        filesLeft = fileList.length;

        for (File file : fileList) {

            if (wifiConnected) {

                uploaded = false;

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
//                            throw new IOException("File too large to upload.");
                        case 901:
//                            uploadFileCount++;
                            Log.i(TAG, "File already uploaded.");
                            break;
//                            throw new IOException("File already uploaded.");
//                        case 902:
//                            moveFailed(fileName);
//                            failedFileCount++;
//                            fileName = null;
//                            throw new IOException("Upload failed.");
                        case 910:
                            uploadTime = System.currentTimeMillis();
                            uploaded = true;
                            uploadFileCount++;
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

        NotificationManager mNotifyMgr =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (mNotifyMgr != null) {
            mNotifyMgr.notify(2, mBuilder.build());
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

        NotificationManager mNotifyMgr2 =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (mNotifyMgr2 != null) {
            mNotifyMgr2.notify(3, mBuilder2.build());
        }
    }

    private void failedNotification() {
        String oText;
        if (failedFileCount == 1) {
            oText = failedFileCount + " file failed to upload.\nPlease check UploadFailed folder.";
        } else {
            oText = failedFileCount + " files too large to upload.\nPlease check UploadFailed folder.";
        }

        failedFileCount = 0;

        mBuilder3 = new Notification.Builder(this)
                .setSmallIcon(R.drawable.oversize_symb)
                .setContentTitle("CradleRide Logger")
                .setContentText(oText);

        NotificationManager mNotifyMgr3 =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (mNotifyMgr3 != null) {
            mNotifyMgr3.notify(4, mBuilder3.build());
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

//    Change this and the PHP for if the file is the wrong type? Though why would that be an issue???
//    public void moveFailed(String failedFile) {
//
//        InputStream in;
//        OutputStream out;
//
//        try {
//
//            //create output directory if it doesn't exist
//            File dir = new File (failedPath);
//            if (!dir.exists())
//            {
//                dir.mkdirs();
//            }
//
//
//            in = new FileInputStream(finishedPath + failedFile);
//            out = new FileOutputStream(failedPath + failedFile);
//
//            byte[] buffer = new byte[1024];
//            int read;
//            while ((read = in.read(buffer)) != -1) {
//                out.write(buffer, 0, read);
//            }
//            in.close();
//
//            // write the output file
//            out.flush();
//            out.close();
//
//            // delete the original file
//            new File(finishedPath + failedFile).delete();
//
//
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//    }

}
