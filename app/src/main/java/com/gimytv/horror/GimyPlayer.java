package com.gimytv.horror;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.media.MediaPlayer;
import android.view.TextureView;
import android.view.Surface;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.VideoView;

public class GimyPlayer {
    private static final String TAG = "GimyHorror_Player";

    public interface PlayerListener {
        void onPlaybackStopped();
    }

    private final Activity activity;
    private final FrameLayout rootContainer;
    private final MovieStore movieStore;
    private final PlayerListener listener;

    // UI elements built programmatically
    private FrameLayout playerContainer;
    private VideoView videoView;
    private TextView tvLoadingIndicator;
    private TextView tvPlaybackIndicator;
    private TextView tvPlayerTitle;
    private LinearLayout seekOverlayLayout;
    private TextView tvSeekCurrent;
    private TextView tvSeekTotal;
    private SeekBar seekSeekBar;

    // Visual Scrubbing & OSD Peek additions
    private LinearLayout bottomSeekOverlayContainer;
    private FrameLayout previewWindowLayout;
    private TextureView previewTextureView;
    private MediaPlayer previewMediaPlayer;
    private FrameLayout previewFrame;
    private ImageView previewImageView;
    private String currentM3u8Url = "";
    private android.graphics.Bitmap posterBitmap = null;

    // Running states
    private GimyMediaSession gimyMediaSession;
    private String selectedMovieId = "";
    private String selectedMovieTitle = "";
    private String selectedMovieImageUrl = "";
    private String selectedMovieSubtitle = "";

    private boolean isSeekingMode = false;
    private boolean shouldPlayOnPrepared = true;
    private long lastAutoSaveTimeMs = 0;
    private int targetSeekTimeMs = 0;
    private int originalPositionBeforeSeekMs = 0;

    private final Handler seekHandler = new Handler();
    private final Handler indicatorHandler = new Handler();

    private final Runnable hideIndicatorRunnable = new Runnable() {
        @Override
        public void run() {
            if (tvPlaybackIndicator != null) {
                tvPlaybackIndicator.setVisibility(View.GONE);
            }
        }
    };

    private final Runnable hideSeekOverlayRunnable = new Runnable() {
        @Override
        public void run() {
            if (bottomSeekOverlayContainer != null) {
                if (videoView != null && !videoView.isPlaying() && !isSeekingMode) {
                    return; // Keep progress bar visible while paused
                }
                if (isSeekingMode) {
                    isSeekingMode = false;
                    seekTo(targetSeekTimeMs);
                    videoView.start();
                    savePlaybackProgress(targetSeekTimeMs, true); // SAVE IMMEDIATELY ON INACTIVITY SEEK COMMIT
                    setPlayerTitleVisible(false);
                    showPlaybackIndicator("▶");
                    if (gimyMediaSession != null) {
                        gimyMediaSession.updatePlaybackState(PlaybackState.STATE_PLAYING);
                    }
                }
                bottomSeekOverlayContainer.setVisibility(View.GONE);
                if (previewWindowLayout != null) {
                    previewWindowLayout.setVisibility(View.GONE);
                }
            }
        }
    };

    private final Runnable updateProgressRunnable = new Runnable() {
        @Override
        public void run() {
            if (videoView != null && playerContainer.getVisibility() == View.VISIBLE) {
                if (!TimeUtils.shouldShowPauseTitle(videoView.isPlaying(), isSeekingMode, !videoView.isPlaying()) && tvPlayerTitle != null && tvPlayerTitle.getVisibility() == View.VISIBLE) {
                    setPlayerTitleVisible(false);
                }
                int posMs = videoView.getCurrentPosition();
                int durMs = videoView.getDuration();
                if (durMs > 0) {
                    if (!isSeekingMode) {
                        seekSeekBar.setMax(durMs);
                        seekSeekBar.setProgress(posMs);
                        tvSeekCurrent.setText(formatTime(posMs));
                        tvSeekTotal.setText(formatTime(durMs));
                        if (gimyMediaSession != null) {
                            gimyMediaSession.updatePlaybackState(videoView.isPlaying() ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED);
                        }
                    }
                }

                // Auto-save progress every 60 seconds (60,000 ms) of active playback
                long nowMs = SystemClock.elapsedRealtime();
                if (nowMs - lastAutoSaveTimeMs >= 60000) {
                    savePlaybackProgress();
                    lastAutoSaveTimeMs = nowMs;
                }

                seekHandler.postDelayed(this, 1000);
            }
        }
    };

