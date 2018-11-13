package uk.ac.nottingham.eaxtp1.CradleRideLogger;

import android.app.job.JobParameters;
import android.app.job.JobService;
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

import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.userID;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.UploadJobService.uploading;

public class DeletingJobService extends JobService {
    public DeletingJobService() {}

    // This JobService handles and runs the checking of files with an XML on the server. If the
    // file is listed on the server, then it is good to delete.

    String uploadedPath, id, newID, date, newDate;
    File[] uploadedFiles;
    URL url;
    boolean deleteFiles, cancelJob;
    static boolean userWantsFilesKept; // Allows user to override the deletion in Settings

    @Override
    public boolean onStartJob(JobParameters jobParameters) {

        uploadedPath = String.valueOf(getExternalFilesDir("Uploaded"));
        File uploadedFolder = new File(uploadedPath);

        // Checks files are not in the process of being uploaded, and that there are files in the
        // folder to be deleted
        if (!uploading && uploadedFolder.isDirectory() && !userWantsFilesKept) {
            uploadedFiles = uploadedFolder.listFiles();
            if (uploadedFiles.length != 0) {
                checkAndDeleteFiles(jobParameters);
                return true;
            }
        }

        // Cancel job
        return false;
    }

    private void checkAndDeleteFiles(final JobParameters jobParameters) {
        new Thread(new Runnable() {
            @Override
            public void run() {

                try {
                    url = new URL(getResources().getString(R.string.deleteURL));
                } catch (Exception e) {
                    e.printStackTrace();
                }

                for (File file : uploadedFiles) {
                    String filename = file.getName();
                    int dateEnd = filename.indexOf(getResources().getString(R.string.id_spacer)), // Find start position of "-ID"
                            idStart = dateEnd + 3, // Find the start of the 8-digit ID
                            idEnd = idStart + 8;   // Find the end of the 8-digit ID
                    newID = filename.substring(idStart,idEnd); // Extract ID from filename
                    newDate = filename.substring(0, dateEnd); // Extract timestamp from filename

                    // If the ID and Date are not the same as previous, ask the XML if file is to
                    // be deleted. Otherwise, use the previous response found.
                    if (! (newID.equals(id) && newDate.equals(date)) ) {

                        if (cancelJob) return;

                        id = newID;
                        date = newDate;

                        try {

                            OkHttpClient okHttpClient = new OkHttpClient.Builder().build();

                            // Send the recording ID and START DATE (and time) to the PHP
                            RequestBody requestBody = new MultipartBody.Builder()
                                    .setType(MultipartBody.FORM)
                                    .addFormDataPart("id", id)
                                    .addFormDataPart("date", date)
                                    .build();

                            Request request = new Request.Builder().url(url).post(requestBody).build();

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
                            if (response.code() == getResources().getInteger(R.integer.deleteFiles)) {
                                // Delete the files
                                deleteFiles = true;

                            } else if (response.code() == getResources().getInteger(R.integer.doNotDeleteFiles)) {
                                // File not ready to be deleted yet
                                deleteFiles = false;

                            } else {
                                // Cancel the deleting process and reschedule it for next
                                // available time.
                                jobFinished(jobParameters, true);
                                return;
                            }

                        } catch (IOException e) {
                            e.printStackTrace();
                            // Cancel the deleting process
                        }

                    }

                    if (deleteFiles) {
                        file.delete();
                    }
                }

                if (BuildConfig.AMB_MODE) {
                    sendStorageUpdate(); // Update the server XML for NTT phones.
                }

                // Confirm the job has finished, and remove it from the schedule.
                jobFinished(jobParameters, false);

            }
        }).start();
    }

    // Send an update of the amount of storage available on NTT phones after deleting files.
    private void sendStorageUpdate() {

        File root = Environment.getDataDirectory();
        StatFs stat = new StatFs(root.getPath());
        String availableBytes = String.valueOf(stat.getAvailableBytes());

        id = String.valueOf(userID);

        try {

            OkHttpClient okHttpClient = new OkHttpClient.Builder().build();

            // Send the recording ID and START DATE (and time) to the PHP
            RequestBody requestBody = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("id", id)
                    .addFormDataPart("storage", availableBytes)
                    .build();

            Request request = new Request.Builder().url(url).post(requestBody).build();

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

    @Override
    public boolean onStopJob(JobParameters jobParameters) {
        cancelJob = true;
        return true;    // Reschedule job
    }
}
