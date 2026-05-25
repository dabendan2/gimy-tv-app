package com.gimytv.horror;

public class Log {
    private static boolean useAndroidLog = true;
    static {
        try {
            Class.forName("android.util.Log");
        } catch (ClassNotFoundException e) {
            useAndroidLog = false;
        }
    }

    public static void i(String tag, String msg) {
        if (useAndroidLog) {
            android.util.Log.i(tag, msg);
        } else {
            System.out.println("[INFO] [" + tag + "] " + msg);
        }
    }

    public static void d(String tag, String msg) {
        if (useAndroidLog) {
            android.util.Log.d(tag, msg);
        } else {
            System.out.println("[DEBUG] [" + tag + "] " + msg);
        }
    }

    public static void w(String tag, String msg) {
        if (useAndroidLog) {
            android.util.Log.w(tag, msg);
        } else {
            System.out.println("[WARN] [" + tag + "] " + msg);
        }
    }

    public static void e(String tag, String msg) {
        if (useAndroidLog) {
            android.util.Log.e(tag, msg);
        } else {
            System.err.println("[ERROR] [" + tag + "] " + msg);
        }
    }

    public static void e(String tag, String msg, Throwable tr) {
        if (useAndroidLog) {
            android.util.Log.e(tag, msg, tr);
        } else {
            System.err.println("[ERROR] [" + tag + "] " + msg);
            if (tr != null) {
                tr.printStackTrace();
            }
        }
    }
}