    public GimyPlayer(Activity activity, FrameLayout rootContainer, MovieStore movieStore, PlayerListener listener) {
        this.activity = activity;
        this.rootContainer = rootContainer;
        this.movieStore = movieStore;
        this.listener = listener;

        buildPlayerUI();
    }

    private void buildPlayerUI() {
        GimyPlayerViewHelper.ViewHolder holder = GimyPlayerViewHelper.buildPlayerUI(activity, rootContainer, new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(android.graphics.SurfaceTexture surfaceTexture, int width, int height) {
                Surface surface = new Surface(surfaceTexture);
                if (previewMediaPlayer != null) {
                    try {
                        previewMediaPlayer.setSurface(surface);
                    } catch (Exception ignored) {}
                }
            }

            @Override
            public void onSurfaceTextureSizeChanged(android.graphics.SurfaceTexture surface, int width, int height) {}

            @Override
            public boolean onSurfaceTextureDestroyed(android.graphics.SurfaceTexture surface) {
                if (previewMediaPlayer != null) {
                    try {
                        previewMediaPlayer.setSurface(null);
                    } catch (Exception ignored) {}
                }
                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(android.graphics.SurfaceTexture surface) {}
        });

        this.playerContainer = holder.playerContainer;
        this.videoView = holder.videoView;
        this.tvLoadingIndicator = holder.tvLoadingIndicator;
        this.tvPlaybackIndicator = holder.tvPlaybackIndicator;
        this.tvPlayerTitle = holder.tvPlayerTitle;
        this.seekOverlayLayout = holder.seekOverlayLayout;
        this.tvSeekCurrent = holder.tvSeekCurrent;
        this.tvSeekTotal = holder.tvSeekTotal;
        this.seekSeekBar = holder.seekSeekBar;
        this.bottomSeekOverlayContainer = holder.bottomSeekOverlayContainer;
        this.previewWindowLayout = holder.previewWindowLayout;
        this.previewTextureView = holder.previewTextureView;
        this.previewFrame = holder.previewFrame;
        this.previewImageView = holder.previewImageView;
    }

    public void setMediaSession(GimyMediaSession gimyMediaSession) {
        this.gimyMediaSession = gimyMediaSession;
    }

    public boolean isPlayerActive() {
        return playerContainer.getVisibility() == View.VISIBLE;
    }

    public VideoView getVideoView() {
        return videoView;
    }

