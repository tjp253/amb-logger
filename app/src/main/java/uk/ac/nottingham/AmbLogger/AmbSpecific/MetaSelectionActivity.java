package uk.ac.nottingham.AmbLogger.AmbSpecific;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static uk.ac.nottingham.AmbLogger.MainActivity.LOGGING_BROADCAST_AMB;
import static uk.ac.nottingham.AmbLogger.MainActivity.ambExtra;
import static uk.ac.nottingham.AmbLogger.MainActivity.loggingFilter;
import static uk.ac.nottingham.AmbLogger.MainActivity.loggingInt;
import static uk.ac.nottingham.AmbLogger.MainActivity.phoneDead;
import static uk.ac.nottingham.AmbLogger.MainActivity.recording;

import androidx.constraintlayout.widget.ConstraintLayout;

import uk.ac.nottingham.AmbLogger.R;
import uk.ac.nottingham.AmbLogger.Widgets.EditTextDefault;

public class MetaSelectionActivity extends Activity implements View.OnClickListener {

//    Activity for user to input which ambulance / trolley was used for the journey, whether a
// baby was on board, whether emergency driving was used, and the reason for transfer.

    Resources res;

    Button buttOther, buttSame;
    Button[] allButts = new Button[6];
    TextView titleView, asView;

    // Define integer values for the different inputs
    final int inMode = -1, inMan = 0, inEng = 1, inTroll = 2, inBaby = 3;
    int inputIndex = 0;
    List<Integer> inputList = new ArrayList<>(); // container for the input options, in order
    List<Integer> manEngList = Arrays.asList(inMan, inEng); // for easy addition & removal

    // Declare AMB-specific preferences and variables.
    SharedPreferences pref;
    SharedPreferences.Editor prefEd;
    public static boolean selectingAmb;
    public static boolean forcedStopAmb;

    ContextThemeWrapper dialogWrapper = new ContextThemeWrapper(this, R.style.MyAlertDialog);

    final String keyDate = "PrefDate";
    String[] trollArray = new String[0];
    boolean displayTrollInputRow;
    ConstraintLayout trollInput;

    // TRANSPORT MODES CLASS OPTIONS
    List<String> chosenModesIndices;
    String mode_road, mode_heli, mode_plane;

    boolean patient, resuscitated, sameDayRecording;
    int viewSame = View.INVISIBLE, viewOther = View.VISIBLE;

    boolean startOfRecording = !(recording || forcedStopAmb || phoneDead);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_amb_select);

        resuscitated = this.getIntent().getBooleanExtra(getString(R.string.extra_dead),false);

        forcedStopAmb = this.getIntent().getBooleanExtra(getString(R.string.forcedIntent),false);

        selectingAmb = true;
        pref = PreferenceManager.getDefaultSharedPreferences(this);
        prefEd = pref.edit();

