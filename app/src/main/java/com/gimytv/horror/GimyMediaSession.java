package com.gimytv.horror;

import android.content.Context;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.media.MediaMetadata;
import android.os.SystemClock;
import android.media.AudioManager;
import android.media.VolumeProvider;

public class GimyMediaSession {

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
        mediaSession = new MediaSession(context, "GimyTV");
        mediaSession.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        mediaSession.setCallback(new MediaSession.Callback() {
            @Override
            public void onPlay() {
                if (controller != null) {
                    controller.onPlayAction();
                }
            }

            @Override
            public void onPause() {
                if (controller != null) {
                    controller.onPauseAction();
                }
            }

            @Override
            public void onStop() {
                if (controller != null) {
                    controller.onStopAction();
                }
            }

            @Override
            public void onSeekTo(long pos) {
                if (controller != null) {
                    controller.onSeekToAction(pos);
                }
            }
        });

        // Local Volume Routing (tells the system playback is local STREAM_MUSIC)
        // This is critical because it enables the TV's built-in Cast Shell (mediashell)
        // to detect local playback, broadcast it to the Wi-Fi network, and automatically
        // delegate volume actions from mobile phones to the TV's system volume!
        android.media.AudioAttributes attrs = new android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MOVIE)
                .build();
        mediaSession.setPlaybackToLocal(attrs);
    }

    public void setActive(boolean active) {
        if (mediaSession != null) {
            mediaSession.setActive(active);
        }
    }

    public void updatePlaybackState(int state) {
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

    public void updateMediaMetadata(String title, long durationMs) {
        updateMediaMetadata(title, durationMs, null);
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
            return src;
        }
    }

    public void updateMediaMetadata(final String title, final long durationMs, final android.graphics.Bitmap coverBitmap) {
        if (mediaSession != null) {
            MediaMetadata.Builder metaBuilder = new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, "Gimy 鬼魅劇場")
                .putLong(MediaMetadata.METADATA_KEY_DURATION, durationMs);
            if (coverBitmap != null) {
                android.graphics.Bitmap scaled = scaleBitmap(coverBitmap, 320); // 320px is perfect for low-latency Binder transit
                if (scaled != null) {
                    metaBuilder.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, scaled);
                    metaBuilder.putBitmap(MediaMetadata.METADATA_KEY_ART, scaled);
                    metaBuilder.putBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON, scaled);
                }
            }
            mediaSession.setMetadata(metaBuilder.build());
        }
    }

    public void release() {
        if (mediaSession != null) {
            mediaSession.release();
            mediaSession = null;
        }
    }
}
