# Diagnostic: "TV does not support volume adjustment" on Android TV / Google TV Streamer

When casting or managing media sessions on Android TV devices (including Chromecast with Google TV and Google TV Streamer 4K) from Wi-Fi connected Android phones, the phone's lock screen or notification drawer volume bar may display:
> "TV does not support volume adjustment" (or 「TV不支援變更音量」 in Chinese)

This reference document explains the exact root cause, how Google's Cast protocol handles volume, and how to resolve it.

---

## 1. Root Cause: Physical CEC / IR Passthrough vs. Digital Volume

Modern Android TV devices allow mapping the physical Voice Remote's volume buttons in three ways:
1.  **HDMI-CEC (Consumer Electronics Control)**: The remote sends commands via Bluetooth to the Chromecast, which forwards them over HDMI to the TV/Soundbar to change its physical hardware volume.
2.  **Infrared (IR)**: The remote's built-in IR blaster directly sends infrared signals to the TV/Soundbar, bypassing the Chromecast entirely for volume.
3.  **Google TV Streamer / Digital Volume**: The remote controls the Chromecast's internal Android digital audio mixer stream (`STREAM_MUSIC`).

### The Conflict
If the TV is configured to use **HDMI-CEC** or **IR** for volume control:
*   The Android TV OS disables and locks its internal digital system volume (`STREAM_MUSIC` is fixed at 100%).
*   Because the TV OS itself cannot digitally modify the decibels of the audio output, its background Google Cast receiver (`mediashell`) broadcasts a flag to all Wi-Fi connected devices: **`volume_control_type: fixed`**.
*   Upon receiving this flag, Google Play Services on the connected mobile phones disables the volume slider and locks the hardware volume rocker, displaying **"TV does not support volume adjustment"**.

---

## 2. Resolution: Configuring Google TV for Digital Volume Control

To allow mobile phones to control the TV's volume over Wi-Fi, the volume control of the streaming device must be set back to digital system volume:

1.  Use the TV remote to navigate to the Google TV **Settings (齒輪)** on the top right.
2.  Go to **Remotes & Accessories (遙控器與配件)**.
3.  Select **Set up remote buttons (設定遙控器按鍵)**.
4.  Select **Volume control (音量控制)**.
5.  Change the selection from **Auto (CEC)** or **TV (IR)** to **Google TV Streamer (or Chromecast)**.

### Result
*   The TV OS immediately unlocks the digital `STREAM_MUSIC` mixer.
*   The Cast receiver updates its broadcast flag to **`volume_control_type: absolute`**.
*   The phone's notification slider immediately becomes active and fully functional.

---

## 3. Developer Guidance: MediaSession Volume Routing

When writing native Android TV applications, the absolute best practice is to route volume to the local stream:

```java
android.media.AudioAttributes attrs = new android.media.AudioAttributes.Builder()
        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MOVIE)
        .build();
mediaSession.setPlaybackToLocal(attrs);
```

### Why avoid custom `VolumeProvider` hacks?
While developers often resort to setting `setPlaybackToRemote(volumeProvider)` with manual `AudioManager` volume hooks to force phone volume keys to work under IR/CEC, **this does not bypass Google Play Services' security lock on the phone side**. The phone will still show the "TV does not support volume" message if the TV's setting is in CEC/IR mode.
Once the TV is switched to "Google TV Streamer" mode, `setPlaybackToLocal(attrs)` is 100% sufficient and natively bridges phone volume sliders to the TV's system volume without any custom code.
