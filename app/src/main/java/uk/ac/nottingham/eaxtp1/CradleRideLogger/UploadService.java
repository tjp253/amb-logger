package uk.ac.nottingham.eaxtp1.CradleRideLogger;

import android.app.IntentService;
import android.app.NotificationManager;
import android.content.Intent;
import android.support.v7.app.NotificationCompat;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
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

import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.compressing;

@SuppressWarnings("ResultOfMethodCallIgnored")
public class UploadService extends IntentService {

    public UploadService() {
        super("UploadService");
    }

    NotificationCompat.Builder mBuilder;

    int jobNumber;

    URL url;
    String urlString = "http://optics.eee.nottingham.ac.uk/~tp/upload.php";


    String mainPath, zipPath, movedPath, uploadFilePath, fileName, parse;

    @Override
    public void onCreate() {
        super.onCreate();

        while (compressing) {
//            DO NOTHING!

            Toast.makeText(this, "Not uploading as compressing", Toast.LENGTH_SHORT).show();

            onDestroy();
        }

        mainPath = String.valueOf(getExternalFilesDir(""));
        zipPath = mainPath + "/Zipped";
        movedPath = mainPath + "/Uploaded";

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

        try {
            url = new URL(urlString);
        } catch (Exception e) {
            e.printStackTrace();
        }

        jobNumber = intent.getIntExtra("jobNumber", 1);

        mBuilder =
                (NotificationCompat.Builder) new NotificationCompat.Builder(this)
                        .setSmallIcon(R.drawable.upload_symb)
                        .setContentTitle("CradleRide Logger")
                        .setContentText("Files have been uploaded.");

        File sourceFolder = new File(zipPath);
        int sourceLength = sourceFolder.getParent().length();
        sourceLength = sourceLength + 7;

        File[] fileList = sourceFolder.listFiles();
        int filesLeft = fileList.length;

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

                filesLeft = filesLeft - 1;

//                Displays notification once the last file has been uploaded
                if (filesLeft == 0) {
                    int mNotificationId = 1;
                    NotificationManager mNotifyMgr =
                            (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                    mNotifyMgr.notify(mNotificationId, mBuilder.build());
                }



            } catch (IOException e) {
                e.printStackTrace();
            }

            moveFile(fileName);
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

}
