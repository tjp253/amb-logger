package uk.ac.nottingham.eaxtp1.CradleRideLogger;

import android.app.job.JobParameters;
import android.app.job.JobService;

import java.io.File;

import static uk.ac.nottingham.eaxtp1.CradleRideLogger.UploadJobService.uploading;

public class DeletingJobService extends JobService {
    public DeletingJobService() {}

    // This JobService handles the Deleting Job which checks the files and deletes them if the
    // server oks it. Afterwards, if NTT, a phone storage update is sent.

    String uploadedPath, id, newID, date, newDate;
    File[] uploadedFiles;
    boolean deleteFiles, cancelJob;
    static boolean userWantsFilesKept, // Allows user to override the deletion in Settings
            rescheduleDeleting; // Boolean to feed back from FileCheckUtilities,

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
        final FileCheckUtilities fileCheck = new FileCheckUtilities(this);
        new Thread(new Runnable() {
            @Override
            public void run() {

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

                        deleteFiles = fileCheck.deleteThisFile(id, date);

                    }

                    if (deleteFiles) {
                        file.delete();
                    } else if (rescheduleDeleting) {
                        jobFinished(jobParameters, true);
                        return;
                    }
                }

                if (BuildConfig.AMB_MODE) {
                    fileCheck.sendStorageUpdate(); // Update the server XML for NTT phones.
                }

                // Confirm the job has finished, and remove it from the schedule.
                jobFinished(jobParameters, false);

            }
        }).start();
    }

    @Override
    public boolean onStopJob(JobParameters jobParameters) {
        cancelJob = true;
        return true;    // Reschedule job
    }
}
