package uk.ac.nottingham.eaxtp1.CradleRideLogger;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.PackageManager;
import android.os.Build;

@SuppressLint("NewApi") // Checked for with the 'mAPI' boolean
public class PermissionHandler extends ContextWrapper {

    final boolean mAPI = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M;

    // Initialise ints for app permissions
    final int PERMISSION_GPS = 2, PERMISSION_AUDIO = 25;

    final String[] locPerms = new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION},
            audPerm = new String[]{Manifest.permission.RECORD_AUDIO};

    public PermissionHandler(Context context) {
        super(context);
    }

    public boolean needsPerms() {
        if (!mAPI) return false;

        return needsLocationPerms() || needsAudioPerm();
    }

    public boolean needsLocationPerms() {
        if (!mAPI) return false;

        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                        != PackageManager.PERMISSION_GRANTED &&
                checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                        != PackageManager.PERMISSION_GRANTED;
    }

    public boolean needsAudioPerm() {
        if (!mAPI) return false;

        return checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                        != PackageManager.PERMISSION_GRANTED;
    }

}
