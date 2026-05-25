package com.gimytv.horror;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.MediaPlayer;
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

    // Running states
    private GimyMediaSession gimyMediaSession;
    private String selectedMovieId = "";
    private String selectedMovieTitle = "";
    private String selectedMovieImageUrl = "";
    private String selectedMovieSubtitle = "";

    private boolean isSeekingMode = false;
    private long lastAutoSaveTime = 0;
    private int targetSeekTime = 0;
    private int originalPositionBeforeSeek = 0;

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
            if (seekOverlayLayout != null) {
                if (isSeekingMode) {
                    isSeekingMode = false;
                    videoView.seekTo(targetSeekTime);
                    videoView.start();
                    setPlayerTitleVisible(false);
                    showPlaybackIndicator("▶");
                }
                seekOverlayLayout.setVisibility(View.GONE);
            }
        }
    };

    private final Runnable updateProgressRunnable = new Runnable() {
        @Override
        public void run() {
            if (videoView != null && playerContainer.getVisibility() == View.VISIBLE) {
                int pos = videoView.getCurrentPosition();
                int dur = videoView.getDuration();
                if (dur > 0) {
                    if (!isSeekingMode) {
                        seekSeekBar.setMax(dur);
                        seekSeekBar.setProgress(pos);
                        tvSeekCurrent.setText(formatTime(pos));
                        tvSeekTotal.setText(formatTime(dur));
                        if (gimyMediaSession != null) {
                            gimyMediaSession.updatePlaybackState(videoView.isPlaying() ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED);
                        }
                    }
                }

                // Auto-save progress every 60 seconds (60,000 ms) of active playback
                long now = SystemClock.elapsedRealtime();
                if (now - lastAutoSaveTime >= 60000) {
                    savePlaybackProgress();
                    lastAutoSaveTime = now;
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
        // Full-Screen Video Player Layer
        playerContainer = new FrameLayout(activity);
        playerContainer.setBackgroundColor(Color.BLACK);
        playerContainer.setVisibility(View.GONE);

        videoView = new VideoView(activity);
        FrameLayout.LayoutParams playerParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER);
        videoView.setLayoutParams(playerParams);
        playerContainer.addView(videoView);

        // Loading overlay
        tvLoadingIndicator = new TextView(activity);
        tvLoadingIndicator.setText("影片載入中，請稍候...");
        tvLoadingIndicator.setTextSize(20);
        tvLoadingIndicator.setTextColor(Color.WHITE);
        FrameLayout.LayoutParams loaderParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        tvLoadingIndicator.setLayoutParams(loaderParams);
        playerContainer.addView(tvLoadingIndicator);

        // Custom playback action indicator (focus-free and intuitive TV player!)
        tvPlaybackIndicator = new TextView(activity);
        tvPlaybackIndicator.setTextSize(36);
        tvPlaybackIndicator.setTextColor(Color.WHITE);
        tvPlaybackIndicator.setGravity(Gravity.CENTER);
        tvPlaybackIndicator.setPadding(40, 30, 40, 30);
        tvPlaybackIndicator.setBackgroundColor(Color.parseColor("#90000000")); // 56% opacity black card
        tvPlaybackIndicator.setVisibility(View.GONE);
        FrameLayout.LayoutParams indicatorParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        tvPlaybackIndicator.setLayoutParams(indicatorParams);
        playerContainer.addView(tvPlaybackIndicator);

        // Player title indicator (shown in the top-left corner on pause!)
        tvPlayerTitle = new TextView(activity);
        tvPlayerTitle.setTextSize(22);
        tvPlayerTitle.setTextColor(Color.WHITE);
        tvPlayerTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        tvPlayerTitle.setPadding(40, 20, 40, 20);
        tvPlayerTitle.setBackgroundColor(Color.parseColor("#90000000")); // 56% opacity black card
        tvPlayerTitle.setVisibility(View.GONE);
        FrameLayout.LayoutParams titleParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT);
        titleParams.setMargins(60, 60, 0, 0); // Margin from top-left corner
        tvPlayerTitle.setLayoutParams(titleParams);
        playerContainer.addView(tvPlayerTitle);

        // Custom TV seek progress timeline overlay (placed at the bottom)
        seekOverlayLayout = new LinearLayout(activity);
        seekOverlayLayout.setOrientation(LinearLayout.HORIZONTAL);
        seekOverlayLayout.setGravity(Gravity.CENTER_VERTICAL);
        seekOverlayLayout.setBackgroundColor(Color.parseColor("#CC121212")); // 80% opacity dark grey
        seekOverlayLayout.setPadding(50, 30, 50, 30);
        seekOverlayLayout.setVisibility(View.GONE);
        
        FrameLayout.LayoutParams seekOverlayParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        seekOverlayLayout.setLayoutParams(seekOverlayParams);

        // Current Seek Time
        tvSeekCurrent = new TextView(activity);
        tvSeekCurrent.setText("00:00");
        tvSeekCurrent.setTextColor(Color.WHITE);
        tvSeekCurrent.setTextSize(14);
        tvSeekCurrent.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        tvSeekCurrent.setPadding(0, 0, 30, 0);
        seekOverlayLayout.addView(tvSeekCurrent);

        // SeekBar (Timeline)
        seekSeekBar = new SeekBar(activity);
        seekSeekBar.setFocusable(false); // DO NOT allow remote control focus to get stuck!
        seekSeekBar.setClickable(false);
        LinearLayout.LayoutParams seekBarParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f); // Takes up all middle space
        seekSeekBar.setLayoutParams(seekBarParams);
        seekOverlayLayout.addView(seekSeekBar);

        // Total Duration Time
        tvSeekTotal = new TextView(activity);
        tvSeekTotal.setText("00:00");
        tvSeekTotal.setTextColor(Color.parseColor("#9AA0A6"));
        tvSeekTotal.setTextSize(14);
        tvSeekTotal.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        tvSeekTotal.setPadding(30, 0, 0, 0);
        seekOverlayLayout.addView(tvSeekTotal);

        playerContainer.addView(seekOverlayLayout);
        rootContainer.addView(playerContainer);
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

        lastAutoSaveTime = SystemClock.elapsedRealtime();
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

        final int savedPos = resume ? movieStore.getProgressPos(selectedMovieId) : 0;
        Log.d(TAG, "Saved playback position for this movie: " + savedPos + " ms");

        videoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                Log.i(TAG, "✅ Video prepared. Duration: " + videoView.getDuration() + " ms");
                tvLoadingIndicator.setVisibility(View.GONE);
                if (savedPos > 0) {
                    Log.i(TAG, "Seeking to saved progress: " + savedPos + " ms");
                    videoView.seekTo(savedPos);
                }
                videoView.start();
                if (gimyMediaSession != null) {
                    gimyMediaSession.updatePlaybackState(PlaybackState.STATE_PLAYING);
                    final String currentMovieId = selectedMovieId;
                    final String currentTitle = selectedMovieTitle;
                    final int duration = videoView.getDuration();
                    ImageLoader.loadImageBitmap(selectedMovieImageUrl, new ImageLoader.ImageLoadCallback() {
                        @Override
                        public void onImageLoaded(final android.graphics.Bitmap bitmap) {
                            if (gimyMediaSession != null && currentMovieId.equals(selectedMovieId)) {
                                activity.runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (currentMovieId.equals(selectedMovieId)) {
                                            gimyMediaSession.updateMediaMetadata(currentTitle, duration, bitmap, selectedMovieImageUrl);
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
                stopPlayer();
            }
        });
    }

    public void savePlaybackProgress() {
        if (videoView != null && selectedMovieId != null && !selectedMovieId.isEmpty()) {
            int pos = videoView.getCurrentPosition();
            int dur = videoView.getDuration();
            Log.d(TAG, "💾 savePlaybackProgress triggered - ID: " + selectedMovieId + " | Pos: " + pos + " ms | Dur: " + dur + " ms");
            if (dur > 0 && pos > 0) {
                movieStore.savePlaybackProgress(selectedMovieId, pos, dur);
                TvWatchNextHelper.updateWatchNext(activity, movieStore, selectedMovieId, selectedMovieTitle, selectedMovieImageUrl, selectedMovieSubtitle, pos, dur);
            }
        }
    }

    public void stopPlayer() {
        Log.i(TAG, "⏹ stopping playback for Movie ID: " + selectedMovieId);
        seekHandler.removeCallbacks(updateProgressRunnable);
        seekHandler.removeCallbacks(hideSeekOverlayRunnable);
        isSeekingMode = false;
        if (seekOverlayLayout != null) {
            seekOverlayLayout.setVisibility(View.GONE);
        }
        savePlaybackProgress(); // Save progress
        if (gimyMediaSession != null) {
            gimyMediaSession.updatePlaybackState(PlaybackState.STATE_STOPPED);
            gimyMediaSession.setActive(false);
        }
        if (videoView.isPlaying()) {
            videoView.stopPlayback();
        }
        playerContainer.setVisibility(View.GONE);

        if (listener != null) {
            listener.onPlaybackStopped();
        }
    }

    public void pausePlaybackOnBackground() {
        Log.i(TAG, "⏸ pausePlaybackOnBackground triggered.");
        seekHandler.removeCallbacks(updateProgressRunnable);
        seekHandler.removeCallbacks(hideSeekOverlayRunnable);
        if (isPlayerActive()) {
            savePlaybackProgress();
            if (videoView != null && videoView.isPlaying()) {
                videoView.pause();
                setPlayerTitleVisible(true);
                showPlaybackIndicator("❚❚");
                if (gimyMediaSession != null) {
                    gimyMediaSession.updatePlaybackState(PlaybackState.STATE_PAUSED);
                }
            }
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

    public boolean handlePlayerKeyDown(int keyCode, KeyEvent event) {
        if (!isPlayerActive()) {
            return false;
        }

        Log.d(TAG, "D-Pad / Player Key pressed: " + KeyEvent.keyCodeToString(keyCode));

        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            if (isSeekingMode) {
                Log.i(TAG, "Seek committed to position: " + targetSeekTime + " ms");
                isSeekingMode = false;
                videoView.seekTo(targetSeekTime);
                videoView.start();
                setPlayerTitleVisible(false);
                showPlaybackIndicator("▶");
                
                seekHandler.removeCallbacks(hideSeekOverlayRunnable);
                seekHandler.postDelayed(hideSeekOverlayRunnable, 2000);
            } else {
                if (videoView.isPlaying()) {
                    Log.i(TAG, "Playback paused via Center/Enter key.");
                    videoView.pause();
                    setPlayerTitleVisible(true);
                    showPlaybackIndicator("❚❚");
                    if (gimyMediaSession != null) gimyMediaSession.updatePlaybackState(PlaybackState.STATE_PAUSED);
                } else {
                    Log.i(TAG, "Playback resumed via Center/Enter key.");
                    videoView.start();
                    setPlayerTitleVisible(false);
                    showPlaybackIndicator("▶");
                    if (gimyMediaSession != null) gimyMediaSession.updatePlaybackState(PlaybackState.STATE_PLAYING);
                }
                if (seekOverlayLayout != null) {
                    seekOverlayLayout.setVisibility(View.VISIBLE);
                    seekSeekBar.setMax(videoView.getDuration());
                    seekSeekBar.setProgress(videoView.getCurrentPosition());
                    tvSeekCurrent.setText(formatTime(videoView.getCurrentPosition()));
                    tvSeekTotal.setText(formatTime(videoView.getDuration()));
                }
                
                seekHandler.removeCallbacks(hideSeekOverlayRunnable);
                seekHandler.postDelayed(hideSeekOverlayRunnable, 4000);
            }
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            if (!isSeekingMode) {
                isSeekingMode = true;
                originalPositionBeforeSeek = videoView.getCurrentPosition();
                targetSeekTime = originalPositionBeforeSeek;
                Log.i(TAG, "Entering Seek Mode (Backward) from pos: " + originalPositionBeforeSeek + " ms");
                videoView.pause();
                setPlayerTitleVisible(true);
                showPlaybackIndicator("❚❚");
                if (gimyMediaSession != null) gimyMediaSession.updatePlaybackState(PlaybackState.STATE_PAUSED);
                if (seekOverlayLayout != null) {
                    seekOverlayLayout.setVisibility(View.VISIBLE);
                }
            }
            targetSeekTime = Math.max(0, targetSeekTime - 30000);
            Log.d(TAG, "Seeking backward, targetSeekTime: " + targetSeekTime + " ms");
            if (seekSeekBar != null) {
                seekSeekBar.setMax(videoView.getDuration());
                seekSeekBar.setProgress(targetSeekTime);
            }
            if (tvSeekCurrent != null) {
                tvSeekCurrent.setText(formatTime(targetSeekTime));
            }
            if (tvSeekTotal != null) {
                tvSeekTotal.setText(formatTime(videoView.getDuration()));
            }
            
            seekHandler.removeCallbacks(hideSeekOverlayRunnable);
            seekHandler.postDelayed(hideSeekOverlayRunnable, 5000);
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            if (!isSeekingMode) {
                isSeekingMode = true;
                originalPositionBeforeSeek = videoView.getCurrentPosition();
                targetSeekTime = originalPositionBeforeSeek;
                Log.i(TAG, "Entering Seek Mode (Forward) from pos: " + originalPositionBeforeSeek + " ms");
                videoView.pause();
                setPlayerTitleVisible(true);
                showPlaybackIndicator("❚❚");
                if (gimyMediaSession != null) gimyMediaSession.updatePlaybackState(PlaybackState.STATE_PAUSED);
                if (seekOverlayLayout != null) {
                    seekOverlayLayout.setVisibility(View.VISIBLE);
                }
            }
            targetSeekTime = Math.min(videoView.getDuration(), targetSeekTime + 30000);
            Log.d(TAG, "Seeking forward, targetSeekTime: " + targetSeekTime + " ms");
            if (seekSeekBar != null) {
                seekSeekBar.setMax(videoView.getDuration());
                seekSeekBar.setProgress(targetSeekTime);
            }
            if (tvSeekCurrent != null) {
                tvSeekCurrent.setText(formatTime(targetSeekTime));
            }
            if (tvSeekTotal != null) {
                tvSeekTotal.setText(formatTime(videoView.getDuration()));
            }
            
            seekHandler.removeCallbacks(hideSeekOverlayRunnable);
            seekHandler.postDelayed(hideSeekOverlayRunnable, 5000);
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (isSeekingMode) {
                Log.i(TAG, "Seek cancelled, returning to original position: " + originalPositionBeforeSeek + " ms");
                isSeekingMode = false;
                videoView.seekTo(originalPositionBeforeSeek);
                videoView.start();
                setPlayerTitleVisible(false);
                showPlaybackIndicator("▶");
                if (gimyMediaSession != null) gimyMediaSession.updatePlaybackState(PlaybackState.STATE_PLAYING);
                if (seekOverlayLayout != null) {
                    seekOverlayLayout.setVisibility(View.GONE);
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

    private String formatTime(int ms) {
        int seconds = (ms / 1000) % 60;
        int minutes = (ms / (1000 * 60)) % 60;
        int hours = ms / (1000 * 60 * 60);
        if (hours > 0) {
            return String.format("%02d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format("%02d:%02d", minutes, seconds);
        }
    }
}