//        Initialise textboxes and buttons
        titleView = findViewById(R.id.optTitle); // FOR THE OPTION QUESTION, AT TOP
        asView    = findViewById(R.id.forcedText); // APPEARS AT BOTTOM IF AUTOSTOP KICKED IN
        buttOther = findViewById(R.id.optOther); // EITHER FULL WIDTH OR ON THE RIGHT
        buttSame  = findViewById(R.id.optSame); // APPEARS ON THE LEFT

        trollInput = findViewById(R.id.input_row);
        trollInput.setVisibility(View.INVISIBLE);

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
        sameDayRecording = pref.getInt(keyDate,0) == Calendar.getInstance().get(Calendar.DAY_OF_YEAR);
        if (sameDayRecording) {
            viewSame = View.VISIBLE;
        }

        res = getResources();

        if (startOfRecording) { // Ask for transport-start data
            // Start GPS initialising
            startService(new Intent(getApplicationContext(), InitialiseGPS.class));

            // FIRST, IDENTIFY THE AVAILABLE TRANSPORT MODES
            String[] allModesIndices = res.getStringArray(R.array.transport_modes_indices);
            chosenModesIndices = new ArrayList<>(
                pref.getStringSet(
                    getString(R.string.key_modes), new HashSet<>(Arrays.asList(allModesIndices))
                )
            );
            mode_road = res.getString(R.string.mode_road);
            mode_heli = res.getString(R.string.mode_heli);
            mode_plane = res.getString(R.string.mode_plane);
            if (chosenModesIndices.size() > 1) { // Multiple transport modes available
                inputList.add(inMode);
            } else if (modeIsRoad()) {
                // want to ask the manufacturer and engine type
                inputList.addAll(manEngList);
            }
            // finally, add the trolley and patient options
            inputList.addAll(Arrays.asList(inTroll, inBaby));

            // Grab the stored trolley codes (if they exist)
            Set<String> trolleySet = pref.getStringSet(getString(R.string.key_troll_options), null);
            if (trolleySet == null) { // grab the CenTre trolleys as default
                trollArray = res.getStringArray(R.array.troll_centre);
            } else { // sort the inputted trolleys
                trollArray = new ArrayList<>(trolleySet).toArray(trollArray);
                Arrays.sort(trollArray);
            }
            displayTrollInputRow = trollArray.length == 0;
            if (displayTrollInputRow) {
                setupInputRow();
            }

            changeButtsStart();  // Setup the question and buttons

        } else {   // Ask for transport-end data

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
                    pref.getString(getString(R.string.key_bob), res.getString(R.string.noButt)),
                    res.getString(R.string.yesButt)
            );
            changeButtsEnd(patient);
            if (!patient) {
                prefEd.putString(getString(R.string.key_trans),"N/A").apply();
            }

        }

    }

    public void setupInputRow() {
        Button submitTextButt = trollInput.findViewById(R.id.row_butt);
        EditTextDefault editText = trollInput.findViewById(R.id.row_input);

        editText.setTextColor(res.getColor(R.color.colorBlack, null));

        submitTextButt.setText(R.string.butt_ok);
        submitTextButt.setEnabled(false);

        submitTextButt.setOnClickListener(view -> {
            //noinspection ConstantConditions - The below TextChangedListener ensures NonNull
            storeAmb(editText.getText().toString());
            trollInput.setVisibility(View.INVISIBLE);
        });

        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Only allow ID to be saved if a value exists
                submitTextButt.setEnabled(s.length() > 0);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
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
        switch (inputList.get(inputIndex)) {  // Which question is to be answered?

            case inMode: // Ask for the mode of transport
                titleView.setText(R.string.modeTit);

                // grab all modes
                String[] allModes = res.getStringArray(R.array.transport_modes);
                Collections.sort(chosenModesIndices); // sort the order
                List<String> chosenModes = new ArrayList<>();
                for (String ind : chosenModesIndices) {
                    chosenModes.add(allModes[Integer.parseInt(ind)]);
                }

                changeButts(chosenModes.toArray(new String[0]), viewSame, viewOther);
                break;

            case inMan: // Ask for ambulance manufacturer
                titleView.setText(R.string.manTit);
                changeButts(res.getStringArray(R.array.amb_manufacturer), viewSame, viewOther);
                break;

            case inEng: // Ask for ambulance engine type
                titleView.setText(R.string.engTit);
                changeButts(res.getStringArray(R.array.amb_engine), viewSame, viewOther);
                break;

            case inTroll: // Ask for trolley ID
                // if not same as before, open input dialog automatically (but change "cancel" to "return")
                titleView.setText(R.string.trollTit);
                changeButts(trollArray, viewSame, View.VISIBLE);
                if (displayTrollInputRow) { // Ask for input immediately
                    trollInput.setVisibility(View.VISIBLE);
                }
                break;

            case inBaby: // Ask whether a baby is on board
                titleView.setText(R.string.patTit);
                changeButts(res.getStringArray(R.array.boolean_options), View.INVISIBLE, View.VISIBLE);
                break;
        }
    }

