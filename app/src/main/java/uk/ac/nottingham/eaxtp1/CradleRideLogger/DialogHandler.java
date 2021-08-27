package uk.ac.nottingham.eaxtp1.CradleRideLogger;

import android.app.AlertDialog;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.view.ContextThemeWrapper;

public class DialogHandler extends ContextWrapper {

    // Class to handle the core of the AlertDialogs used in the MainActivity. If interaction with
    // MainActivity is required, some features need to be added inside the activity itself. This
    // handler is simply to reduce the amount of code in MainActivity to simplify reading.

    ContextThemeWrapper dialogWrapper = new ContextThemeWrapper(this, R.style.MyAlertDialog);

    public DialogHandler(Context context) {
        super(context);
    }

    // Create the privacy policy dialog
    public AlertDialog getPolicyDialog() {
        return new AlertDialog.Builder(dialogWrapper)
                .setTitle(R.string.privacy_title)
                .setMessage(R.string.privacy_policy)
                .setPositiveButton(R.string.butt_ok, (dialog, buttInt) -> {
//                  Close the Privacy Policy
                }).create();
    }

    // Build the core of the disclosure dialog
    public AlertDialog.Builder buildDisclosureDialog() {
        return new AlertDialog.Builder(dialogWrapper)
                .setTitle(R.string.ad_title)
                .setCancelable(false);
    }

    // Check if the app is the launcher (AMB Build only)
    public boolean appNotLauncher() {
        final Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        final ResolveInfo res = getPackageManager().resolveActivity(intent, 0);

        return !(res.activityInfo != null &&
                this.getPackageName().equals(res.activityInfo.packageName));

    }

    // Build the LauncherDialog core (AMB Build only)
    public AlertDialog.Builder buildLauncherPrompt() {
        return new AlertDialog.Builder(dialogWrapper)
                .setTitle(R.string.lc_title)
                .setMessage(R.string.lc_mess)
                .setNegativeButton(R.string.butt_later, (dialogInterface, i) -> {
                    // Close the Launcher Prompt
                });
    }
}
