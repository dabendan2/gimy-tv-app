# Gimy TV App - MediaSession, Volume Control, & Logging Quirks

This reference document preserves the session-specific diagnostics, code implementations, and system-level troubleshooting for the logging and remote volume control challenges encountered in the Gimy TV App.

---

## 1. JVM `NoClassDefFoundError: android/util/Log` in Unit Tests

### The Problem
During development, the automated build script (`scripts/build_apk.py`) runs unit tests (`com.gimytv.horror.TestRunner`) natively on the host JVM to enforce Test-Driven Development (TDD) before compiling. 
However, because the local JVM classpath does not contain the Android SDK, any class referencing `android.util.Log` throws a runtime crash:
```log
Executing GimyParser Unit Tests...
  [FAIL] GimyParser tests failed!
java.lang.NoClassDefFoundError: android/util/Log
	at com.gimytv.horror.GimyParser.parseMoviesFromHtml(GimyParser.java:106)
	at com.gimytv.horror.GimyParserTest.testParseMoviesFromHtml(GimyParserTest.java:35)
Caused by: java.lang.ClassNotFoundException: android.util.Log
```

### The Solution: Reflection-Based Log Wrapper
We introduced a package-private `Log.java` class that uses class-loading reflection to dynamically detect whether it is executing under a real Android OS or a vanilla JVM, seamlessly falling back to formatted stdout console prints without requiring heavy mocking frameworks:

```java
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
```

---

## 2. "TV Does Not Support Volume Adjustment" (TV不支援變更音量)

When broadcasting media control notifications from Android TV over the local Wi-Fi, other Android devices (phones, tablets, Google Home) may display the warning **"TV does not support volume adjustment"** or disable the volume slider. This issue has two root causes:

### Cause A: Playback Volume Mode in MediaSession
*   **The Issue**: If the `MediaSession` declares relative volume controls (`VOLUME_CONTROL_RELATIVE`), Google Cast notifications / Google Home cannot render an absolute seek bar slider, causing Google Play Services to flag the Cast session as volume-disabled.
*   **The Fix**: Use `VOLUME_CONTROL_ABSOLUTE` and map the max/current scale directly 1:1 to the TV's physical stream `STREAM_MUSIC` (which uses a standard scale of `0` to `15` steps):

```java
final android.media.AudioManager am = (android.media.AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
int maxVol = am.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC);
int currentVol = am.getStreamVolume(android.media.AudioManager.STREAM_MUSIC);

VolumeProvider volumeProvider = new VolumeProvider(VolumeProvider.VOLUME_CONTROL_ABSOLUTE, maxVol, currentVol) {
    @Override
    public void onSetVolumeTo(int volume) {
        // Absolute slider dragging
        am.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, volume, android.media.AudioManager.FLAG_SHOW_UI);
        setCurrentVolume(volume);
    }

    @Override
    public void onAdjustVolume(int direction) {
        // Phone physical volume key press micro-steps
        if (direction > 0) {
            am.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC, android.media.AudioManager.ADJUST_RAISE, android.media.AudioManager.FLAG_SHOW_UI);
        } else if (direction < 0) {
            am.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC, android.media.AudioManager.ADJUST_LOWER, android.media.AudioManager.FLAG_SHOW_UI);
        }
        int cur = am.getStreamVolume(android.media.AudioManager.STREAM_MUSIC);
        setCurrentVolume(cur);
    }
};
mediaSession.setPlaybackToRemote(volumeProvider);
```

### Cause B: Television Remote Audio Control Settings (CEC/IR Mode)
*   **The Issue**: If the TV's Google TV Streamer voice remote is set to control the physical TV, Soundbar, or Receiver via **HDMI-CEC** or **Infrared (IR)**, the operating system locks and disables the digital system volume entirely. This locks `STREAM_MUSIC` to fixed 100% and transmits a "volume control disabled" flag to Google Play Services over Cast.
*   **The Workaround**: The user must manually configure the Google TV remote to control the streaming device's system volume instead of the external TV or audio equipment:
    1. Go to Google TV **Settings** ➔ **Remotes & Accessories** ➔ **Set up remote buttons**.
    2. Select **Volume control**.
    3. Change from **Auto (CEC)** or **TV (IR)** to **Google TV Streamer**.
    Once completed, the digital audio pipeline is unlocked, and phone Cast sliders and rockers will immediately work without any "not supported" warning!
