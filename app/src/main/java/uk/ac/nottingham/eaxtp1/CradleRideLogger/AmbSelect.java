package uk.ac.nottingham.eaxtp1.CradleRideLogger;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.util.Calendar;
import java.util.Objects;

import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.ambExtra;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.recording;

public class AmbSelect extends Activity implements View.OnClickListener {

    Button butt1, butt2, butt3, butt4, buttOther, buttSame;
    TextView titleView;
    int ambInt, trollInt, transInt, emergeInt;
    boolean patBool;

    SharedPreferences ambPref;
    SharedPreferences.Editor prefEd;

    static final String keyAmb = "PrefAmb", keyTroll = "PrefTroll", keyPat = "PrefPat",
            keyTrans = "PrefTrans", keyEmerge = "PrefEmerge";
    final String keyDate = "PrefDate";

    boolean trolley, patient;
    int intOne, intTwo;

    CountDownTimer buttPause;

    AlertDialog inAlert;

    @SuppressLint("CommitPrefEdits")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_amb_select);

        ambPref = getSharedPreferences(getString(R.string.pref_amb), MODE_PRIVATE);
        prefEd = ambPref.edit();

        titleView = findViewById(R.id.optTitle);

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
                if (!recording) {
                    changeButts();
                } else {
                    setupQuestion(false);
                }
            }
        };

        if (recording) {
            butt3.setVisibility(View.INVISIBLE);
            butt4.setVisibility(View.INVISIBLE);
            buttSame.setVisibility(View.GONE);
            buttOther.setText(R.string.butt_cancel);
            patient = ambPref.getBoolean(keyPat, false);
            setupQuestion(patient);
            if (!patient) {
                prefEd.putString(keyTrans,"N/A").commit();
            }
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
        if (!trolley) {
            trolley = true;
            titleView.setText(R.string.trollTit);
            butt1.setText(R.string.tro1);
            butt2.setText(R.string.tro2);
            butt3.setText(R.string.tro3);
            butt4.setText(R.string.tro4);
        } else {
            trolley = false;    patient = true;
            titleView.setText(R.string.patTit);
            butt1.setText(R.string.yesButt);
            butt2.setText(R.string.noButt);
            butt3.setVisibility(View.INVISIBLE);
            butt4.setVisibility(View.INVISIBLE);
            buttOther.setVisibility(View.INVISIBLE);
            buttSame.setVisibility(View.INVISIBLE);
        }
    }

    public void storeAmb(int optionNo) {
        buttPause.start();
        if (!recording) {
            if (trolley) {
                switch (optionNo) {
                    case 1: trollInt = R.string.tro1;   break;
                    case 2: trollInt = R.string.tro2;   break;
                    case 3: trollInt = R.string.tro3;   break;
                    case 4: trollInt = R.string.tro4;   break;
                }
                prefEd.putString(keyTroll, getString(trollInt)).commit();
            } else if (patient) {
                switch (optionNo) {
                    case 1: patBool = true; break;
                    case 2: patBool = false; break;
                }
                prefEd.putBoolean(keyPat, patBool).commit();
                prefEd.putInt(keyDate, Calendar.getInstance().get(Calendar.DAY_OF_YEAR)).commit();
                sendIntentBack();
            } else {
                switch (optionNo) {
                    case 1: ambInt = R.string.amb1; break;
                    case 2: ambInt = R.string.amb2; break;
                    case 3: ambInt = R.string.amb3; break;
                    case 4: ambInt = R.string.amb4; break;
                }
                prefEd.putString(keyAmb, getString(ambInt)).commit();
            }
        } else {
            if (patient) {
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
                sendIntentBack();
            }
        }
    }

    public void sendIntentBack() {
        Intent returnIntent = new Intent().putExtra(ambExtra, true);
        setResult(Activity.RESULT_OK, returnIntent);
        finish();
    }

    public void keepEntry() {
        if (patient) {
            finish();
        } else {
            buttPause.start();
        }
    }

    public void getOther() {
        if (recording) {
            storeAmb(5);
        } else {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            View inputView = View.inflate(this, R.layout.amb_input, null);
            if (!trolley) {
                intOne = R.id.ambInput;
                intTwo = R.id.trollInput;
            } else {
                intOne = R.id.trollInput;
                intTwo = R.id.ambInput;
            }
            final EditText input = inputView.findViewById(intOne);
            final EditText input2 = inputView.findViewById(intTwo);

            if (!trolley) {
                input2.setVisibility(View.INVISIBLE);
                builder.setMessage(R.string.entAmb)
                        .setPositiveButton(R.string.entButt, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                prefEd.putString(keyAmb, input.getText().toString()).commit();
                                changeButts();
                            }
                        });
            } else {
                input2.setVisibility(View.INVISIBLE);
                builder.setMessage(R.string.entTroll)
                        .setPositiveButton(R.string.entButt, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                prefEd.putString(keyTroll, input.getText().toString()).commit();
                            }
                        });
            }

            builder.setView(inputView)
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
    }

    @Override
    public void onBackPressed() {
        // Do nothing
    }
}
