package com.gimytv.horror;

import android.content.Context;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.media.MediaMetadata;
import android.os.SystemClock;
import android.media.AudioManager;
import android.media.VolumeProvider;

public class GimyMediaSession {
    private static final String TAG = "GimyHorror_Media";

    public interface PlaybackController {
        void onPlayAction();
        void onPauseAction();
        void onStopAction();
        void onSeekToAction(long pos);
        int getCurrentPosition();
    }

    private final Context context;
    private MediaSession mediaSession;
    private final PlaybackController controller;

    public GimyMediaSession(Context context, PlaybackController controller) {
        this.context = context;
        this.controller = controller;
        initMediaSession();
    }

    private void initMediaSession() {
        Log.i(TAG, "Initializing MediaSession for Gimy TV App.");
        mediaSession = new MediaSession(context, "GimyTV");
        mediaSession.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        mediaSession.setCallback(new MediaSession.Callback() {
            @Override
            public void onPlay() {
                Log.i(TAG, "MediaSession callback: onPlay() received.");
                if (controller != null) {
                    controller.onPlayAction();
                }
            }

            @Override
            public void onPause() {
                Log.i(TAG, "MediaSession callback: onPause() received.");
                if (controller != null) {
                    controller.onPauseAction();
                }
            }

            @Override
            public void onStop() {
                Log.i(TAG, "MediaSession callback: onStop() received.");
                if (controller != null) {
                    controller.onStopAction();
                }
            }

            @Override
            public void onSeekTo(long pos) {
                Log.i(TAG, "MediaSession callback: onSeekTo(" + pos + ") received.");
                if (controller != null) {
                    controller.onSeekToAction(pos);
                }
            }
        });

        // Remote Volume Provider (enables Wi-Fi connected mobile phones and Cast notifications
        // to control the TV's system volume via hardware keys and notification sliders!)
        final android.media.AudioManager am = (android.media.AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        int maxVol = am.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC);
        int currentVol = am.getStreamVolume(android.media.AudioManager.STREAM_MUSIC);

        // Using VOLUME_CONTROL_ABSOLUTE matches the 1:1 hardware steps (usually 0 to 15)
        // on Google TV / Chromecast and completely eliminates the "TV does not support volume adjustment" warning.
        VolumeProvider volumeProvider = new VolumeProvider(VolumeProvider.VOLUME_CONTROL_ABSOLUTE, maxVol, currentVol) {
            @Override
            public void onSetVolumeTo(int volume) {
                Log.i(TAG, "VolumeProvider onSetVolumeTo received. Volume: " + volume);
                am.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, volume, android.media.AudioManager.FLAG_SHOW_UI);
                setCurrentVolume(volume);
            }

            @Override
            public void onAdjustVolume(int direction) {
                Log.i(TAG, "VolumeProvider onAdjustVolume received. Direction: " + direction);
                if (direction > 0) {
                    am.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC, android.media.AudioManager.ADJUST_RAISE, android.media.AudioManager.FLAG_SHOW_UI);
                } else if (direction < 0) {
                    am.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC, android.media.AudioManager.ADJUST_LOWER, android.media.AudioManager.FLAG_SHOW_UI);
                }
                int cur = am.getStreamVolume(android.media.AudioManager.STREAM_MUSIC);
                setCurrentVolume(cur);
                Log.d(TAG, "VolumeProvider: updated current volume to " + cur + "/" + maxVol);
            }
        };
        mediaSession.setPlaybackToRemote(volumeProvider);
    }

    public void setActive(boolean active) {
        Log.i(TAG, "MediaSession setActive(" + active + ") called.");
        if (mediaSession != null) {
            mediaSession.setActive(active);
        }
    }

    public void updatePlaybackState(int state) {
        Log.d(TAG, "MediaSession updatePlaybackState(" + state + ") called.");
        if (mediaSession != null) {
            long actions = PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_STOP | PlaybackState.ACTION_SEEK_TO;
            long position = (controller != null) ? controller.getCurrentPosition() : 0;
            float speed = (state == PlaybackState.STATE_PLAYING) ? 1.0f : 0.0f;
            PlaybackState.Builder stateBuilder = new PlaybackState.Builder()
                .setActions(actions)
                .setState(state, position, speed, SystemClock.elapsedRealtime());
            mediaSession.setPlaybackState(stateBuilder.build());
        }
    }

    private android.graphics.Bitmap scaleBitmap(android.graphics.Bitmap src, int maxDimension) {
        if (src == null) return null;
        int width = src.getWidth();
        int height = src.getHeight();
        if (width <= maxDimension && height <= maxDimension) {
            return src;
        }
        float ratio = (float) width / (float) height;
        int newWidth, newHeight;
        if (width > height) {
            newWidth = maxDimension;
            newHeight = Math.round(maxDimension / ratio);
        } else {
            newHeight = maxDimension;
            newWidth = Math.round(maxDimension * ratio);
        }
        try {
            return android.graphics.Bitmap.createScaledBitmap(src, newWidth, newHeight, true);
        } catch (Exception e) {
            Log.e(TAG, "scaleBitmap Exception: failed to scale album art cover bitmap.", e);
            return src;
        }
    }

    public void updateMediaMetadata(String title, long durationMs) {
        updateMediaMetadata(title, durationMs, null, null);
    }

    public void updateMediaMetadata(final String title, final long durationMs, final android.graphics.Bitmap coverBitmap) {
        updateMediaMetadata(title, durationMs, coverBitmap, null);
    }

    public void updateMediaMetadata(final String title, final long durationMs, final android.graphics.Bitmap coverBitmap, final String imageUrl) {
        Log.i(TAG, "MediaSession updateMediaMetadata: Title=" + title + " | Duration=" + durationMs + " ms | Bitmap=" + (coverBitmap != null ? (coverBitmap.getWidth() + "x" + coverBitmap.getHeight()) : "null") + " | URL=" + imageUrl);
        if (mediaSession != null) {
            MediaMetadata.Builder metaBuilder = new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, title)
                .putString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE, title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, "Gimy 鬼魅劇場")
                .putString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE, "Gimy 鬼魅劇場")
                .putString(MediaMetadata.METADATA_KEY_ALBUM, "Gimy 鬼魅劇場")
                .putLong(MediaMetadata.METADATA_KEY_DURATION, durationMs);

            if (imageUrl != null && !imageUrl.isEmpty()) {
                String formattedUrl = imageUrl.trim();
                if (formattedUrl.startsWith("//")) {
                    formattedUrl = "https:" + formattedUrl;
                } else if (formattedUrl.startsWith("/")) {
                    formattedUrl = "https://gimyplus.com" + formattedUrl;
                }
                Log.d(TAG, "MediaSession: Putting artwork URIs: " + formattedUrl);
                metaBuilder.putString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI, formattedUrl);
                metaBuilder.putString(MediaMetadata.METADATA_KEY_ART_URI, formattedUrl);
                metaBuilder.putString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI, formattedUrl);
            }

            if (coverBitmap != null) {
                android.graphics.Bitmap scaled = scaleBitmap(coverBitmap, 320); // 320px is perfect for low-latency Binder transit
                if (scaled != null) {
                    Log.d(TAG, "MediaSession: Putting scaled bitmap of size " + scaled.getWidth() + "x" + scaled.getHeight());
                    metaBuilder.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, scaled);
                    metaBuilder.putBitmap(MediaMetadata.METADATA_KEY_ART, scaled);
                    metaBuilder.putBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON, scaled);
                }
            }
            mediaSession.setMetadata(metaBuilder.build());
        }
    }

    public void release() {
        Log.i(TAG, "Releasing MediaSession.");
        if (mediaSession != null) {
            mediaSession.release();
            mediaSession = null;
        }
    }
}
