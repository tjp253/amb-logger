package uk.ac.nottingham.eaxtp1.CradleRideLogger;

import android.app.IntentService;
import android.content.Intent;
import android.content.res.Resources;

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

    @Override
    public void onCreate() {
        super.onCreate();

        Resources res = getResources();

        mainPath = getExternalFilesDir("") + "/";
        folderPath = mainPath + res.getString(R.string.fol_rec);
        finishedPath = mainPath + res.getString(R.string.fol_fin);

//        Ensures there's a folder to move the recorded files to.
        File finishedDirectory = new File(finishedPath);
        if (!finishedDirectory.exists()) {
            finishedDirectory.mkdir();
        }

    }

    @Override
    protected void onHandleIntent(Intent intent) {
        moveFiles(folderPath, finishedPath);
    }

    public void moveFiles(String oldPath, String newPath) {

        moving = true;

        InputStream in;
        OutputStream out;
        byte[] buffer = new byte[2048]; // amount of bytes to read & write each pass
        int read;

        File oldFolder = new File(oldPath);

        File[] fileList = oldFolder.listFiles();

        if (BuildConfig.AMB_MODE) { // Combine the AMB metadata with the main file
            Resources res = getResources();
            for (File file : fileList) {
                if (file.getName().contains(res.getString(R.string.suffix_meta))) {
                    file.delete();
                    continue;
                }
                String nameMain = file.getAbsolutePath();
                String nameMeta = nameMain.replace(res.getString(R.string.suffix), res.getString(R.string.suffix_meta));
                // Define final name for combined parts
                String nameFull = nameMain.replace(res.getString(R.string.suffix), "");
                try {
                    out = new FileOutputStream(nameFull); // to write new file
                    for (String filename : new String[]{nameMeta, nameMain}) {
                        in = new FileInputStream(filename);
                        while ((read = in.read(buffer)) >= 0) {
                            out.write(buffer, 0, read);
                        }
                        in.close();
                    }
                    file.delete();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            fileList = oldFolder.listFiles();
        }

        for (File file : fileList) {

            int filesRemaining = oldFolder.listFiles().length;
            if (filesRemaining == 0) {
                return;
            }

            try {

                in = new FileInputStream(file);
                out = new FileOutputStream(newPath + "/" + file.getName());

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

        // Schedule an upload job to try and get the files which were just recorded to upload
        new JobUtilities(this).scheduleUpload();

    }

}