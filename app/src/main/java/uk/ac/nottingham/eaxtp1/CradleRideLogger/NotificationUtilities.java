package uk.ac.nottingham.eaxtp1.CradleRideLogger;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.os.Build;

@SuppressLint("NewApi") // This is checked for with the boolean 'newAPI'.
public class NotificationUtilities extends ContextWrapper {

    // Class to handle all notification duties. This class establishes the Notification Channels
    // for APIs 26+, individual notifications and the notification manager.

    // Check if the Android version supports notification channels
    final boolean newAPI = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O;

    private NotificationManager manager;
    public final String FOREGROUND_CHANNEL_ID = BuildConfig.APPLICATION_ID + ".RECORDING",
            UPLOADING_CHANNEL_ID = BuildConfig.APPLICATION_ID + ".UPLOADING",
            UPLOADED_CHANNEL_ID = BuildConfig.APPLICATION_ID + ".UPLOADED",
            FAILED_CHANNEL_ID = BuildConfig.APPLICATION_ID + ".CANCELLED",
            STOPPED_CHANNEL_ID = BuildConfig.APPLICATION_ID + ".STOPPED",
            FOREGROUND_CHANNEL_NAME = "FOREGROUND CHANNEL",
            UPLOADING_CHANNEL_NAME = "UPLOADING CHANNEL",
            UPLOADED_CHANNEL_NAME = "UPLOADED CHANNEL",
            FAILED_CHANNEL_NAME = "FAILED CHANNEL",
            STOPPED_CHANNEL_NAME = "STOPPED CHANNEL";

    public final int UPLOADED_INT = getResources().getInteger(R.integer.uploadedID),
            OVERSIZED_INT = getResources().getInteger(R.integer.oversizedID),
            FAILED_INT = getResources().getInteger(R.integer.failedID),
            FOREGROUND_INT = getResources().getInteger(R.integer.foregroundID),
            STOPPED_INT = getResources().getInteger(R.integer.stoppedID);

    public NotificationUtilities(Context context) {
        super(context);
        if (newAPI) {
            createChannels();
        }
    }

    // Create the notification channels needed for APIs 26+
    public void createChannels() {

        NotificationChannel foreChannel = new NotificationChannel(FOREGROUND_CHANNEL_ID,
                FOREGROUND_CHANNEL_NAME,NotificationManager.IMPORTANCE_LOW);
        foreChannel.enableLights(false);
        foreChannel.enableVibration(false);
        foreChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        foreChannel.setDescription(getString(R.string.foreground_description));

        getManager().createNotificationChannel(foreChannel);

        NotificationChannel uploadingChannel = new NotificationChannel(UPLOADING_CHANNEL_ID,
                UPLOADING_CHANNEL_NAME,NotificationManager.IMPORTANCE_LOW);
        uploadingChannel.enableLights(false);
        uploadingChannel.enableVibration(false);
        uploadingChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        uploadingChannel.setDescription(getString(R.string.uploading_description));

        getManager().createNotificationChannel(uploadingChannel);

        NotificationChannel uploadedChannel = new NotificationChannel(UPLOADED_CHANNEL_ID,
                UPLOADED_CHANNEL_NAME,NotificationManager.IMPORTANCE_LOW);
        uploadedChannel.enableLights(false);
        uploadedChannel.enableVibration(false);
        uploadedChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        uploadedChannel.setDescription(getString(R.string.uploaded_description));

        getManager().createNotificationChannel(uploadedChannel);

        NotificationChannel failedChannel = new NotificationChannel(FAILED_CHANNEL_ID,
                FAILED_CHANNEL_NAME,NotificationManager.IMPORTANCE_LOW);
        failedChannel.enableLights(false);
        failedChannel.enableVibration(false);
        failedChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        failedChannel.setDescription(getString(R.string.failed_description));

        getManager().createNotificationChannel(failedChannel);

        NotificationChannel stoppedChannel = new NotificationChannel(STOPPED_CHANNEL_ID,
                STOPPED_CHANNEL_NAME,NotificationManager.IMPORTANCE_LOW);
        stoppedChannel.enableLights(false);
        stoppedChannel.enableVibration(false);
        stoppedChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        stoppedChannel.setDescription(getString(R.string.stopped_description));

        getManager().createNotificationChannel(stoppedChannel);

    }

