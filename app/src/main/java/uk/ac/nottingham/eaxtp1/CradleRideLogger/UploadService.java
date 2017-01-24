package uk.ac.nottingham.eaxtp1.CradleRideLogger;

import android.app.IntentService;
import android.app.NotificationManager;
import android.content.Intent;
import android.support.v7.app.NotificationCompat;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import static uk.ac.nottingham.eaxtp1.CradleRideLogger.CompressionService.movedPath;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.CompressionService.zipPath;

public class UploadService extends IntentService {

    public UploadService() {
        super("UploadService");
    }

    NotificationCompat.Builder mBuilder;

    int jobNumber;

    URL url;
    String urlString = "http://optics.eee.nottingham.ac.uk/~tp/upload.php";

    String uploadFilePath, fileName, parse;

    @Override
    public void onCreate() {
        super.onCreate();

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

//        Toast.makeText(this, "Service is stopped!", Toast.LENGTH_SHORT).show();

    }

    @Override
    protected void onHandleIntent(Intent intent) {

        try {
            url = new URL(urlString);
        } catch (Exception e) {
            e.printStackTrace();
        }

        jobNumber = intent.getIntExtra("jobNumber", 1);

        mBuilder =
                (NotificationCompat.Builder) new NotificationCompat.Builder(this)
                        .setSmallIcon(R.drawable.hand_icon)
                        .setContentTitle("CradleRide Logger")
                        .setContentText("Files have been uploaded.");

        File sourceFolder = new File(zipPath);
        int sourceLength = sourceFolder.getParent().length();
        sourceLength = sourceLength + 7;

        File[] fileList = sourceFolder.listFiles();

        for (File file : fileList) {

            uploadFilePath = file.getAbsolutePath();
            fileName = uploadFilePath.substring(sourceLength);
            parse = fileName.substring(0, fileName.length() - 4) + "/zip";

            try {

                OkHttpClient okHttpClient = new OkHttpClient();
                File fileToUpload = new File(uploadFilePath);

                RequestBody requestBody = new MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("fileToUpload", fileName, RequestBody.create(MediaType.parse(parse), fileToUpload))

                        .build();

                Request request = new Request.Builder().url(url).post(requestBody).build();

                Response response = okHttpClient.newCall(request).execute();



                if (!response.isSuccessful()) {
                    throw new IOException("Unexpected code " + response);
                }

                // Sets an ID for the notification
                int mNotificationId = 1;
// Gets an instance of the NotificationManager service
                NotificationManager mNotifyMgr =
                        (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
// Builds the notification and issues it.
                mNotifyMgr.notify(mNotificationId, mBuilder.build());

            } catch (IOException e) {
                e.printStackTrace();
            }

            moveFile(fileName);
        }




        mBuilder =
                (NotificationCompat.Builder) new NotificationCompat.Builder(this)
                        .setSmallIcon(R.drawable.hand_icon)
                        .setContentTitle("The service works")
                        .setContentText("This is proof. number " + String.valueOf(jobNumber));




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

}
