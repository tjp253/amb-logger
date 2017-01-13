package uk.ac.nottingham.eaxtp1.CradleRideLogger;

import android.app.IntentService;
import android.app.NotificationManager;
import android.content.Intent;
import android.support.v7.app.NotificationCompat;
import android.widget.Toast;

public class UploadIntentService extends IntentService {

    public UploadIntentService() {
        super("UploadIntentService");
    }

    NotificationCompat.Builder mBuilder;

//    private long startTime;

    @Override
    public void onCreate() {
        super.onCreate();

//        startTime = System.currentTimeMillis();

         mBuilder =
                (NotificationCompat.Builder) new NotificationCompat.Builder(this)
                        .setSmallIcon(R.drawable.hand_icon)
                        .setContentTitle("The service works")
                        .setContentText("This is proof!!");

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Toast.makeText(this, "Service is stopped!", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onHandleIntent(Intent intent) {

// Sets an ID for the notification
        int mNotificationId = 001;
// Gets an instance of the NotificationManager service
        NotificationManager mNotifyMgr =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
// Builds the notification and issues it.
        mNotifyMgr.notify(mNotificationId, mBuilder.build());
        

//        int jobNumber = intent.getIntExtra("jobNumber", 0);
//
//        for (int i=0; i<5; i++) {
//            try{Thread.sleep(2000);}catch (Exception e){;}
//
//
//
//
//        }
//
//        Toast.makeText(this, "", Toast.LENGTH_SHORT).show();

//        int upload = intent.getIntExtra("Upload", 0);
//
//        for (int i=0; i<1; i++) {
//            //        Shows that the IntentService has been called
//            Toast.makeText(this, "Service has been started! " + String.valueOf(upload),
//                    Toast.LENGTH_SHORT).show();
//            int working = i;
//        }

    }

}
