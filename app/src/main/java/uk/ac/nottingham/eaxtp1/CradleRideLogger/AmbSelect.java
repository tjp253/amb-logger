package uk.ac.nottingham.eaxtp1.CradleRideLogger;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import java.util.Calendar;
import java.util.Objects;

import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.ambExtra;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.loggingFilter;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.loggingInt;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.phoneDead;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.recording;

public class AmbSelect extends Activity implements View.OnClickListener {

//    Activity for user to input which ambulance / trolley was used for the journey, whether a
// baby was on board, whether emergency driving was used, and the reason for transfer.

    Button buttOther, buttSame;
    Button[] allButts = new Button[6];
    TextView titleView, asView;

//    Declare AMB-specific preferences and variables.
    SharedPreferences ambPref;
    SharedPreferences.Editor prefEd;
    static boolean selectingAmb, forcedStopAmb;

    AlertDialog inAlert;
    ContextThemeWrapper dialogWrapper = new ContextThemeWrapper(this, R.style.MyAlertDialog);

    final String keyDate = "PrefDate";
    String[] manArray, engArray, trollArray, boolArray, reasonArray;

    boolean patient, resuscitated;
    int inputNo, viewSame = View.INVISIBLE, viewOther = View.VISIBLE;

    @SuppressLint({"CommitPrefEdits", "ApplySharedPref"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_amb_select);

        resuscitated = this.getIntent().getBooleanExtra(getString(R.string.extra_dead),false);

        forcedStopAmb = this.getIntent().getBooleanExtra(getString(R.string.forcedIntent),false);

        selectingAmb = true;
        ambPref = getSharedPreferences(getString(R.string.pref_amb), MODE_PRIVATE);
        prefEd = ambPref.edit();

//        Initialise textboxes and buttons
        titleView = findViewById(R.id.optTitle); // FOR THE OPTION QUESTION, AT TOP
        asView    = findViewById(R.id.forcedText); // APPEARS AT BOTTOM IF AUTOSTOP KICKED IN
        buttOther = findViewById(R.id.optOther); // EITHER FULL WIDTH OR ON THE RIGHT
        buttSame  = findViewById(R.id.optSame); // APPEARS ON THE LEFT

        // DEFAULT OPTION BUTTONS
        allButts[0] = findViewById(R.id.opt1); // TOP LEFT
        allButts[1] = findViewById(R.id.opt2); // TOP RIGHT
        allButts[2] = findViewById(R.id.opt3); // MIDDLE LEFT
        allButts[3] = findViewById(R.id.opt4); // MIDDLE RIGHT
        allButts[4] = findViewById(R.id.opt5); // BOTTOM LEFT
        allButts[5] = findViewById(R.id.opt6); // BOTTOM RIGHT

        for (Button allButt : allButts) {
            allButt.setOnClickListener(this);
        }
        buttOther.setOnClickListener(this);
        buttSame.setOnClickListener(this);

//        If the app has been used before during the same day, offer a 'Same as Previous' option
// for ambulance & trolley options - potentially saving time.
        if (ambPref.getInt(keyDate,0) == Calendar.getInstance().get(Calendar.DAY_OF_YEAR)) {
            viewSame = View.VISIBLE;
        }

        Resources res = getResources();

        boolArray = res.getStringArray(R.array.boolean_options);
        reasonArray = res.getStringArray(R.array.reason_options);

        if (recording || forcedStopAmb || phoneDead) {   // Ask for transport-end data

            if (resuscitated) {
                buttOther.setText(R.string.optForgot);

            } else if (forcedStopAmb) { // Hide cancel button if recording stopped due to inactivity
                viewOther = View.INVISIBLE;
                asView.setVisibility(View.VISIBLE);

            } else {
                buttOther.setText(R.string.optCancel);

            }
//            Check if baby is on board, and change the question appropriately
            patient = Objects.equals(
                    ambPref.getString(getString(R.string.key_bob), "NO"),
                    res.getString(R.string.yesButt));
            changeButtsEnd(patient);
            if (!patient) {
                prefEd.putString(getString(R.string.key_trans),"N/A").commit();
            }

        } else {    // Ask for transport-start data
//            Start GPS initialising
            startService(new Intent(getApplicationContext(), AmbGPSService.class));

            // Grab the list of ambulance manufacturers
            manArray = res.getStringArray(R.array.amb_manufacturer);
            // Grab the list of possible engine types
            engArray = res.getStringArray(R.array.amb_engine);

            // Grab the NTT-specific trolley codes
            int nttInt = ambPref.getInt(getString(R.string.key_pref_ntt),0);
            TypedArray array = res.obtainTypedArray(R.array.troll_ntt);
            int trollChoice = Integer.parseInt( Objects.requireNonNull(array.getString(nttInt)).substring((1)) );
            array.recycle();    // Android was giving me  warning. Can't have that, so added this!
            trollArray = res.getStringArray(trollChoice);

            changeButtsStart();  // Setup the question and buttons
        }

    }

