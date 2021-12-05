package uk.ac.nottingham.eaxtp1.CradleRideLogger.FileHandling;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Intent;

import java.io.File;

import static uk.ac.nottingham.eaxtp1.CradleRideLogger.FileHandling.UploadJobService.uploading;

import uk.ac.nottingham.eaxtp1.CradleRideLogger.BuildConfig;
import uk.ac.nottingham.eaxtp1.CradleRideLogger.R;
import uk.ac.nottingham.eaxtp1.CradleRideLogger.Utilities.FileCheckUtilities;

public class DeletingJobService extends JobService {
    public DeletingJobService() {}

    // This JobService handles the Deleting Job which checks the files and deletes them if the
    // server oks it. Afterwards, if NTT, a phone storage update is sent.

    String uploadedPath, id, newID, date, newDate;
    File[] uploadedFiles;
    boolean deleteFiles, cancelJob;
    public static boolean userWantsFilesKept, // Allows user to override the deletion in Settings
            rescheduleDeleting; // Boolean to feed back from FileCheckUtilities,

    @Override
    public boolean onStartJob(JobParameters jobParameters) {

        uploadedPath = String.valueOf(getExternalFilesDir(getApplicationContext().getResources().getString(R.string.fol_up)));
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
        final FileCheckUtilities fileCheck = new FileCheckUtilities(this);
        new Thread(() -> {

            for (File file : uploadedFiles) {
                newID = fileCheck.getID(file); // Extract ID from filename
                newDate = fileCheck.getDate(file); // Extract timestamp from filename
                // If the ID and Date are not the same as previous, ask the XML if file is to
                // be deleted. Otherwise, use the previous response found.
                if (! (newID.equals(id) && newDate.equals(date)) ) {

                    if (cancelJob) return;

                    id = newID;
                    date = newDate;

                    deleteFiles = fileCheck.deleteThisFile(id, date);

                }

                if (rescheduleDeleting) {
                    jobFinished(jobParameters, true);
                    return;
                } else if (!deleteFiles){
                    startService(new Intent(getApplicationContext(), UploadService.class)
                            .putExtra("Resend", file.getAbsolutePath()));
                }
            }

            if (BuildConfig.AMB_MODE) {
                fileCheck.sendStorageUpdate(); // Update the server XML for NTT phones.
            }

            // Confirm the job has finished, and remove it from the schedule.
            jobFinished(jobParameters, false);

        }).start();
    }

    @Override
    public boolean onStopJob(JobParameters jobParameters) {
        cancelJob = true;
        return true;    // Reschedule job
    }
}