    NotificationManager getManager() {
        if (manager == null) {
            manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        }
        return manager;
    }

    // Creates the notification used to keep services in the foreground.
    public Notification.Builder getForegroundNotification() {
        if (newAPI) {
            return new Notification.Builder(getApplicationContext(), FOREGROUND_CHANNEL_ID)
                    .setContentTitle(getString(R.string.app_name))
                    .setContentText(getString(R.string.recording_data))
                    .setSmallIcon(R.drawable.ambulance_symb);
        } else {
            return new Notification.Builder(getApplicationContext())
                    .setContentTitle(getString(R.string.app_name))
                    .setContentText(getString(R.string.recording_data))
                    .setSmallIcon(R.drawable.ambulance_symb);
        }
    }

    // Creates the foreground notification for uploading the files.
    public Notification.Builder getUploadingNotification() {
        if (newAPI) {
            return new Notification.Builder(getApplicationContext(), UPLOADING_CHANNEL_ID)
                    .setContentTitle(getString(R.string.app_name))
                    .setContentText(getString(R.string.uploading))
                    .setSmallIcon(R.drawable.ambulance_symb);
        } else {
            return new Notification.Builder(getApplicationContext())
                    .setContentTitle(getString(R.string.app_name))
                    .setContentText(getString(R.string.uploading))
                    .setSmallIcon(R.drawable.ambulance_symb);
        }
    }

    // Creates notifications to tell the user how many files were uploaded or failed.
    public Notification.Builder getUploadedNotification(boolean success, String body) {
        int id;
        if (success) {
            id = R.drawable.upload_symb;
        } else {
            id = R.drawable.failed_symb;
        }
        if (newAPI) {
            return new Notification.Builder(getApplicationContext(), UPLOADED_CHANNEL_ID)
                    .setContentTitle(getString(R.string.app_name))
                    .setContentText(body)
                    .setSmallIcon(id);
        } else {
            return new Notification.Builder(getApplicationContext())
                    .setContentTitle(getString(R.string.app_name))
                    .setContentText(body)
                    .setSmallIcon(id);
        }
    }

    // Tells the user the GPS failed to fix and creates an intent to take them to the app.
    public Notification.Builder getFailedNotification() {
        Intent restartApp = new Intent(this, MainActivity.class)
                .setAction(Intent.ACTION_VIEW)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .putExtra("clicked", true);
        PendingIntent goToApp = PendingIntent.getActivity(this,0,restartApp,PendingIntent
                .FLAG_UPDATE_CURRENT);
        if (newAPI) {
            return new Notification.Builder(getApplicationContext(), FAILED_CHANNEL_ID)
                    .setContentTitle(getString(R.string.app_name))
                    .setContentText(getString(R.string.failed))
                    .setSmallIcon(R.drawable.info_symb)
                    .setContentIntent(goToApp)
                    .setAutoCancel(true);
        } else {
            return new Notification.Builder(getApplicationContext())
                    .setContentTitle(getString(R.string.app_name))
                    .setContentText(getString(R.string.failed))
                    .setSmallIcon(R.drawable.info_symb)
                    .setContentIntent(goToApp)
                    .setAutoCancel(true);
        }
    }

    // Informs user that the recording has stopped due to a lack of movement.
    public Notification.Builder getStoppedNotification() {
        if (newAPI) {
            return new Notification.Builder(getApplicationContext(), STOPPED_CHANNEL_ID)
                    .setContentTitle(getString(R.string.app_name))
                    .setContentText(getString(R.string.as_message))
                    .setSmallIcon(R.drawable.stop_symb);
        } else {
            return new Notification.Builder(getApplicationContext())
                    .setContentTitle(getString(R.string.app_name))
                    .setContentText(getString(R.string.as_message))
                    .setSmallIcon(R.drawable.stop_symb);
        }
    }

    // Cancel old notifications.
    public void cancelNotifications(boolean newRecording) {
        getManager().cancel(UPLOADED_INT);
        getManager().cancel(OVERSIZED_INT);
        getManager().cancel(FAILED_INT);
        if (newRecording) {
            getManager().cancel(STOPPED_INT);
        }
    }

}
