package uk.ac.nottingham.AmbLogger.Utilities;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.Resources;
import android.os.Environment;
import android.os.StatFs;

import java.io.File;
import java.io.IOException;
import java.net.SocketException;
import java.net.URL;
import java.net.UnknownHostException;

import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import uk.ac.nottingham.AmbLogger.R;

import static uk.ac.nottingham.AmbLogger.FileHandling.DeletingJobService.rescheduleDeleting;
import static uk.ac.nottingham.AmbLogger.MainActivity.userID;

public class FileCheckUtilities extends ContextWrapper {

    // These Utilities handle and run the checking of files with an XML on the server. If the
    // file is listed on the server, then it is good to delete.
    // Also handled is the Storage Check for NTT phones.

    // Initialise the Utilities.
    public FileCheckUtilities(Context context) {
        super(context);
    }

    URL url;

    String idSeparator;

    Resources res = getResources();

    // Register the URL required.
    private URL getURL() {
        if (url == null) {
            try {
                url = new URL(res.getString(R.string.deleteURL));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return url;
    }

    // Method of extracting the ID from a filename
    public String getID(File file) {
        String filename = file.getName();
        int idStart = filename.indexOf(getIDSeparator()) + getIDSeparator().length();
        return filename.substring(idStart, idStart + 8);
    }

    private String getIDSeparator() {
        if (idSeparator == null) {
            idSeparator = res.getString(R.string.id_spacer);
        }
        return idSeparator;
    }

    // Method of extracting the start time from a filename
    public String getDate(File file) {
        String filename = file.getName();
        int dateEnd = filename.indexOf(getIDSeparator());
        return filename.substring(0, dateEnd);
    }

    // Check if file is to be deleted or not.
    public boolean deleteThisFile(String id, String date) {
        try {

            OkHttpClient okHttpClient = new OkHttpClient.Builder().build();

            // Send the recording ID and START DATE (and time) to the PHP
            RequestBody requestBody = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("id", id)
                    .addFormDataPart("date", date)
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

            // Depending on the response code from the PHP, either delete the
            // files or don't!
            // Using IF-ELSE instead of SWITCH to enable use of resource INTs
            if (response.code() == res.getInteger(R.integer.deleteFiles)) {
                // Delete the files
                deleteJourney(id, date);
                return true;

            } else if (response.code() == res.getInteger(R.integer.doNotDeleteFiles)) {
                // File not ready to be deleted yet
                return false;

            } else {
                // Cancel the deleting process and reschedule it for next
                // available time.
                rescheduleDeleting = true;
                return false;
            }

        } catch (IOException e) {
            e.printStackTrace();
            // Cancel the deleting process
            rescheduleDeleting = true;
            return false;
        }
    }

    // Send an update of the amount of storage available on NTT phones after deleting files.
    public void sendStorageUpdate() {

        File root = Environment.getDataDirectory();
        StatFs stat = new StatFs(root.getPath());
        String availableBytes = String.valueOf(stat.getAvailableBytes()), id = userID, version = "";

        try {
            version = version + getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {

            OkHttpClient okHttpClient = new OkHttpClient.Builder().build();

            // Send the recording ID and START DATE (and time) to the PHP
            RequestBody requestBody = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("id", id)
                    .addFormDataPart("storage", availableBytes)
                    .addFormDataPart("version", version) // Send Version Name for info
                    .build();

            Request request = new Request.Builder()
                    .url(getURL())
                    .post(requestBody)
                    .build();

            try {
                okHttpClient.newCall(request).execute();
            } catch (UnknownHostException uhe) {
                throw new IOException("UnknownHostException - Upload connection failed.");
            } catch (SocketException se) {
                throw new IOException("Socket Exception");
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Method for handling the deletion of all files with a particular ID and Date.
    // i.e. all files from a specific journey.
    private void deleteJourney(final String id, final String date) {
        new Thread(() -> {
            File uploadedFolder = new File(String.valueOf(getExternalFilesDir(res.getString(R.string.fol_up))));
            File[] uploadedFileList = uploadedFolder.listFiles();
            if (uploadedFileList == null) return;
            for (File file : uploadedFileList) {
                if (getID(file).equals(id) && getDate(file).equals(date)) {
                    file.delete();
                }
            }
        }).start();
    }

}