package uk.ac.nottingham.eaxtp1.CradleRideLogger;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.Editable;
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
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.recording;

public class AmbSelect extends Activity implements View.OnClickListener {

//    Activity for user to input which ambulance / trolley was used for the journey, whether a
// baby was on board, whether emergency driving was used, and the reason for transfer.

    Button butt1, butt2, butt3, butt4, buttOther, buttSame;
    TextView titleView, asView;

//    Declare AMB-specific preferences and variables.
    SharedPreferences ambPref;
    SharedPreferences.Editor prefEd;
    static boolean selectingAmb, forcedStopAmb;
    static final String keyAmb = "PrefAmb", keyTroll = "PrefTroll", keyPat = "PrefPat",
            keyTrans = "PrefTrans", keyEmerge = "PrefEmerge";

    AlertDialog inAlert;
    ContextThemeWrapper dialogWrapper = new ContextThemeWrapper(this, R.style.MyAlertDialog);

    final String keyDate = "PrefDate";
    String prefStr;
    String[] ambArray, trollArray;

    boolean patient;
    int inputNo, intOne, intTwo, strID;

    @SuppressLint({"CommitPrefEdits", "ApplySharedPref"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_amb_select);

        forcedStopAmb = this.getIntent().getBooleanExtra(getString(R.string.forcedIntent),false);

        selectingAmb = true;
        ambPref = getSharedPreferences(getString(R.string.pref_amb), MODE_PRIVATE);
        prefEd = ambPref.edit();

//        Initialise textboxes and buttons
        titleView = findViewById(R.id.optTitle);
        asView    = findViewById(R.id.forcedText);
        butt1     = findViewById(R.id.opt1);
        butt2     = findViewById(R.id.opt2);
        butt3     = findViewById(R.id.opt3);
        butt4     = findViewById(R.id.opt4);
        buttOther = findViewById(R.id.optOther);
        buttSame  = findViewById(R.id.optSame);

        butt1.setOnClickListener(this);
        butt2.setOnClickListener(this);
        butt3.setOnClickListener(this);
        butt4.setOnClickListener(this);
        buttOther.setOnClickListener(this);
        buttSame.setOnClickListener(this);

//        If the app has been used before during the same day, offer a 'Same as Previous' option
// for ambulance & trolley options - potentially saving time.
        if (ambPref.getInt(keyDate,0) == Calendar.getInstance().get(Calendar.DAY_OF_YEAR)) {
            buttSame.setVisibility(View.VISIBLE);
        }

        if (recording || forcedStopAmb) {   // Ask for transport-end data
            butt3.setVisibility(View.INVISIBLE);
            butt4.setVisibility(View.INVISIBLE);
            buttSame.setVisibility(View.GONE);
            buttOther.setText(R.string.butt_cancel);
            if (forcedStopAmb) {    // Hide cancel button if recording stopped due to inactivity
                buttOther.setVisibility(View.INVISIBLE);
                asView.setVisibility(View.VISIBLE);
            }
//            Check if baby is on board, and change the question appropriately
            patient = ambPref.getBoolean(keyPat, false);
            changeButtsEnd(patient);
            if (!patient) {
                prefEd.putString(keyTrans,"N/A").commit();
            }

        } else {    // Ask for transport-start data
//            Start GPS initialising
            startService(new Intent(getApplicationContext(), AmbGPSService.class));
//            Extract the NTT's specific IDs
            int nttInt = ambPref.getInt(getString(R.string.key_pref_ntt),0);
            TypedArray array = getResources().obtainTypedArray(R.array.amb_ntt);
            int ambChoice   = Integer.valueOf( Objects.requireNonNull(array.getString(nttInt)).substring((1)) );
            array = getResources().obtainTypedArray(R.array.troll_ntt);
            int trollChoice = Integer.valueOf( Objects.requireNonNull(array.getString(nttInt)).substring((1)) );
            array.recycle();    // Android was giving me  warning. Can't have that, so added this!
            ambArray   = getResources().getStringArray(ambChoice);
            trollArray = getResources().getStringArray(trollChoice);

            changeButtsStart();  // Setup the question and buttons
        }

    }

