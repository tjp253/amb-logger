package uk.ac.nottingham.eaxtp1.CradleRideLogger;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.support.v4.content.LocalBroadcastManager;
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

    static boolean selectingAmb, forcedStopAmb;
    Button butt1, butt2, butt3, butt4, buttOther, buttSame;
    TextView titleView, asView;
    int transInt, emergeInt, inputNo;
    boolean patBool;

    SharedPreferences ambPref;
    SharedPreferences.Editor prefEd;
    ContextThemeWrapper dialogWrapper = new ContextThemeWrapper(this, R.style.MyAlertDialog);

    static final String keyAmb = "PrefAmb", keyTroll = "PrefTroll", keyPat = "PrefPat",
            keyTrans = "PrefTrans", keyEmerge = "PrefEmerge";
    final String keyDate = "PrefDate";
    String prefStr;
    String[] ambArray, trollArray;

    boolean patient;
    int intOne, intTwo, strID;
    Intent startGPS;

    CountDownTimer buttPause;

    AlertDialog inAlert;

    @SuppressLint({"CommitPrefEdits", "ApplySharedPref"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_amb_select);

        forcedStopAmb = this.getIntent().getBooleanExtra(getString(R.string.forcedIntent),false);

        selectingAmb = true;
        ambPref = getSharedPreferences(getString(R.string.pref_amb), MODE_PRIVATE);
        prefEd = ambPref.edit();

        titleView = findViewById(R.id.optTitle);
        asView = findViewById(R.id.forcedText);

        butt1 = findViewById(R.id.opt1);
        butt2 = findViewById(R.id.opt2);
        butt3 = findViewById(R.id.opt3);
        butt4 = findViewById(R.id.opt4);
        buttOther = findViewById(R.id.optOther);
        buttSame = findViewById(R.id.optSame);

        butt1.setOnClickListener(this);
        butt2.setOnClickListener(this);
        butt3.setOnClickListener(this);
        butt4.setOnClickListener(this);
        buttOther.setOnClickListener(this);
        buttSame.setOnClickListener(this);

        if (ambPref.getInt(keyDate,0) != Calendar.getInstance().get(Calendar.DAY_OF_YEAR)) {
            buttSame.setVisibility(View.GONE);
        }

        buttPause = new CountDownTimer(500,500) {
            @Override
            public void onTick(long millisUntilFinished) {}

            @Override
            public void onFinish() {
                if (!recording && !forcedStopAmb) {
                    changeButts();
                } else {
                    setupQuestion(false);
                }
            }
        };

        if (recording || forcedStopAmb) {
            butt3.setVisibility(View.INVISIBLE);
            butt4.setVisibility(View.INVISIBLE);
            buttSame.setVisibility(View.GONE);
            buttOther.setText(R.string.butt_cancel);
            if (forcedStopAmb) {
                buttOther.setVisibility(View.INVISIBLE);
                asView.setVisibility(View.VISIBLE);
            }
            patient = ambPref.getBoolean(keyPat, false);
            setupQuestion(patient);
            if (!patient) {
                prefEd.putString(keyTrans,"N/A").commit();
            }
        } else {
            startGPS = new Intent(getApplicationContext(), AmbGPSService.class);
            startService(startGPS);
            int nttInt = ambPref.getInt(getString(R.string.key_pref_ntt),0);
            int ambChoice = Integer.valueOf( Objects.requireNonNull(getResources().obtainTypedArray(R.array.amb_ntt).getString(nttInt)).substring((1)) );
            int trollChoice = Integer.valueOf( Objects.requireNonNull(getResources().obtainTypedArray(R.array.troll_ntt).getString(nttInt)).substring((1)) );
            ambArray = getResources().getStringArray(ambChoice);
            trollArray = getResources().getStringArray(trollChoice);

            changeButts();
        }

    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.opt1: storeAmb(1);    break;
            case R.id.opt2: storeAmb(2);    break;
            case R.id.opt3: storeAmb(3);    break;
            case R.id.opt4: storeAmb(4);    break;
            case R.id.optOther: getOther(); break;
            case R.id.optSame: keepEntry(); break;
        }
    }

    public void changeButts() {
        switch (inputNo) {
            case 0:
                titleView.setText(R.string.ambTit);
                butt1.setText(ambArray[0]);
                butt2.setText(ambArray[1]);
                butt3.setText(ambArray[2]);
                butt4.setText(ambArray[3]);
                break;
            case 1:
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
            case 2:
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

    public void setupQuestion(boolean bool) {
        if (bool) {
            titleView.setText(R.string.transTit);
            butt1.setText(R.string.transReasonOne);
            butt2.setText(R.string.transReasonTwo);
        } else {
            titleView.setText(R.string.emergeTit);
            butt1.setText(R.string.yesButt);
            butt2.setText(R.string.noButt);
        }
    }

    public void storeAmb(int optionNo) {
        if (!(recording || forcedStopAmb)) {   // Selections before journey.
            buttPause.start();
            switch (inputNo) {
                case 0:
                    prefEd.putString(keyAmb, ambArray[optionNo-1]).commit();
                    break;
                case 1:
                    prefEd.putString(keyTroll, trollArray[optionNo-1]).commit();
                    break;
                case 2:
                    patBool = optionNo == 1;
                    prefEd.putBoolean(keyPat, patBool).commit();
                    prefEd.putInt(keyDate, Calendar.getInstance().get(Calendar.DAY_OF_YEAR)).commit();
                    sendIntentBack(true);
                    break;
            }
        } else {    // Selections after journey.
            if (patient) {
                buttPause.start();
                switch (optionNo) {
                    case 1: transInt = R.string.transReasonOne; break;
                    case 2: transInt = R.string.transReasonTwo; break;
                    case 5: finish();   break;
                }
                prefEd.putString(keyTrans, getString(transInt));
                patient = false;
            } else {
                switch (optionNo) {
                    case 1: emergeInt = R.string.yesButt; break;
                    case 2: emergeInt = R.string.noButt; break;
                    case 5: finish();   break;
                }
                prefEd.putString(keyEmerge, getString(emergeInt)).commit();
                sendIntentBack(true);
            }
        }

        inputNo++;
    }

    public void sendIntentBack(boolean bool) {
        Intent returnIntent = new Intent().putExtra(ambExtra, bool);
        setResult(Activity.RESULT_OK, returnIntent);
        if (buttPause != null) {
            buttPause.cancel();
        }

//        If forced to stop (due to inactivity) this refreshes the MainActivity
        if (forcedStopAmb) {
            Intent intent = new Intent(loggingFilter);
            intent.putExtra(loggingInt, 9);
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
        }

        finish();
    }

    public void keepEntry() {
        buttPause.start();
        inputNo++;
    }

    public void getOther() {
        if (recording) {
            storeAmb(5);
        } else {
            AlertDialog.Builder builder = new AlertDialog.Builder(dialogWrapper);
            View inputView = View.inflate(this, R.layout.amb_input, null);
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
                            prefEd.putString(prefStr, input.getText().toString()).commit();
                            changeButts();
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
                        Objects.requireNonNull(inAlert.getWindow()).setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
                    }
                }
            });

            input.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    if (s.length() > 3) {
                        inButt.setEnabled(true);
                    } else {
                        inButt.setEnabled(false);
                    }
                }

                @Override
                public void afterTextChanged(Editable s) {
                }
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
                changeButts();
            } else {
                patient = true;
                setupQuestion(true);
            }
        }
        // Do nothing
    }

    public void confirmExit() {
        AlertDialog.Builder builder = new AlertDialog.Builder(dialogWrapper);
        builder .setTitle("Cancel Recording")
                .setPositiveButton(R.string.yesButt, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        stopService(startGPS);
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