//    Set up inputs at end of recording
    public void changeButtsEnd(boolean baby) {
        if (baby) { // Ask reason for transfer
            titleView.setText(R.string.transTit);
            changeButts(res.getStringArray(R.array.reason_options), View.GONE, viewOther);
        } else {    // Ask about emergency driving
            titleView.setText(R.string.emergeTit);
            changeButts(res.getStringArray(R.array.boolean_options), View.GONE, viewOther);
        }
    }

    public boolean modeIsRoad() { // return whether the MODE is ROAD
        return mode_road.equals(pref.getString(getString(R.string.key_mode), ""));
    }

//    Store input choices in preferences for logging and to speed up future inputs (SLIGHTLY)
    public void storeAmb(String metaText) {
        if (startOfRecording) {   // Selections before journey.
            buttPause.start();
            switch (inputList.get(inputIndex)) {

                case inMode: // Store the transport mode
                    prefEd.putString(getString(R.string.key_mode), metaText).apply();
                    if (modeIsRoad()) { // add the manufacturer and engine options
                        inputList.addAll(1, manEngList);
                    }
                    break;

                case inMan: // Store ambulance manufacturer
                    prefEd.putString(getString(R.string.key_man), metaText).apply();
                    break;

                case inEng: // Store ambulance engine type
                    prefEd.putString(getString(R.string.key_eng), metaText).apply();
                    break;

                case inTroll: // Store trolley ID
                    prefEd.putString(getString(R.string.key_troll), metaText).apply();
                    break;

                case inBaby: // Store baby presence
                    prefEd.putString(getString(R.string.key_bob), metaText).apply();
                    prefEd.putInt(keyDate, Calendar.getInstance().get(Calendar.DAY_OF_YEAR)).apply();
                    sendIntentBack(true);   // Start the full recording
                    return;

            }
        } else {    // Selections after journey.
            if (patient) {
                buttPause.start();
                prefEd.putString(getString(R.string.key_trans), metaText);
                patient = false;
            } else {
                prefEd.putString(getString(R.string.key_emerge), metaText).apply();
                sendIntentBack(true);
                return;
            }
        }

        inputIndex++;  // How many questions have now been answered?

    }

    @Override
    public void onClick(View v) {   // Store different results depending on button pressed
        int vID = v.getId();
        if (vID == R.id.optOther) {
            getOther();
        } else if (vID == R.id.optSame) {
            // Don't change the preference value
            buttPause.start();
            inputIndex++;
        } else {
            storeAmb( ((Button) v).getText().toString().replace("\n", "-") );
        }
    }

// Timer to allow a slight pause between input changes. Otherwise the user may doubt they've
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
            intent.putExtra(loggingInt, LOGGING_BROADCAST_AMB);
            sendBroadcast(intent);
        }

        finish();
    }

    public void getOther() {
        if (resuscitated) {
            storeAmb("UNKNOWN"); // Enter in 'Unknown'
            inputIndex++;

        } else if (recording) {
            finish();   // Cancel selection and return to recording

        } else { // Act as Back Button

            onBackPressed();

        }
    }

    @Override
    public void onBackPressed() {
        if (!recording && inputIndex == 0) { // Confirm user wants to return to home screen and cancel recording.

            confirmExit();

        } else if (inputIndex > 0) { // Change buttons back to previous selection

            inputIndex--;
            if (!recording) { // starting options
                if (modeIsRoad() && inputList.get(inputIndex) == inMode) {
                    // remove the manufacturer and engine options, in case user chooses other mode
                    inputList.removeAll(manEngList);
                } else if (inputList.get(inputIndex + 1) == inTroll && displayTrollInputRow) {
                    trollInput.setVisibility(View.INVISIBLE); // Hide the input row
                }
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
