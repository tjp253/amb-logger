package uk.ac.nottingham.eaxtp1.CradleRideLogger;

import android.app.IntentService;
import android.content.Intent;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.moving;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.WifiReceiver.wifiConnected;

@SuppressWarnings("ResultOfMethodCallIgnored")
public class MovingService extends IntentService {
    public MovingService() { super("MovingService");
    }

    String mainPath, folderPath, zipPath;

    @Override
    public void onCreate() {
        super.onCreate();

        mainPath = String.valueOf(getExternalFilesDir(""));
        folderPath = mainPath + "/Recording";
        zipPath = mainPath + "/Finished";

//        Ensures there's a folder to move the recorded files to.
        File zipDirectory = new File(zipPath);
        if (!zipDirectory.exists()) {
            zipDirectory.mkdir();
        }

        moveFiles(folderPath, zipPath);

    }

    @Override
    protected void onHandleIntent(Intent intent) {
        moveFiles(folderPath, zipPath);
    }

    public boolean moveFiles(String oldPath, String newPath) {

        moving = true;

        InputStream in;
        OutputStream out;
        byte[] buffer = new byte[2048];

        File oldFolder = new File(oldPath);

        File[] fileList = oldFolder.listFiles();

        for (File file : fileList) {

            int filesRemaining = oldFolder.listFiles().length;
            if (filesRemaining == 0) {
                return true;
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
//                in = null;

                out.flush();
                out.close();
//                out = null;

                new File(String.valueOf(file)).delete();

            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }

        }

        moving = false;

        if (wifiConnected) {
            Intent uploadService = new Intent(this, UploadService.class);
            this.startService(uploadService);
        }

        return true;
    }

}