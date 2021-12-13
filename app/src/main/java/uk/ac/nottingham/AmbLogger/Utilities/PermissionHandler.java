package uk.ac.nottingham.AmbLogger.Utilities;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.PackageManager;

@SuppressLint("NewApi") // Checked for with the 'mAPI' boolean
public class PermissionHandler extends ContextWrapper {

    // Initialise ints for app permissions
    public final int PERMISSION_GPS = 2, PERMISSION_AUDIO = 25;

    public final String[] locPerms = new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION},
            audPerm = new String[]{Manifest.permission.RECORD_AUDIO};

    public PermissionHandler(Context context) {
        super(context);
    }

    public boolean needsPerms() {
        return needsLocationPerms() || needsAudioPerm();
    }

    public boolean needsLocationPerms() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                        != PackageManager.PERMISSION_GRANTED &&
                checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                        != PackageManager.PERMISSION_GRANTED;
    }

    public boolean needsAudioPerm() {
        return checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                        != PackageManager.PERMISSION_GRANTED;
    }

}
