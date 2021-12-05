package uk.ac.nottingham.eaxtp1.CradleRideLogger.FileHandling;

import android.app.IntentService;
import android.content.Intent;
import android.content.res.Resources;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import uk.ac.nottingham.eaxtp1.CradleRideLogger.BuildConfig;
import uk.ac.nottingham.eaxtp1.CradleRideLogger.R;
import uk.ac.nottingham.eaxtp1.CradleRideLogger.Utilities.FileCheckUtilities;
import uk.ac.nottingham.eaxtp1.CradleRideLogger.Utilities.JobUtilities;
import uk.ac.nottingham.eaxtp1.CradleRideLogger.Utilities.NotificationUtilities;

import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.moving;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.versionNum;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.FileHandling.UploadJobService.uploadFilter;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.FileHandling.UploadJobService.uploading;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.FileHandling.UploadJobService.uploadSuccess;

@SuppressWarnings("ResultOfMethodCallIgnored")
public class UploadService extends IntentService {

    public UploadService() {
        super("UploadService");
    }

    // Securely uploads the recorded files to the Optics server.

    NotificationUtilities notUtils;

    Resources res;

    URL url;

    String mainPath, finishedPath, movedPath, uploadFilePath, fileName, parse, failedPath, fullFilePath;

    File sourceFolder; // Have the sourceFolder available to the whole class to enable
    // 'NotificationSender' to count the amount of files left to be uploaded.

    int uploadFileCount = 0, failedFileCount = 0, uploadLimit;

    long fullFileLength;

    @Override
    public void onCreate() {
        super.onCreate();
        res = getResources();

        uploadLimit = res.getInteger(R.integer.limit_upload);

        mainPath = String.valueOf(getExternalFilesDir(""));
        finishedPath = mainPath + "/" + res.getString(R.string.fol_fin) + "/";
        movedPath = mainPath + "/" + res.getString(R.string.fol_up) + "/";
        failedPath = mainPath + "/FailedUploads/";

        notUtils = new NotificationUtilities(this);
        // Cancel uploaded / oversized / failed notifications when starting a new lot of uploads
        notUtils.cancelNotifications(false);

        startForeground(notUtils.FOREGROUND_INT,notUtils.getUploadingNotification().build());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent.hasExtra("Resend")) {
            uploadFile(new File(intent.getStringExtra("Resend")));
        } else {
            handleUploads();
        }
    }

    private URL getURL() {
        if (url == null) {
            try {
                url = new URL(res.getString(R.string.uploadURL));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return url;
    }
    
    public void handleUploads() {

        if (moving) {
            try {
                Thread.sleep(5000);   // Wait for 5 seconds to finish moving files. Rather than kill straight away.
            } catch (Exception e) {
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
        if (fileList.length == 0) {
            return; // nothing to upload. should not reach here, but catch anyway
        }

        for (File file : fileList) { // For each file in the 'finished' folder

            if (uploading) {

                fullFileLength = file.length();
                if (fullFileLength > uploadLimit) {

                    if (!uploadInChunks(file)) {
                        return;
                    }

                } else {

                    if (!uploadFile(file)) {
                        return;
                    }

                }

                moveFile(file.getName());

            } else {
                sendBroadcast(sourceFolder.listFiles().length == 0);
            }

        }

//      Displays notification once the last file has been uploaded
        notificationSender();

        if (BuildConfig.AMB_MODE) {
            // Update the server XML for NTT phones.
            new FileCheckUtilities(this).sendStorageUpdate();
        }

        new JobUtilities(this).scheduleDelete();

    }

    public boolean uploadInChunks(File file) {

        fullFilePath = file.getAbsolutePath();

        try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(file))) {

            int chunks = (int) fullFileLength / uploadLimit;
            byte[] buffer = new byte[uploadLimit];
            int chunk = 0;
            while (in.read(buffer) != -1) {

                // create a new file of each chunk and send to upload
                File chunkFile = new File(formChunkName(chunk, chunk == chunks));
                FileOutputStream out = new FileOutputStream(chunkFile);
                out.write(buffer);
                out.flush();
                out.close();
                if (!uploadFile(chunkFile)) {
                    return false;
                }
                chunkFile.delete();
                chunk++;
                if (chunk == chunks) {// reset the buffer in case final chunk is small
                    buffer = new byte[(int) fullFileLength - (chunks * uploadLimit)];
                }

            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return true;
    }

    public String formChunkName(int chunk, boolean finalPart) {
        String toInsert = String.format(Locale.UK, res.getString(R.string.chunk_formatter), chunk);
        if (finalPart) {
            toInsert += res.getString(R.string.suffix);
        }
        return fullFilePath.replace(
                res.getString(R.string.file_type),
                toInsert + res.getString(R.string.file_type)
        );
    }

    public boolean uploadFile(File file) {

        uploadFilePath = file.getAbsolutePath();
        fileName = file.getName();
        parse = fileName.substring(0, fileName.length() - 2) + "/gz";

        try {

            OkHttpClient okHttpClient = new OkHttpClient.Builder()
                    .writeTimeout( 60, TimeUnit.SECONDS)
                    .build();
            File fileToUpload = new File(uploadFilePath);

            RequestBody requestBody = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart(
                            res.getString(R.string.dataPartFilename), fileName,
                            RequestBody.create(fileToUpload, MediaType.parse(parse))
                    )
                    .addFormDataPart("appVersion", versionNum) // to aid future processing
                    .build();

            Request request = new Request.Builder()
                    .url(getURL())
                    .post(requestBody)
                    .build();
            Response response;
            try {
                response = okHttpClient.newCall(request).execute();
            } catch (UnknownHostException uhe) {
                throw new IOException("UnknownHostException - Upload connection failed.");
            } catch (SocketException se) {
                throw new IOException("Socket Exception");
            }

            // Receives the PHP response code (programmed myself) to feedback any
            // problems / tell the app this file was uploaded successfully.
            // Using IF-ELSE instead of SWITCH to enable use of resource INTs
            if (response.code() == res.getInteger(R.integer.successfullyUp)) {
                uploadFileCount++;

            } else if (response.code() == res.getInteger(R.integer
                    .oversizedUp)) {

                // upload is greater than 10 MB. SHOULD NOT HAPPEN NOW.
                return false;

            } else if (response.code() == res.getInteger(R.integer.alreadyUp)) {
                // File already uploaded.

            } else {
                fileName = null;
                failedFileCount++;
            }

        } catch (IOException e) {
            e.printStackTrace();
            notificationSender();
            return false;
        }

        return true;
    }

    public void notificationSender() {

        sendBroadcast(sourceFolder.listFiles().length == 0);

        if (uploadFileCount > 0) {
            uploadNotification();
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

        notUtils.getManager()
                .notify(notUtils.UPLOADED_INT,
                        notUtils.getUploadedNotification(true, uText)
                                .build());
    }

    private void failedNotification() {
        String fText;
        if (failedFileCount == 1) {
            fText = failedFileCount + " file failed to upload.\nPlease check the Finished folder.";
        } else {
            fText = failedFileCount + " files too large to upload.\nPlease check the Finished folder.";
        }

        failedFileCount = 0;

        notUtils.getManager()
                .notify(notUtils.FAILED_INT,
                        notUtils.getUploadedNotification(false, fText)
                                .build());
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

    private void sendBroadcast(boolean successfullyUploaded) {
        Intent intent = new Intent(uploadFilter);
        intent.putExtra(uploadSuccess, successfullyUploaded);
        sendBroadcast(intent);
    }

}