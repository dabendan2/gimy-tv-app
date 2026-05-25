package com.gimytv.horror;

import android.content.Context;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.media.MediaMetadata;
import android.os.SystemClock;

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
        if (mediaSession != null) {
            MediaMetadata.Builder metaBuilder = new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, "Gimy 鬼魅劇場")
                .putLong(MediaMetadata.METADATA_KEY_DURATION, durationMs);
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
