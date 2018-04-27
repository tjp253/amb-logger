package uk.ac.nottingham.eaxtp1.CradleRideLogger;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.amb;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.troll;
import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.pat;

public class AmbSelect extends Activity implements View.OnClickListener {

    Button butt1, butt2, butt3, butt4, buttOther;
    TextView titleView;

    boolean trolley, patient;
    int intOne, intTwo;

    CountDownTimer buttPause;

    AlertDialog inAlert;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_amb_select);

        titleView = findViewById(R.id.optTitle);

        butt1 = findViewById(R.id.opt1);
        butt2 = findViewById(R.id.opt2);
        butt3 = findViewById(R.id.opt3);
        butt4 = findViewById(R.id.opt4);
        buttOther = findViewById(R.id.optOther);

        butt1.setOnClickListener(this);
        butt2.setOnClickListener(this);
        butt3.setOnClickListener(this);
        butt4.setOnClickListener(this);
        buttOther.setOnClickListener(this);

        buttPause = new CountDownTimer(500,500) {
            @Override
            public void onTick(long millisUntilFinished) {}

            @Override
            public void onFinish() {
                changeButts();
            }
        };

    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.opt1: storeAmb(1);    break;
            case R.id.opt2: storeAmb(2);    break;
            case R.id.opt3: storeAmb(3);    break;
            case R.id.opt4: storeAmb(4);    break;
            case R.id.optOther: getOther(); break;
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
        }
    }

    public void storeAmb(int optionNo) {
        buttPause.start();
        if (trolley) {
            switch (optionNo) {
                case 1: troll = getString(R.string.tro1);   break;
                case 2: troll = getString(R.string.tro2);   break;
                case 3: troll = getString(R.string.tro3);   break;
                case 4: troll = getString(R.string.tro4);   break;
            }
        } else if (patient) {
            switch (optionNo) {
                case 1: pat = getString(R.string.yesButt);  break;
                case 2: pat = getString(R.string.noButt);   break;
            }
            finish();
        } else {
            switch (optionNo) {
                case 1: amb = getString(R.string.amb1); break;
                case 2: amb = getString(R.string.amb2); break;
                case 3: amb = getString(R.string.amb3); break;
                case 4: amb = getString(R.string.amb4); break;
            }
        }
    }

    public void getOther() {
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
            builder .setMessage(R.string.entAmb)
                    .setPositiveButton(R.string.entButt, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            amb = input.getText().toString();
                            changeButts();
                        }
                    });
        } else {
            input2.setVisibility(View.INVISIBLE);
            builder .setMessage(R.string.entTroll)
                    .setPositiveButton(R.string.entButt, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            troll = input.getText().toString();
//                            finish();
                        }
                    });
        }

        builder .setView(inputView)
                .setCancelable(false).create();

        inAlert = builder.show();

        final Button inButt = inAlert.getButton(AlertDialog.BUTTON_POSITIVE);
        inButt.setEnabled(false);

        input.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    inAlert.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
                }
            }
        });

        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.length() > 3) {
                    inButt.setEnabled(true);
                } else {
                    inButt.setEnabled(false);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    @Override
    public void onBackPressed() {
        // Do nothing
    }
}