    public void startPlayer(final String m3u8Url, boolean resume, String movieId, String title, String imageUrl, String subtitle) {
        Log.i(TAG, "🎬 startPlayer requested - Title: " + title + " | Movie ID: " + movieId + " | Resume: " + resume + " | URL: " + m3u8Url);
        this.selectedMovieId = movieId;
        this.selectedMovieTitle = title;
        this.selectedMovieImageUrl = imageUrl;
        this.selectedMovieSubtitle = subtitle;
        this.currentM3u8Url = m3u8Url;
        this.shouldPlayOnPrepared = true;

        lastAutoSaveTimeMs = SystemClock.elapsedRealtime();
        setPlayerTitleVisible(false);
        tvLoadingIndicator.setVisibility(View.VISIBLE);
        playerContainer.setVisibility(View.VISIBLE);

        if (gimyMediaSession != null) {
            Log.d(TAG, "Active MediaSession and set to BUFFERING.");
            gimyMediaSession.setActive(true);
            gimyMediaSession.updateMediaMetadata(selectedMovieTitle != null && !selectedMovieTitle.isEmpty() ? selectedMovieTitle : "Gimy TV", -1);
            gimyMediaSession.updatePlaybackState(PlaybackState.STATE_BUFFERING);

            final String currentMovieId = selectedMovieId;
            final String currentTitle = selectedMovieTitle;
            ImageLoader.loadImageBitmap(selectedMovieImageUrl, new ImageLoader.ImageLoadCallback() {
                @Override
                public void onImageLoaded(final android.graphics.Bitmap bitmap) {
                    if (bitmap != null) {
                        posterBitmap = bitmap; // Cache poster bitmap!
                    }
                    if (bitmap != null && gimyMediaSession != null && currentMovieId.equals(selectedMovieId)) {
                        activity.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (currentMovieId.equals(selectedMovieId)) {
                                    Log.d(TAG, "MediaSession metadata updated with poster bitmap.");
                                    gimyMediaSession.updateMediaMetadata(currentTitle != null && !currentTitle.isEmpty() ? currentTitle : "Gimy TV", -1, bitmap, selectedMovieImageUrl);
                                }
                            }
                        });
                    }
                }
            });
        }

        videoView.setMediaController(null); // Completely disable default controller
        videoView.setVideoPath(m3u8Url);
        videoView.requestFocus();

        preparePreviewMediaPlayer();

        if (!resume) {
            Log.i(TAG, "Restart play requested. Clearing saved progress in MovieStore for movie ID: " + selectedMovieId);
            movieStore.clearPlaybackProgress(selectedMovieId);
        }

        videoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                Log.i(TAG, "✅ Video prepared. Duration: " + videoView.getDuration() + " ms");
                
                // CRITICAL SAFETY SHIELD: If player is NOT active, NEVER start video!
                // Discard background preparation immediately if the user is not looking at the player.
                if (!isPlayerActive()) {
                    Log.w(TAG, "⚠️ Player is not active (getVisibility != VISIBLE). Releasing and stopping preparation.");
                    videoView.stopPlayback();
                    return;
                }
                
                tvLoadingIndicator.setVisibility(View.GONE);
                
                // Dynamically read the latest progress from MovieStore.
                // This correctly handles both the first start (where resume=false clears the progress to 0 beforehand)
                // and any subsequent prepares (surface recreation when returning from background).
                int finalSeekPosMs = movieStore.getProgressPos(selectedMovieId);
                Log.d(TAG, "Prepared playback. Seek position from MovieStore: " + finalSeekPosMs + " ms");
                
                int duration = videoView.getDuration();
                if (TimeUtils.isNearEnd(finalSeekPosMs, duration)) {
                    Log.i(TAG, "Saved progress " + finalSeekPosMs + " ms is near the end of video (duration: " + duration + " ms). Resetting play position to 0 and clearing DB progress.");
                    finalSeekPosMs = 0;
                    movieStore.clearPlaybackProgress(selectedMovieId);
                }

                if (activity instanceof MainActivity) {
                    int pSeek = ((MainActivity) activity).pendingSeekMs;
                    if (pSeek != -1) {
                        if (pSeek < 0) {
                            finalSeekPosMs = videoView.getDuration() + pSeek;
                        } else {
                            finalSeekPosMs = pSeek;
                        }
                        ((MainActivity) activity).pendingSeekMs = -1; // reset
                        Log.i(TAG, "Using pendingSeekMs from MainActivity resolved to: " + finalSeekPosMs + " ms");
                    }
                }
                
                if (finalSeekPosMs > 0) {
                    Log.i(TAG, "Seeking to final progress: " + finalSeekPosMs + " ms");
                    seekTo(finalSeekPosMs);
                }
                
                if (shouldPlayOnPrepared) {
                    Log.i(TAG, "Starting video playback (shouldPlayOnPrepared=true).");
                    videoView.start();
                    setPlayerTitleVisible(false);
                } else {
                    Log.i(TAG, "Keeping video playback PAUSED (shouldPlayOnPrepared=false).");
                    videoView.pause();
                    showPlaybackIndicator("❚❚");
                    setPlayerTitleVisible(true);
                }
                
                if (gimyMediaSession != null) {
                    gimyMediaSession.updatePlaybackState(shouldPlayOnPrepared ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED);
                    final String currentMovieId = selectedMovieId;
                    final String currentTitle = selectedMovieTitle;
                    final int finalDur = duration;
                    ImageLoader.loadImageBitmap(selectedMovieImageUrl, new ImageLoader.ImageLoadCallback() {
                        @Override
                        public void onImageLoaded(final android.graphics.Bitmap bitmap) {
                            if (bitmap != null) {
                                posterBitmap = bitmap; // Cache poster bitmap!
                            }
                            if (gimyMediaSession != null && currentMovieId.equals(selectedMovieId)) {
                                activity.runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (currentMovieId.equals(selectedMovieId)) {
                                            gimyMediaSession.updateMediaMetadata(currentTitle, finalDur, bitmap, selectedMovieImageUrl);
                                        }
                                    }
                                });
                            }
                        }
                    });
                }
                // Start progress timeline loop
                seekHandler.removeCallbacks(updateProgressRunnable);
                seekHandler.post(updateProgressRunnable);
            }
        });

        videoView.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer mp, int what, int extra) {
                Log.e(TAG, "❌ Video player error occurred! What: " + what + " | Extra: " + extra + " | URL: " + m3u8Url);
                tvLoadingIndicator.setVisibility(View.GONE);
                android.widget.Toast.makeText(activity, "影片載入失敗，可能需要切換線路！", android.widget.Toast.LENGTH_LONG).show();
                stopPlayer();
                return true;
            }
        });

        videoView.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                Log.i(TAG, "🎬 Playback completed for Movie: " + selectedMovieTitle);
                if (videoView != null && selectedMovieId != null && !selectedMovieId.isEmpty()) {
                    int dur = videoView.getDuration();
                    if (dur > 0) {
                        Log.i(TAG, "Saving final completed progress: " + dur + " ms");
                        savePlaybackProgress(dur, true); // Force save on completion
                    }
                }
                stopPlayer();
            }
        });
    }

    public void seekTo(int posMs) {
        if (videoView != null) {
            videoView.seekTo(posMs);
        }
    }

    public void savePlaybackProgress() {
        savePlaybackProgress(false);
    }

    public void savePlaybackProgress(boolean force) {
        if (videoView != null && selectedMovieId != null && !selectedMovieId.isEmpty()) {
            int pos = videoView.getCurrentPosition();
            savePlaybackProgress(pos, force);
        }
    }

    public void savePlaybackProgress(int pos) {
        savePlaybackProgress(pos, true); // External/manual single-arg saves default to force=true
    }

    public void savePlaybackProgress(int pos, boolean force) {
        if (videoView != null && selectedMovieId != null && !selectedMovieId.isEmpty()) {
            int dur = videoView.getDuration();
            Log.d(TAG, "💾 savePlaybackProgress triggered - ID: " + selectedMovieId + " | Pos: " + pos + " ms | Dur: " + dur + " ms | Force: " + force);
            if (dur > 0 && pos >= 0) {
                movieStore.savePlaybackProgress(selectedMovieId, pos, dur, force);
            }
        }
    }

    private void preparePreviewMediaPlayer() {
        if (previewMediaPlayer != null) {
            try { previewMediaPlayer.release(); } catch (Exception ignored) {}
        }
        if (currentM3u8Url == null || currentM3u8Url.isEmpty()) return;
        previewMediaPlayer = new MediaPlayer();
        try {
            previewMediaPlayer.setDataSource(activity, android.net.Uri.parse(currentM3u8Url));
            previewMediaPlayer.setVolume(0f, 0f); // MUTE preview audio
            previewMediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    Log.i(TAG, "✅ Preview MediaPlayer prepared.");
                    if (previewTextureView.isAvailable()) {
                        try {
                            previewMediaPlayer.setSurface(new Surface(previewTextureView.getSurfaceTexture()));
                        } catch (Exception ignored) {}
                    }
                }
            });
            previewMediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer mp, int what, int extra) {
                    Log.e(TAG, "❌ Preview MediaPlayer error occurred! What: " + what + " | Extra: " + extra);
                    return true; // Prevent default error dialog
                }
            });
            previewMediaPlayer.setOnSeekCompleteListener(new MediaPlayer.OnSeekCompleteListener() {
                @Override
                public void onSeekComplete(MediaPlayer mp) {
                    final int currentSeekTimeSec = targetSeekTimeMs / 1000;
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (previewTextureView != null && previewTextureView.isAvailable()) {
                                android.graphics.Bitmap bmp = previewTextureView.getBitmap();
                                if (bmp != null) {
                                    synchronized (frameCache) {
                                        frameCache.put(currentSeekTimeSec, bmp);
                                    }
                                    // Trigger refresh of the strip so neighbor cards get the newly grabbed frame!
                                    updatePreviewStrip(targetSeekTimeMs);
                                }
                            }
                        }
                    });
                }
            });
            previewMediaPlayer.prepareAsync();
        } catch (Exception e) {
            Log.e(TAG, "Error preparing Preview MediaPlayer: " + e.getMessage());
        }
    }

    private void stopAndReleasePreviewMediaPlayer() {
        if (previewMediaPlayer != null) {
            try {
                previewMediaPlayer.stop();
                previewMediaPlayer.release();
            } catch (Exception ignored) {}
            previewMediaPlayer = null;
        }
    }

    public void stopPlayer() {
        Log.i(TAG, "⏹ stopping playback for Movie ID: " + selectedMovieId);
        seekHandler.removeCallbacks(updateProgressRunnable);
        seekHandler.removeCallbacks(hideSeekOverlayRunnable);
        isSeekingMode = false;
        currentLoadingRequestTag++; // Discard active async frame requests
        setPlayerTitleVisible(false);
        if (bottomSeekOverlayContainer != null) {
            bottomSeekOverlayContainer.setVisibility(View.GONE);
        }
        if (previewWindowLayout != null) {
            previewWindowLayout.setVisibility(View.GONE);
        }
        savePlaybackProgress(); // Save progress
        if (gimyMediaSession != null) {
            gimyMediaSession.updatePlaybackState(PlaybackState.STATE_STOPPED);
            gimyMediaSession.setActive(false);
        }
        // Ensure the player is stopped and resources (like audio decoders and network streams) are fully released, even if paused
        videoView.stopPlayback();
        stopAndReleasePreviewMediaPlayer();
        playerContainer.setVisibility(View.GONE);

        if (listener != null) {
            listener.onPlaybackStopped();
        }
    }

    public void pausePlaybackOnBackground() {
        Log.i(TAG, "⏸ pausePlaybackOnBackground triggered.");
        seekHandler.removeCallbacks(updateProgressRunnable);
        seekHandler.removeCallbacks(hideSeekOverlayRunnable);
        if (previewWindowLayout != null) {
            previewWindowLayout.setVisibility(View.GONE);
        }
        stopAndReleasePreviewMediaPlayer();
        if (isPlayerActive()) {
            savePlaybackProgress();
            if (videoView != null && videoView.isPlaying()) {
                videoView.pause();
                this.shouldPlayOnPrepared = true; // Was actively playing, so resume when prepared
                setPlayerTitleVisible(true);
                showPlaybackIndicator("❚❚");
                if (gimyMediaSession != null) {
                    gimyMediaSession.updatePlaybackState(PlaybackState.STATE_PAUSED);
                }
            } else {
                this.shouldPlayOnPrepared = false; // Was already paused, so stay paused when prepared
            }
        }
    }

    public void pausePlayback() {
        if (videoView != null && isPlayerActive()) {
            if (videoView.isPlaying()) {
                Log.i(TAG, "Playback paused.");
                videoView.pause();
                savePlaybackProgress();
            }
            this.shouldPlayOnPrepared = false; // User manually paused, so stay paused when prepared
            setPlayerTitleVisible(true);
            showPlaybackIndicator("❚❚");
            if (gimyMediaSession != null) {
                gimyMediaSession.updatePlaybackState(PlaybackState.STATE_PAUSED);
            }
            if (bottomSeekOverlayContainer != null) {
                bottomSeekOverlayContainer.setVisibility(View.VISIBLE);
                seekSeekBar.setMax(videoView.getDuration());
                seekSeekBar.setProgress(videoView.getCurrentPosition());
                tvSeekCurrent.setText(formatTime(videoView.getCurrentPosition()));
                tvSeekTotal.setText(formatTime(videoView.getDuration()));
            }
            if (previewWindowLayout != null) {
                previewWindowLayout.setVisibility(View.VISIBLE);
                if (previewMediaPlayer == null) {
                    preparePreviewMediaPlayer();
                }
                if (previewMediaPlayer != null) {
                    previewMediaPlayer.seekTo(videoView.getCurrentPosition());
                }
                updatePreviewStrip(videoView.getCurrentPosition());
            }
            seekHandler.removeCallbacks(hideSeekOverlayRunnable);
            seekHandler.postDelayed(hideSeekOverlayRunnable, 60000); // 1 minute inactivity timeout
        }
    }

    public void resumePlayback() {
        if (videoView != null && isPlayerActive()) {
            if (!videoView.isPlaying()) {
                Log.i(TAG, "Playback resumed.");
                videoView.start();
            }
            this.shouldPlayOnPrepared = true; // User manually resumed, so play when prepared
            setPlayerTitleVisible(false);
            showPlaybackIndicator("▶");
            if (gimyMediaSession != null) {
                gimyMediaSession.updatePlaybackState(PlaybackState.STATE_PLAYING);
            }
            if (bottomSeekOverlayContainer != null) {
                bottomSeekOverlayContainer.setVisibility(View.VISIBLE);
                seekSeekBar.setMax(videoView.getDuration());
                seekSeekBar.setProgress(videoView.getCurrentPosition());
                tvSeekCurrent.setText(formatTime(videoView.getCurrentPosition()));
                tvSeekTotal.setText(formatTime(videoView.getDuration()));
            }
            if (previewWindowLayout != null) {
                previewWindowLayout.setVisibility(View.VISIBLE);
            }
            seekHandler.removeCallbacks(hideSeekOverlayRunnable);
            seekHandler.postDelayed(hideSeekOverlayRunnable, 4000);
        }
    }

    public void showPlaybackIndicator(String text) {
        if (tvPlaybackIndicator == null) return;
        tvPlaybackIndicator.setText(text);
        tvPlaybackIndicator.setVisibility(View.VISIBLE);
        indicatorHandler.removeCallbacks(hideIndicatorRunnable);
        indicatorHandler.postDelayed(hideIndicatorRunnable, 1200);
    }

    public void setPlayerTitleVisible(boolean visible) {
        if (tvPlayerTitle != null) {
            if (visible) {
                tvPlayerTitle.setText("《" + selectedMovieTitle + "》 - 暫停");
                tvPlayerTitle.setVisibility(View.VISIBLE);
            } else {
                tvPlayerTitle.setVisibility(View.GONE);
            }
        }
    }

    private String formatDelta(int ms) {
        return TimeUtils.formatDelta(ms);
    }

    public boolean handlePlayerKeyDown(int keyCode, KeyEvent event) {
        if (!isPlayerActive()) {
            return false;
        }

        Log.d(TAG, "D-Pad / Player Key pressed: " + KeyEvent.keyCodeToString(keyCode));

        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            if (isSeekingMode) {
                Log.i(TAG, "Seek committed instantly to position: " + targetSeekTimeMs + " ms");
                isSeekingMode = false;
                seekTo(targetSeekTimeMs);
                videoView.start();
                savePlaybackProgress(targetSeekTimeMs, true); // SAVE IMMEDIATELY ON MANUAL SEEK COMMIT
                setPlayerTitleVisible(false);
                showPlaybackIndicator("▶");
                if (gimyMediaSession != null) {
                    gimyMediaSession.updatePlaybackState(PlaybackState.STATE_PLAYING);
                }
                if (bottomSeekOverlayContainer != null) {
                    bottomSeekOverlayContainer.setVisibility(View.GONE);
                }
                if (previewWindowLayout != null) {
                    previewWindowLayout.setVisibility(View.GONE);
                }
                seekHandler.removeCallbacks(hideSeekOverlayRunnable);
            } else {
                if (videoView.isPlaying()) {
                    Log.i(TAG, "Playback paused via Center/Enter key.");
                    pausePlayback();
                } else {
                    Log.i(TAG, "Playback resumed via Center/Enter key.");
                    resumePlayback();
                }
            }
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            if (!isSeekingMode) {
                isSeekingMode = true;
                originalPositionBeforeSeekMs = videoView.getCurrentPosition();
                targetSeekTimeMs = originalPositionBeforeSeekMs;
                Log.i(TAG, "Entering Seek Mode (Backward) from pos: " + originalPositionBeforeSeekMs + " ms");
                videoView.pause();
                savePlaybackProgress(originalPositionBeforeSeekMs, false); // SAVE IMMEDIATELY ON SEEK ENTER
                setPlayerTitleVisible(true);
                showPlaybackIndicator("❚❚");
                if (gimyMediaSession != null) gimyMediaSession.updatePlaybackState(PlaybackState.STATE_PAUSED);
                if (bottomSeekOverlayContainer != null) {
                    bottomSeekOverlayContainer.setVisibility(View.VISIBLE);
                }
            }
            if (previewMediaPlayer == null) {
                preparePreviewMediaPlayer();
            }
            targetSeekTimeMs = Math.max(0, targetSeekTimeMs - 30000);
            Log.d(TAG, "Seeking backward, targetSeekTimeMs: " + targetSeekTimeMs + " ms");
            if (seekSeekBar != null) {
                seekSeekBar.setMax(videoView.getDuration());
                seekSeekBar.setProgress(targetSeekTimeMs);
            }
            if (tvSeekCurrent != null) {
                tvSeekCurrent.setText(formatTime(targetSeekTimeMs));
            }
            if (tvSeekTotal != null) {
                tvSeekTotal.setText(formatTime(videoView.getDuration()));
            }
            if (previewWindowLayout != null) {
                previewWindowLayout.setVisibility(View.VISIBLE);
            }
            if (previewMediaPlayer != null) {
                previewMediaPlayer.seekTo(targetSeekTimeMs);
            }
            updatePreviewStrip(targetSeekTimeMs);

            
            seekHandler.removeCallbacks(hideSeekOverlayRunnable);
            seekHandler.postDelayed(hideSeekOverlayRunnable, 60000); // INACTIVITY COMMIT (1 MINUTE)
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            if (!isSeekingMode) {
                isSeekingMode = true;
                originalPositionBeforeSeekMs = videoView.getCurrentPosition();
                targetSeekTimeMs = originalPositionBeforeSeekMs;
                Log.i(TAG, "Entering Seek Mode (Forward) from pos: " + originalPositionBeforeSeekMs + " ms");
                videoView.pause();
                savePlaybackProgress(originalPositionBeforeSeekMs, false); // SAVE IMMEDIATELY ON SEEK ENTER
                setPlayerTitleVisible(true);
                showPlaybackIndicator("❚❚");
                if (gimyMediaSession != null) gimyMediaSession.updatePlaybackState(PlaybackState.STATE_PAUSED);
                if (bottomSeekOverlayContainer != null) {
                    bottomSeekOverlayContainer.setVisibility(View.VISIBLE);
                }
            }
            if (previewMediaPlayer == null) {
                preparePreviewMediaPlayer();
            }
            targetSeekTimeMs = Math.min(videoView.getDuration(), targetSeekTimeMs + 30000);
            Log.d(TAG, "Seeking forward, targetSeekTimeMs: " + targetSeekTimeMs + " ms");
            if (seekSeekBar != null) {
                seekSeekBar.setMax(videoView.getDuration());
                seekSeekBar.setProgress(targetSeekTimeMs);
            }
            if (tvSeekCurrent != null) {
                tvSeekCurrent.setText(formatTime(targetSeekTimeMs));
            }
            if (tvSeekTotal != null) {
                tvSeekTotal.setText(formatTime(videoView.getDuration()));
            }
            if (previewWindowLayout != null) {
                previewWindowLayout.setVisibility(View.VISIBLE);
            }
            if (previewMediaPlayer != null) {
                previewMediaPlayer.seekTo(targetSeekTimeMs);
            }
            updatePreviewStrip(targetSeekTimeMs);

            
            seekHandler.removeCallbacks(hideSeekOverlayRunnable);
            seekHandler.postDelayed(hideSeekOverlayRunnable, 60000); // INACTIVITY COMMIT (1 MINUTE)
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (isSeekingMode) {
                Log.i(TAG, "Seek cancelled, returning to original position: " + originalPositionBeforeSeekMs + " ms");
                isSeekingMode = false;
                seekTo(originalPositionBeforeSeekMs);
                videoView.start();
                savePlaybackProgress(originalPositionBeforeSeekMs, true); // SAVE TO RESTORE ORIGINAL PROGRESS IN DB
                setPlayerTitleVisible(false);
                showPlaybackIndicator("▶");
                if (gimyMediaSession != null) gimyMediaSession.updatePlaybackState(PlaybackState.STATE_PLAYING);
                if (bottomSeekOverlayContainer != null) {
                    bottomSeekOverlayContainer.setVisibility(View.GONE);
                }
                if (previewWindowLayout != null) {
                    previewWindowLayout.setVisibility(View.GONE);
                }
                seekHandler.removeCallbacks(hideSeekOverlayRunnable);
                return true;
            } else {
                Log.i(TAG, "Back pressed while playing, stopping player.");
                stopPlayer();
                return true;
            }
        }
        return false;
    }

    private int currentLoadingRequestTag = 0;
    private final java.util.concurrent.ExecutorService frameLoaderExecutor = java.util.concurrent.Executors.newFixedThreadPool(3);
    private final java.util.Map<Integer, android.graphics.Bitmap> frameCache = new java.util.LinkedHashMap<Integer, android.graphics.Bitmap>(50, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(java.util.Map.Entry<Integer, android.graphics.Bitmap> eldest) {
            return size() > 100; // Cache up to 100 frames
        }
    };

    private void updatePreviewPosition(int progress, int max) {
        if (max <= 0) return;
        if (seekSeekBar == null || previewFrame == null) return;

        int seekLeft = seekSeekBar.getLeft();
        int seekWidth = seekSeekBar.getWidth();
        if (seekWidth <= 0) {
            seekWidth = activity.getResources().getDisplayMetrics().widthPixels;
        }

        int paddingLeft = seekSeekBar.getPaddingLeft();
        int paddingRight = seekSeekBar.getPaddingRight();
        int usableWidth = seekWidth - paddingLeft - paddingRight;

        // Calculate the center position of the thumb
        float progressRatio = (float) progress / max;
        float thumbX = seekLeft + paddingLeft + (usableWidth * progressRatio);

        // Center the previewFrame over the thumbX
        float previewFrameWidth = previewFrame.getWidth();
        if (previewFrameWidth <= 0) {
            previewFrameWidth = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 150, activity.getResources().getDisplayMetrics());
        }

        float targetX = thumbX - (previewFrameWidth / 2f);

        // Clamp to screen bounds / SeekBar bounds
        float minX = seekLeft + paddingLeft;
        float maxX = seekLeft + seekWidth - paddingRight - previewFrameWidth;
        if (targetX < minX) targetX = minX;
        if (targetX > maxX) targetX = maxX;

        previewFrame.setTranslationX(targetX);
    }

    private void updatePreviewStrip(final int baseTimeMs) {
        final int requestTag = ++currentLoadingRequestTag;
        final String videoUrl = currentM3u8Url;
        if (videoUrl == null || videoUrl.isEmpty()) return;

        final int durationMs = videoView.getDuration();
        final int targetTimeMs = Math.max(0, Math.min(baseTimeMs, durationMs));

        // Update preview position and visibility on main thread
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (previewFrame != null) {
                    previewFrame.setVisibility(View.VISIBLE);
                    updatePreviewPosition(targetTimeMs, durationMs);
                }
            }
        });

        // Check if it is in cache
        final android.graphics.Bitmap cachedBitmap;
        synchronized (frameCache) {
            cachedBitmap = frameCache.get(targetTimeMs / 1000);
        }

        if (cachedBitmap != null) {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (requestTag == currentLoadingRequestTag) {
                        if (previewImageView != null) {
                            previewImageView.clearColorFilter();
                            previewImageView.setImageBitmap(cachedBitmap);
                            previewImageView.setVisibility(View.VISIBLE);
                            previewImageView.setAlpha(1f);
                        }
                    }
                }
            });
        } else {
            // Not in cache: First set poster placeholder with dark tint
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (requestTag == currentLoadingRequestTag) {
                        if (previewImageView != null) {
                            if (posterBitmap != null) {
                                previewImageView.setImageBitmap(posterBitmap);
                                previewImageView.setColorFilter(Color.parseColor("#90000000"), PorterDuff.Mode.SRC_ATOP);
                                previewImageView.setAlpha(0.6f);
                                previewImageView.setVisibility(View.VISIBLE);
                            } else {
                                previewImageView.setImageDrawable(null);
                                previewImageView.setVisibility(View.VISIBLE);
                            }
                        }
                    }
                }
            });

            // Load actual frame asynchronously
            frameLoaderExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    final android.graphics.Bitmap bitmap = retrieveFrameAtTime(videoUrl, targetTimeMs);
                    if (bitmap != null) {
                        synchronized (frameCache) {
                            frameCache.put(targetTimeMs / 1000, bitmap);
                        }
                        activity.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (requestTag == currentLoadingRequestTag) {
                                    if (previewImageView != null) {
                                        previewImageView.clearColorFilter();
                                        previewImageView.setImageBitmap(bitmap);
                                        previewImageView.setAlpha(0.5f);
                                        previewImageView.setVisibility(View.VISIBLE);
                                        previewImageView.animate().alpha(1f).setDuration(250).start();
                                    }
                                }
                            }
                        });
                    }
                }
            });
        }
    }

    private android.graphics.Bitmap retrieveFrameAtTime(String videoUrl, int timeMs) {
        android.media.MediaMetadataRetriever retriever = null;
        try {
            retriever = new android.media.MediaMetadataRetriever();
            retriever.setDataSource(videoUrl, new java.util.HashMap<String, String>());
            long timeUs = timeMs * 1000L;
            return retriever.getFrameAtTime(timeUs, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
        } catch (Exception e) {
            Log.e(TAG, "Error retrieving frame at " + timeMs + " ms: " + e.getMessage());
            return null;
        } finally {
            if (retriever != null) {
                try {
                    retriever.release();
                } catch (Exception ignored) {}
            }
        }
    }

    private String formatTime(int ms) {
        return TimeUtils.formatTime(ms);
    }
}
