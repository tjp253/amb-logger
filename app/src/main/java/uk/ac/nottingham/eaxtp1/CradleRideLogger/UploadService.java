package uk.ac.nottingham.eaxtp1.CradleRideLogger;

import android.app.IntentService;
import android.app.NotificationManager;
import android.content.Intent;
import android.support.v7.app.NotificationCompat;

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

    NotificationCompat.Builder mBuilder, mBuilder2;

    int jobNumber;

    URL url;
    String urlString = "http://optics.eee.nottingham.ac.uk/~tp/upload.php";


    String mainPath, recordPath, zipPath, movedPath, uploadFilePath, fileName, parse, oversizedPath;

    int uploadFileCount = 0, oversizedFileCount = 0;

    @Override
    public void onCreate() {
        super.onCreate();

        while (moving) {
            onDestroy();
        }

        mainPath = String.valueOf(getExternalFilesDir(""));
        recordPath = mainPath + "/Recording";
        zipPath = mainPath + "/Finished";
        movedPath = mainPath + "/Uploaded";
        oversizedPath = mainPath + "/Oversized";

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

        File recordFolder = new File(recordPath);
        if (recordFolder.listFiles().length > 0) {
            onDestroy();
        }
        if (!wifiConnected) {
            onDestroy();
        }

        try {
            url = new URL(urlString);
        } catch (Exception e) {
            e.printStackTrace();
        }

        jobNumber = intent.getIntExtra("jobNumber", 1);

        File sourceFolder = new File(zipPath);
        int sourceLength = sourceFolder.getParent().length();
        sourceLength = sourceLength + 9;

        File[] fileList = sourceFolder.listFiles();
        int filesLeft = fileList.length;

        for (File file : fileList) {

            uploadFilePath = file.getAbsolutePath();
            fileName = uploadFilePath.substring(sourceLength);
            parse = fileName.substring(0, fileName.length() - 2) + "/gz";

            try {

                OkHttpClient okHttpClient = new OkHttpClient();
                File fileToUpload = new File(uploadFilePath);

                RequestBody requestBody = new MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("fileToUpload", fileName, RequestBody.create(MediaType.parse(parse), fileToUpload))

                        .build();

                Request request = new Request.Builder().url(url).post(requestBody).build();
                Response response;
                try {
                    response = okHttpClient.newCall(request).execute();
                } catch (UnknownHostException uhe) {
                    throw new IOException("U H E");
                } catch (SocketException se) {
                    throw new IOException("S E");
                }

                if (response.code() == 900) {
                    moveOversized(fileName);
                    filesLeft = filesLeft - 1;
                    oversizedFileCount = oversizedFileCount + 1;
                    fileName = null;
                    throw new IOException("File too large to upload.");
                } else if (response.code() == 901) {
                    filesLeft = filesLeft - 1;
                    uploadFileCount = uploadFileCount + 1;
                    throw new IOException("File already uploaded.");
                }

                filesLeft = filesLeft - 1;
                uploadFileCount = uploadFileCount + 1;

            } catch (IOException e) {
                e.printStackTrace();
//                break;
            }

            if (fileName != null) {
                moveFile(fileName);
            }
        }

//      Displays notification once the last file has been uploaded
        if (filesLeft == 0) {
            if (uploadFileCount > 0) {
                String uText;
                if (uploadFileCount == 1) {
                    uText = uploadFileCount + " file uploaded.";
                } else {
                    uText = uploadFileCount + " files uploaded.";
                }

                mBuilder =
                        (NotificationCompat.Builder) new NotificationCompat.Builder(this)
                                .setSmallIcon(R.drawable.upload_symb)
                                .setContentTitle("CradleRide Logger")
                                .setContentText(uText);

                NotificationManager mNotifyMgr =
                        (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                mNotifyMgr.notify(1, mBuilder.build());
            }

            if (oversizedFileCount > 0) {
                String oText;
                if (oversizedFileCount == 1) {
                    oText = oversizedFileCount + " file too large to upload.";
                } else {
                    oText = oversizedFileCount + " files too large to upload.";
                }

                mBuilder2 =
                        (NotificationCompat.Builder) new NotificationCompat.Builder(this)
                                .setSmallIcon(R.drawable.oversize_symb)
                                .setContentTitle("CradleRide Logger")
                                .setContentText(oText);

                NotificationManager mNotifyMgr2 =
                        (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                mNotifyMgr2.notify(2, mBuilder2.build());
            }
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

            in = new FileInputStream(zipPath + fileToMove);
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
            new File(zipPath + fileToMove).delete();


        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public void
    moveOversized(String oversizeFile) {

        InputStream in;
        OutputStream out;

        try {

            //create output directory if it doesn't exist
            File dir = new File (oversizedPath);
            if (!dir.exists())
            {
                dir.mkdirs();
            }


            in = new FileInputStream(zipPath + oversizeFile);
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
            new File(zipPath + oversizeFile).delete();


        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
