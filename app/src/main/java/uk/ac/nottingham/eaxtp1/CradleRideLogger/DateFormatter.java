package uk.ac.nottingham.eaxtp1.CradleRideLogger;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.Resources;

import java.text.SimpleDateFormat;
import java.util.Date;

public class DateFormatter extends ContextWrapper {

    /*
    Simple class to handle the creation of a date string for use in the filename.
    Date is formatted in accordance with ISO 8601, both ensuring ordering in file systems
    and ensuring that conversions for processing are near-automatic.
    Method contained in a class to enable access from multiple services, if required.
     */

    public DateFormatter(Context base) {
        super(base);
    }

    Resources res = getResources();

    public String formDate() {
        // Creates a string of the current date and time, to ISO 8601 standard
        @SuppressLint("SimpleDateFormat")
        SimpleDateFormat dateFormat = new SimpleDateFormat(res.getString(R.string.file_date_format));
        Date todayDate = new Date();
        String date = dateFormat.format(todayDate);
        date = date.replace(" ", "T"); // cannot include 'T' initially
        return date;
    }
}
