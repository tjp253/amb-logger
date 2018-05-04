package uk.ac.nottingham.eaxtp1.CradleRideLogger;

import android.app.IntentService;
import android.content.Intent;
import android.content.SharedPreferences;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.LinkedList;
import java.util.NoSuchElementException;
import java.util.Queue;
import java.util.zip.Deflater;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.keyBuffEnd;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.keyFS;

public class BufferService extends IntentService {

    public BufferService() {
        super("BufferService");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        preferences = getSharedPreferences("myPreferences", MODE_PRIVATE);
        startMoving = new Intent(getApplicationContext(), MovingService.class);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null) {
            recFold = intent.getStringExtra("Folder");
            inFile = intent.getIntExtra("Samples", 0);
            fileParts = intent.getIntExtra("FileParts", 0);
            if (inFile != 0) {
                removeOrDelete();
            }
        }
    }

    Intent startMoving;

    Queue<String> movingQ;
    StringBuilder stringBuilder;

    String nameOld, nameNew, recFold, nameTemp = "temp.csv.gz";
    int inFile, toRemove, toKeep, fileParts, filePos;
    SharedPreferences preferences;

    File endFileOld, endFileNew;
    GZIPInputStream gInput;
    OutputStream gOutput;
    BufferedReader buffRead;
    String toNewFile, toQ, suffix = ".csv.gz";

    public void removeOrDelete() {

//        Sample frequency * minutes to buff * seconds in a minute
        toRemove = preferences.getInt(keyFS, 0) *
                preferences.getInt(keyBuffEnd, 0) * 60;

        toKeep = inFile - toRemove;

        File folder = new File(recFold);
        File[] fileList = folder.listFiles();
        filePos = fileList.length - 1;

        nameOld = fileList[filePos].getPath();

        endFileOld = new File(nameOld);

        if (toKeep > 0) { // Remove some samples from the end file
            nameNew = nameOld;
            nameOld = recFold + nameTemp;
            endFileOld.renameTo(new File(nameOld));
            endFileNew = new File(nameNew);
            editFiles(false);

        } else if (fileParts > 1){ // Delete old end file and process next file accordingly.

            fileParts--;
            filePos--;

            endFileOld.delete();

            nameOld = fileList[filePos].getPath();
            endFileOld = new File(nameOld);

            if (fileParts == 1) { // Remove part number from filename
                nameNew = nameOld.replace("-01" + suffix, suffix);
            } else { // Add "-END" to filename
                nameNew = nameOld.replace(fileParts + suffix, fileParts + "-END" + suffix);
            }
            endFileNew = new File(nameNew);

            if (toKeep == 0) { // Simply rename the preceding file to become an end file

                endFileOld.renameTo(endFileNew);

            } else {

                toRemove -= inFile;
                inFile = 0;
                editFiles(true);

            }
        }

        startService(startMoving);

    }

    public void editFiles(boolean countLines) { // Move required samples from old file to new file.

        movingQ = new LinkedList<>();
        stringBuilder = new StringBuilder("");

        try {
            gInput = new GZIPInputStream(new FileInputStream(nameOld));
            gOutput = new GZIPOutputStream(new FileOutputStream(nameNew)) {{
                def.setLevel(Deflater.BEST_COMPRESSION);
            }};
            buffRead = new BufferedReader(new InputStreamReader(gInput));

            if (countLines) {

                while ( buffRead.readLine() != null) {
                    inFile++;
                }
                toKeep = inFile - toRemove;
                buffRead.close();
                buffRead = null;

                editFiles(false);

            } else {

                for (int i = 1; i <= toKeep; i++) {
                    toQ = buffRead.readLine() + "\n";
                    movingQ.add(toQ);
                    if (i % 2000 == 0) {
                        writeToFile();
                    }
                }

                if (movingQ.size() > 0) {
                    writeToFile();
                }

                buffRead.close();
                gInput.close();
                gOutput.close();

                new File(nameOld).delete();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public void writeToFile() {
        try {

            stringBuilder.setLength(0);

            int qSize = movingQ.size();
            for (int ii = 1; ii <= qSize; ii++) {
                stringBuilder.append(movingQ.remove());
            }

            toNewFile = stringBuilder.toString();

            gOutput.write(toNewFile.getBytes("UTF-8"));

        } catch (IOException ioe) {
            ioe.printStackTrace();
        } catch (NoSuchElementException noe) {
            noe.printStackTrace();
        }
    }
}
