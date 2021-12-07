package uk.ac.nottingham.eaxtp1.CradleRideLogger.Utilities;

import java.util.ArrayList;

public class TextUtils {

    public static String joinCSV(Object[] strings) {
        return android.text.TextUtils.join(",", strings);
    }

    public static String joinCSV(ArrayList<String> strings) {
        return joinCSV(strings.toArray(new String[0]));
    }

    public static String joinNewLine(Object[] strings) {
        return android.text.TextUtils.join("\n", strings);
    }

}
