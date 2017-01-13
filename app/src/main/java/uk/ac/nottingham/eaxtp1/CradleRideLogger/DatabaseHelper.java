package uk.ac.nottingham.eaxtp1.CradleRideLogger;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.text.SimpleDateFormat;
import java.util.Date;

//  Helps to write the database!!|

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_TITLE = "VibData_";
    private static final String DATABASE_TYPE = ".db";
    private static final String TABLE_NAME = "vibration_table";
    private static final String COL_1 = "ID";
    private static final String COL_2 = "EAST_ACC";
    private static final String COL_3 = "NORTH_ACC";
    private static final String COL_4 = "DOWN_ACC";
    private static final String COL_5 = "LAT";
    private static final String COL_6 = "LONG";
    private static final String COL_7 = "TIME";

    //    Creates a string of the current date and time
    private static SimpleDateFormat dateFormat = new SimpleDateFormat("dd_MM_yyyy__HH_mm");
    private static Date todaysDate = new Date();
    private static String date = dateFormat.format(todaysDate);
    private static String DATABASE_NAME = DATABASE_TITLE + date + DATABASE_TYPE;


//  Creates the database at the start, referencing the start time in the name.
    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, 1);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {

        db.execSQL("create table " + TABLE_NAME +
                " (" + COL_1 + " INTEGER PRIMARY KEY AUTOINCREMENT, " + COL_2 + " FLOAT, " + COL_3 + " FLOAT," + COL_4 + " FLOAT,"
                + COL_5 + " DOUBLE," + COL_6 + " DOUBLE," + COL_7 + " LONG)" );

    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

        db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
        onCreate(db);

    }

    public boolean insertData(Float X_acc, Float Y_acc, Float Z_acc, Double Lat, Double Long, Long Time) {

        SQLiteDatabase db = this.getWritableDatabase();

        ContentValues contentValues = new ContentValues();
        contentValues.put(COL_2, X_acc);
        contentValues.put(COL_3, Y_acc);
        contentValues.put(COL_4, Z_acc);
        contentValues.put(COL_5, Lat);
        contentValues.put(COL_6, Long);
        contentValues.put(COL_7, Time);

        long result = db.insert(TABLE_NAME, null, contentValues);

        return result != -1;

    }

}