    public void changeButts(String[] options, int sameVisibility, int otherVisibility) {
        int opt;
        for (opt = 0; opt < options.length; opt++) {
            allButts[opt].setText(options[opt]);
            allButts[opt].setVisibility(View.VISIBLE);
        }

        int visibility = View.INVISIBLE;
        for (int hide = opt; hide < allButts.length; hide++) {
            if (hide == 4) { // HIDE THE BOTTOM ROW, AS NOT REQUIRED (and ease button presses)
                visibility = View.GONE;
            }
            allButts[hide].setVisibility(visibility);
        }

        buttSame.setVisibility(sameVisibility);
        buttOther.setVisibility(otherVisibility);
    }

//    Set up inputs at start of recording
    public void changeButtsStart() {
        switch (inputNo) {  // How many questions have already been answered?

            case 0: // Ask for ambulance manufacturer
                titleView.setText(R.string.manTit);
                buttOther.setText(R.string.optOther);
                changeButts(manArray, viewSame, viewOther);
                break;

            case 1: // Ask for ambulance engine type
                titleView.setText(R.string.engTit);
                buttOther.setText(R.string.optReturn);
                changeButts(engArray, viewSame, View.VISIBLE);
                break;

            case 2: // Ask for trolley ID
                titleView.setText(R.string.trollTit);
                changeButts(trollArray, viewSame, View.VISIBLE);
                break;

            case 3: // Ask whether a baby is on board
                titleView.setText(R.string.patTit);
                changeButts(boolArray, View.INVISIBLE, View.VISIBLE);
                break;
        }
    }

//    Set up inputs at end of recording
    public void changeButtsEnd(boolean baby) {
        if (baby) { // Ask reason for transfer
            titleView.setText(R.string.transTit);
            changeButts(reasonArray, View.GONE, viewOther);
        } else {    // Ask about emergency driving
            titleView.setText(R.string.emergeTit);
            changeButts(boolArray, View.GONE, viewOther);
        }
    }

//    Store input choices in preferences for logging and to speed up future inputs (SLIGHTLY)
    public void storeAmb(String metaText) {
        if (!(recording || forcedStopAmb || phoneDead)) {   // Selections before journey.
            buttPause.start();
            switch (inputNo) {

                case 0: // Store ambulance manufacturer
                    prefEd.putString(getString(R.string.key_man), metaText).commit();
                    break;

                case 1: // Store ambulance engine type
                    prefEd.putString(getString(R.string.key_eng), metaText).commit();
                    break;

                case 2: // Store trolley ID
                    prefEd.putString(getString(R.string.key_troll), metaText).commit();
                    break;

                case 3: // Store baby presence
                    prefEd.putString(getString(R.string.key_bob), metaText).commit();
                    prefEd.putInt(keyDate, Calendar.getInstance().get(Calendar.DAY_OF_YEAR)).commit();
                    sendIntentBack(true);   // Start the full recording
                    return;

            }
        } else {    // Selections after journey.
            if (patient) {
                buttPause.start();
                prefEd.putString(getString(R.string.key_trans), metaText);
                patient = false;
            } else {
                prefEd.putString(getString(R.string.key_emerge), metaText).commit();
                sendIntentBack(true);
                return;
            }
        }

        inputNo++;  // How many questions have now been answered?
    }