    @Override
    public void onClick(View v) {   // Store different results depending on button pressed
        switch (v.getId()) {
            case R.id.opt1: storeAmb(1); break;
            case R.id.opt2: storeAmb(2); break;
            case R.id.opt3: storeAmb(3); break;
            case R.id.opt4: storeAmb(4); break;
            case R.id.optOther: getOther(); break;
            case R.id.optSame:  // Don't change the preference value
                buttPause.start();
                inputNo++;
                break;
        }
    }

//    Set up inputs at start of recording
    public void changeButtsStart() {
        switch (inputNo) {  // How many questions have already been answered?
            case 0: // Ask for ambulance ID
                titleView.setText(R.string.ambTit);
                butt1.setText(ambArray[0]);
                butt2.setText(ambArray[1]);
                butt3.setText(ambArray[2]);
                butt4.setText(ambArray[3]);
                break;
            case 1: // Ask for trolley ID
                titleView.setText(R.string.trollTit);
                butt1.setText(trollArray[0]);
                butt2.setText(trollArray[1]);
                butt3.setText(trollArray[2]);
                butt4.setText(trollArray[3]);
                butt3.setVisibility(View.VISIBLE);
                butt4.setVisibility(View.VISIBLE);
                buttOther.setVisibility(View.VISIBLE);
                if (ambPref.getInt(keyDate,0) == Calendar.getInstance().get(Calendar.DAY_OF_YEAR)) {
                    buttSame.setVisibility(View.VISIBLE);
                }
                break;
            case 2: // Ask whether a baby is on board
                titleView.setText(R.string.patTit);
                butt1.setText(R.string.yesButt);
                butt2.setText(R.string.noButt);
                butt3.setVisibility(View.INVISIBLE);
                butt4.setVisibility(View.INVISIBLE);
                buttOther.setVisibility(View.INVISIBLE);
                buttSame.setVisibility(View.GONE);
                break;
        }
    }

//    Set up inputs at end of recording
    public void changeButtsEnd(boolean baby) {
        if (baby) { // Ask reason for transfer
            titleView.setText(R.string.transTit);
            butt1.setText(R.string.transReasonOne);
            butt2.setText(R.string.transReasonTwo);
        } else {    // Ask about emergency driving
            titleView.setText(R.string.emergeTit);
            butt1.setText(R.string.yesButt);
            butt2.setText(R.string.noButt);
        }
    }

//    Store input choices in preferences for logging and to speed up future inputs SLIGHTLY
    public void storeAmb(int optionNo) {
        if (!(recording || forcedStopAmb)) {   // Selections before journey.
            buttPause.start();
            switch (inputNo) {
                case 0: // Store ambulance ID
                    prefEd.putString(keyAmb, ambArray[optionNo-1]).commit();
                    break;
                case 1: // Store trolley ID
                    prefEd.putString(keyTroll, trollArray[optionNo-1]).commit();
                    break;
                case 2: // Store baby presence
                    prefEd.putBoolean(keyPat, optionNo == 1).commit();
                    prefEd.putInt(keyDate, Calendar.getInstance().get(Calendar.DAY_OF_YEAR)).commit();
                    sendIntentBack(true);   // Start the full recording
                    return;
            }
        } else {    // Selections after journey.
            if (patient) {
                buttPause.start();
                switch (optionNo) {
                    case 1: prefEd.putString(keyTrans, getString(R.string.transReasonOne)); break;
                    case 2: prefEd.putString(keyTrans, getString(R.string.transReasonTwo)); break;
                    case 5: finish(); return;
                }
                patient = false;
            } else {
                switch (optionNo) {
                    case 1: prefEd.putString(keyEmerge, getString(R.string.yesButt)).commit(); break;
                    case 2: prefEd.putString(keyEmerge, getString(R.string.noButt)).commit(); break;
                    case 5: finish(); return;
                }
                sendIntentBack(true);
                return;
            }
        }

        inputNo++;  // How many questions have now been answered?
    }

//    Timer to allow a slight pause between input changes. Otherwise the user may doubt they've
// answered correctly.
    public CountDownTimer buttPause = new CountDownTimer(500,500) {
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
        if (recording) {
            storeAmb(5);    // Cancel selection and return to recording

        } else {    // Give user a window to input their own Ambulance / Trolley ID

            AlertDialog.Builder builder = new AlertDialog.Builder(dialogWrapper);
            View inputView = View.inflate(this, R.layout.amb_input, null);
//            Tell the app what to show and what to hide
            if (inputNo == 0) {
                intOne = R.id.ambInput;
                intTwo = R.id.trollInput;
                strID = R.string.entAmb;
                prefStr = keyAmb;
            } else {
                intOne = R.id.trollInput;
                intTwo = R.id.ambInput;
                strID = R.string.entTroll;
                prefStr = keyTroll;
            }
            final EditText input = inputView.findViewById(intOne);
            final EditText input2 = inputView.findViewById(intTwo);
            input2.setVisibility(View.INVISIBLE);

            builder.setMessage(strID)
                    .setPositiveButton(R.string.entButt, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
//                            Commit ID inputted by user
                            prefEd.putString(prefStr, input.getText().toString()).commit();
                            changeButtsStart();
                        }
                    })
                    .setView(inputView)
                    .setCancelable(false).create();

            inAlert = builder.show();

            final Button inButt = inAlert.getButton(AlertDialog.BUTTON_POSITIVE);
            inButt.setEnabled(false);

            input.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean hasFocus) {
                    if (hasFocus) {
//                        Bring up the keyboard
                        Objects.requireNonNull(inAlert.getWindow()).setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
                    }
                }
            });

            input.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
//                    Only allow ID to be saved if it is a reasonable length
                    if (s.length() > 3 && s.length() < 10) {
                        inButt.setEnabled(true);
                    } else {
                        inButt.setEnabled(false);
                    }
                }

                @Override
                public void afterTextChanged(Editable s) {}
            });
        }

        inputNo++;
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
                .setPositiveButton(R.string.yesButt, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        sendIntentBack(false);
                    }
                })
                .setNegativeButton(R.string.noButt, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
//                        Do nothing.
                    }
                })
                .setCancelable(false).create().show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        selectingAmb = false;
    }
}