    @Override
    public void onClick(View v) {   // Store different results depending on button pressed
        int vID = v.getId();
        if (vID == R.id.optOther) {
            getOther();
        } else if (vID == R.id.optSame) {
            // Don't change the preference value
            buttPause.start();
            inputNo++;
        } else {
            storeAmb( ((Button) v).getText().toString().replace("\n", "-") );
        }
    }

//    Timer to allow a slight pause between input changes. Otherwise the user may doubt they've
// answered correctly.
    public CountDownTimer buttPause = new CountDownTimer(200,200) {
        @Override
        public void onTick(long millisUntilFinished) {}

        @Override
        public void onFinish() {
            if (!recording && !forcedStopAmb) {
                changeButtsStart();
            } else {
                changeButtsEnd(false);
            }
        }
    };

//    Tell Main Activity what to do next, and close this screen
    public void sendIntentBack(boolean done) {
        setResult(Activity.RESULT_OK, new Intent().putExtra(ambExtra, done));
        if (buttPause != null) {
            buttPause.cancel();
        }

//        If forced to stop (due to inactivity) this refreshes the MainActivity
        if (forcedStopAmb) {
            Intent intent = new Intent(loggingFilter);
            intent.putExtra(loggingInt, 9);
            sendBroadcast(intent);
        }

        finish();
    }

    public void getOther() {
        if (resuscitated) {
            storeAmb("UNKNOWN"); // Enter in 'Unknown'
            inputNo++;

        } else if (recording) {
            finish();   // Cancel selection and return to recording

        } else if (inputNo > 0) { // Act as Back Button
            onBackPressed();

        } else {    // Give user a window to input their own Ambulance ID

            AlertDialog.Builder builder = new AlertDialog.Builder(dialogWrapper);
            View inputView = View.inflate(this, R.layout.amb_input, null);
//            Tell the app what to show and what to hide

            final EditText input = inputView.findViewById(R.id.ambInput);
            input.setFilters(new InputFilter[] {new InputFilter.AllCaps()}); // Ensure all caps

            builder.setMessage(R.string.entMan)
                    .setPositiveButton(R.string.entButt, (dialog, which) -> {
//                            Commit ID inputted by user
                        prefEd.putString(getString(R.string.key_man), input.getText().toString()).commit();
                        changeButtsStart();
                    })
                    .setView(inputView)
                    .setCancelable(false).create();

            inAlert = builder.show();

            final Button inButt = inAlert.getButton(AlertDialog.BUTTON_POSITIVE);
            inButt.setEnabled(false);

            input.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
//                        Bring up the keyboard
                    Objects.requireNonNull(inAlert.getWindow())
                            .setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
                }
            });

            input.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
//                    Only allow ID to be saved if it is a reasonable length
                    inButt.setEnabled(s.length() > 3 && s.length() < 10);
                }

                @Override
                public void afterTextChanged(Editable s) {}
            });

            inputNo++;
        }
    }

    @Override
    public void onBackPressed() {
        if (!recording && inputNo == 0) { // Confirm user wants to return to home screen and cancel recording.
            confirmExit();
        } else if (inputNo != 0){ // Change buttons back to previous selection
            inputNo--;
            if (!recording) {
                changeButtsStart();
            } else {
                patient = true;
                changeButtsEnd(true);
            }
        }
        // Otherwise, do nothing
    }

    public void confirmExit() {
        AlertDialog.Builder builder = new AlertDialog.Builder(dialogWrapper);
        builder .setTitle("Cancel Recording")
                .setPositiveButton(R.string.yesButt, (dialog, which) -> sendIntentBack(false))
                .setNegativeButton(R.string.noButt, (dialog, which) -> {
//                        Do nothing.
                })
                .setCancelable(false).create().show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        selectingAmb = false;
    }
}
