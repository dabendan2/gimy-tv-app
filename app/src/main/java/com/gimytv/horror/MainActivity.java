package com.gimytv.horror;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.media.session.PlaybackState;
import java.net.URLEncoder;
import java.util.ArrayList;

public class MainActivity extends Activity {
    private static final String TAG = "GimyHorror_UI";

    // Encapsulated Components
    private FilterBarManager filterBarManager;

    // Layout elements
    private LinearLayout mainSplitLayout;
    private LinearLayout gridContainer;
    private ScrollView gridScrollView;
    private ScrollView rightScrollView;
    private LinearLayout playButtonLayout;

    // Detail Panel elements
    private TextView tvDetailTitle;
    private TextView tvDetailMeta;
    private TextView tvDetailSynopsis;

    // Persisted preferences & state
    private MovieStore movieStore;
    private GimyMediaSession gimyMediaSession = null;

    // Encapsulated Components
    private GimyPlayer gimyPlayer;
    private DetailPanelManager detailPanelManager;
    private GridPanelManager gridPanelManager;
    public int pendingSeekMs = -1;
    private boolean isDeepLinkActive = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "🚀 onCreate: Initializing Gimy TV App.");
        movieStore = new MovieStore(this);

        // Root container (holds main layout and full-screen video overlay)
        FrameLayout rootContainer = new FrameLayout(this);
        rootContainer.setBackgroundColor(Color.parseColor("#121212")); // Cyber-Horror deep background

        // 1. Main Split Layout
        mainSplitLayout = new LinearLayout(this);
        mainSplitLayout.setOrientation(LinearLayout.HORIZONTAL);
        mainSplitLayout.setWeightSum(10f);

        // LEFT PART: Filters & Movie Grid (6.2 width / 62%)
        LinearLayout leftPanel = new LinearLayout(this);
        leftPanel.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams leftParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 6.2f);
        leftPanel.setLayoutParams(leftParams);
        leftPanel.setPadding(40, 40, 30, 40);

        // Top App Title ("Gimy 鬼魅劇場" Google Style)
        LinearLayout titleLayout = new LinearLayout(this);
        titleLayout.setOrientation(LinearLayout.HORIZONTAL);
        titleLayout.setPadding(0, 0, 0, 20);

        String[] logoLetters = {"G", "i", "m", "y"};
        String[] logoColors = {"#4285F4", "#EA4335", "#FBBC05", "#4285F4"};
        for (int i = 0; i < logoLetters.length; i++) {
            TextView letter = new TextView(this);
            letter.setText(logoLetters[i]);
            letter.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
            letter.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            letter.setTextColor(Color.parseColor(logoColors[i]));
            titleLayout.addView(letter);
        }

        TextView subTitle = new TextView(this);
        subTitle.setText(" 鬼魅劇場");
        subTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
        subTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        subTitle.setTextColor(Color.WHITE);
        titleLayout.addView(subTitle);

        leftPanel.addView(titleLayout);

        // Filter Rows Container
        LinearLayout filterContainer = new LinearLayout(this);
        filterContainer.setOrientation(LinearLayout.VERTICAL);
        filterContainer.setPadding(0, 0, 0, 20);

        filterBarManager = new FilterBarManager(this, filterContainer, new FilterBarManager.FilterBarListener() {
            @Override
            public void onFilterChanged(String sort, String region, String year) {
                refreshMovieGrid();
            }
        });
        leftPanel.addView(filterContainer);

        // Grid Scroll Container
        gridScrollView = new ScrollView(this);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        gridScrollView.setLayoutParams(scrollParams);
        gridScrollView.setVerticalScrollBarEnabled(false);

        gridContainer = new LinearLayout(this);
        gridContainer.setOrientation(LinearLayout.VERTICAL);
        gridContainer.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        gridScrollView.addView(gridContainer);
        leftPanel.addView(gridScrollView);

        // RIGHT PART: Movie Details & Action (3.8 width / 38%)
        LinearLayout rightPanel = new LinearLayout(this);
        rightPanel.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams rightParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 3.8f);
        rightPanel.setLayoutParams(rightParams);
        rightPanel.setBackgroundColor(Color.parseColor("#1C1D1F")); // Deep dark card panel

        // 1. Right Scroll View (contains the entire detail panel)
        rightScrollView = new ScrollView(this);
        rightScrollView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        rightScrollView.setVerticalScrollBarEnabled(true);

        // 2. Right Scroll Content Container
        final LinearLayout rightScrollContent = new LinearLayout(this);
        rightScrollContent.setOrientation(LinearLayout.VERTICAL);
        rightScrollContent.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        rightScrollContent.setPadding(45, 45, 45, 45);

        // Detail Title
        tvDetailTitle = new TextView(this);
        tvDetailTitle.setText("《請在左側選取電影》");
        tvDetailTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        tvDetailTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        tvDetailTitle.setTextColor(Color.WHITE);
        tvDetailTitle.setPadding(0, 0, 0, 15);
        rightScrollContent.addView(tvDetailTitle);

        // Detail Meta info (Rating, Actor list)
        tvDetailMeta = new TextView(this);
        tvDetailMeta.setText("地區/演員：---\n狀態：---");
        tvDetailMeta.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        tvDetailMeta.setTextColor(Color.parseColor("#9AA0A6")); // Subtle gray text
        tvDetailMeta.setPadding(0, 0, 0, 20);
        tvDetailMeta.setLineSpacing(5f, 1f);
        rightScrollContent.addView(tvDetailMeta);

        // Detail Synopsis (Scrollable / auto wrapping inside the main parent scrollview!)
        tvDetailSynopsis = new TextView(this);
        tvDetailSynopsis.setText("這裡將顯示該鬼片劇迷網的詳細劇情介紹與恐怖謎底大綱。");
        tvDetailSynopsis.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        tvDetailSynopsis.setTextColor(Color.parseColor("#DADCE0")); // Brighter read text
        tvDetailSynopsis.setPadding(20, 20, 20, 20); // padded inside the focused background
        tvDetailSynopsis.setLineSpacing(6f, 1.1f);
        rightScrollContent.addView(tvDetailSynopsis);

        // --- TV Premium Scroll and Focus Setup ---
        rightScrollView.setFocusable(true);
        rightScrollView.setFocusableInTouchMode(true);

        rightScrollView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (detailPanelManager != null && detailPanelManager.getPlayButton() != null && detailPanelManager.getPlayButton().isEnabled()) {
                    detailPanelManager.getPlayButton().requestFocus();
                }
            }
        });

        rightScrollView.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    Log.i(TAG, "🎯 FocusState: Right Detail Panel focused");
                    v.setBackgroundColor(Color.parseColor("#303134")); // Highlight
                } else {
                    v.setBackgroundColor(Color.TRANSPARENT);
                }
            }
        });

        rightScrollView.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                        int currentScrollY = rightScrollView.getScrollY();
                        int scrollViewHeight = rightScrollView.getHeight();
                        int contentHeight = rightScrollContent.getHeight();
                        if (currentScrollY + scrollViewHeight < contentHeight - 15) {
                            rightScrollView.smoothScrollBy(0, 100); // Smooth scroll down 100px
                            return true; // Consume event to keep focus inside reader
                        } else {
                            if (detailPanelManager != null && detailPanelManager.getPlayButton() != null) {
                                detailPanelManager.getPlayButton().requestFocus(); // Focus to play/resume button
                                return true;
                            }
                        }
                    } else if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                        if (rightScrollView.getScrollY() > 0) {
                            rightScrollView.smoothScrollBy(0, -100); // Smooth scroll up 100px
                            return true; // Consume event to keep focus inside reader
                        } else {
                            return true; // Lock at the top of right panel (prevent jump left)
                        }
                    }
                }
                return false;
            }
        });

        // Action Buttons Container (Holds compact buttons, aligned to the LEFT!)
        playButtonLayout = new LinearLayout(this);
        playButtonLayout.setOrientation(LinearLayout.HORIZONTAL);
        playButtonLayout.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL); // Align LEFT!
        LinearLayout.LayoutParams playBtnContainerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        playBtnContainerParams.setMargins(0, 30, 0, 0); // space above buttons
        playButtonLayout.setLayoutParams(playBtnContainerParams);
        rightScrollContent.addView(playButtonLayout);

        rightScrollView.addView(rightScrollContent);
        rightPanel.addView(rightScrollView);

        // Assemble split screen
        mainSplitLayout.addView(leftPanel);
        mainSplitLayout.addView(rightPanel);
        rootContainer.addView(mainSplitLayout);

        // Initialize GridPanelManager Component
        gridPanelManager = new GridPanelManager(this, gridContainer, movieStore, new GridPanelManager.GridPanelListener() {
            @Override
            public void onMovieCardFocused(Movie movie, View card) {
                if (isDeepLinkActive) {
                    Log.i(TAG, "Ignoring autofocus card focus because a deep-link is active: 《" + movie.title + "》 (ID: " + movie.id + ")");
                    isDeepLinkActive = false;
                    return;
                }
                Log.i(TAG, "🎯 FocusState: Movie Card focused -> 《" + movie.title + "》 (ID: " + movie.id + ")");
                if (detailPanelManager != null) {
                    detailPanelManager.loadMovieDetails(movie.id, movie.title, movie.imageUrl, movie.note, movie.subtitle);
                }
            }

            @Override
            public void onMovieCardClicked(Movie movie, View card) {
                if (detailPanelManager != null && detailPanelManager.getPlayButton() != null && detailPanelManager.getPlayButton().isEnabled()) {
                    rightScrollView.requestFocus();
                } else {
                    android.widget.Toast.makeText(MainActivity.this, "影片載入中，請稍候...", android.widget.Toast.LENGTH_SHORT).show();
                }
            }
        });

        // Initialize DetailPanelManager Component
        detailPanelManager = new DetailPanelManager(this, rightScrollView, playButtonLayout,
                tvDetailTitle, tvDetailMeta, tvDetailSynopsis, movieStore, new DetailPanelManager.DetailPanelListener() {
            @Override
            public void onPlayMovieRequested(String playPath, boolean resume) {
                playMovie(playPath, resume);
            }

            @Override
            public void onListStateChanged(String movieId, int nextState) {
                // Synchronously update left grid card title in real time!
                View lastFocusedCard = gridPanelManager.getLastFocusedCard();
                if (lastFocusedCard != null && lastFocusedCard instanceof LinearLayout) {
                    LinearLayout card = (LinearLayout) lastFocusedCard;
                    if (card.getChildCount() > 1 && card.getChildAt(1) instanceof TextView) {
                        TextView tvCardTitle = (TextView) card.getChildAt(1);
                        String titleText = tvDetailTitle.getText().toString();
                        if (titleText.startsWith("《") && titleText.endsWith("》")) {
                            String originalTitle = titleText.substring(1, titleText.length() - 1);
                            String prefix = "";
                            if (nextState == 1) prefix = "📝 ";
                            else if (nextState == 2) prefix = "❤️ ";
                            else if (nextState == 3) prefix = "💩 ";
                            tvCardTitle.setText(prefix + originalTitle);
                        }
                    }
                }
            }
        });

        // Initialize GimyPlayer Component
        gimyPlayer = new GimyPlayer(this, rootContainer, movieStore, new GimyPlayer.PlayerListener() {
            @Override
            public void onPlaybackStopped() {
                mainSplitLayout.setVisibility(View.VISIBLE);
                if (detailPanelManager.getPlayButton() != null && detailPanelManager.getPlayButton().getTag() != null) {
                    detailPanelManager.updatePlayButtons((String) detailPanelManager.getPlayButton().getTag());
                }
                gridPanelManager.localRefreshGrid();
                View lastFocusedCard = gridPanelManager.getLastFocusedCard();
                if (lastFocusedCard != null) {
                    lastFocusedCard.requestFocus();
                }
            }
        });

        setContentView(rootContainer);

        // Kick off default load
        refreshMovieGrid();
        handleIntent(getIntent());
    }



    private void refreshMovieGrid() {
        String sort = filterBarManager != null ? filterBarManager.getSelectedSort() : "熱門推薦";
        String region = filterBarManager != null ? filterBarManager.getSelectedRegion() : "全部";
        String year = filterBarManager != null ? filterBarManager.getSelectedYear() : "全部";
        Log.i(TAG, "🔍 refreshMovieGrid requested: Sort=" + sort + " | Region=" + region + " | Year=" + year);
        gridContainer.removeAllViews();
        TextView loading = new TextView(this);
        loading.setText("陰間頻道連接中，請稍候...");
        loading.setTextSize(16);
        loading.setTextColor(Color.parseColor("#9AA0A6"));
        loading.setGravity(Gravity.CENTER);
        loading.setPadding(0, 120, 0, 0);
        gridContainer.addView(loading);

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String sort = filterBarManager != null ? filterBarManager.getSelectedSort() : "熱門推薦";
                    String region = filterBarManager != null ? filterBarManager.getSelectedRegion() : "全部";
                    String year = filterBarManager != null ? filterBarManager.getSelectedYear() : "全部";
                    String queryUrl = GimyParser.constructCategoryUrl(sort, region, year);
                    Log.d(TAG, "Constructed query URL: " + queryUrl);
                    String html = GimyParser.fetchHtml(queryUrl);
                    final ArrayList<Movie> parsedMovies = GimyParser.parseMoviesFromHtml(html);

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Log.i(TAG, "Grid updated with " + parsedMovies.size() + " movies.");
                            gridPanelManager.populateGrid(parsedMovies);
                        }
                    });
                } catch (Exception e) {
                    Log.e(TAG, "Error in refreshMovieGrid thread", e);
                }
            }
        }).start();
    }

    private void playMovie(final String playPath, final boolean resume) {
        Log.i(TAG, "🎬 playMovie requested: path=" + playPath + " | resume=" + resume);
        gridContainer.removeAllViews();
        TextView loading = new TextView(this);
        loading.setText("正在分析驚悚影片流，請稍候...");
        loading.setTextSize(16);
        loading.setTextColor(Color.parseColor("#9AA0A6"));
        loading.setGravity(Gravity.CENTER);
        loading.setPadding(0, 120, 0, 0);
        gridContainer.addView(loading);

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String fullVodUrl = "https://gimyplus.com" + playPath;
                    Log.d(TAG, "Fetching movie streaming page HTML from: " + fullVodUrl);
                    String playHtml = GimyParser.fetchHtml(fullVodUrl);
                    final String m3u8Url = GimyParser.parseM3U8Url(playHtml);

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (m3u8Url.isEmpty()) {
                                Log.e(TAG, "parseM3U8Url returned empty for path: " + playPath);
                                tvDetailSynopsis.setText("通靈影片流失敗，該線路或已被陰間屏蔽！");
                            } else {
                                Log.i(TAG, "Found m3u8 stream. Launching player...");
                                startPlayer(m3u8Url, resume);
                            }
                        }
                    });
                } catch (Exception e) {
                    Log.e(TAG, "Error in playMovie analysis thread", e);
                }
            }
        }).start();
    }

    private void startPlayer(String m3u8Url, final boolean resume) {
        initMediaSession();
        if (gimyMediaSession != null) {
            gimyPlayer.setMediaSession(gimyMediaSession);
        }
        mainSplitLayout.setVisibility(View.GONE);
        gimyPlayer.startPlayer(m3u8Url, resume, detailPanelManager.getSelectedMovieId(), detailPanelManager.getSelectedMovieTitle(), detailPanelManager.getSelectedMovieImageUrl(), detailPanelManager.getSelectedMovieSubtitle());
    }

    private void initMediaSession() {
        if (gimyMediaSession == null) {
            gimyMediaSession = new GimyMediaSession(this, new GimyMediaSession.PlaybackController() {
                @Override
                public void onPlayAction() {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (gimyPlayer != null && gimyPlayer.isPlayerActive() && !gimyPlayer.getVideoView().isPlaying()) {
                                gimyPlayer.getVideoView().start();
                                gimyPlayer.setPlayerTitleVisible(false);
                                gimyPlayer.showPlaybackIndicator("▶");
                                if (gimyMediaSession != null) gimyMediaSession.updatePlaybackState(PlaybackState.STATE_PLAYING);
                            }
                        }
                    });
                }

                @Override
                public void onPauseAction() {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (gimyPlayer != null && gimyPlayer.isPlayerActive() && gimyPlayer.getVideoView().isPlaying()) {
                                gimyPlayer.getVideoView().pause();
                                gimyPlayer.setPlayerTitleVisible(true);
                                gimyPlayer.showPlaybackIndicator("❚❚");
                                if (gimyMediaSession != null) gimyMediaSession.updatePlaybackState(PlaybackState.STATE_PAUSED);
                            }
                        }
                    });
                }

                @Override
                public void onStopAction() {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (gimyPlayer != null && gimyPlayer.isPlayerActive()) {
                                gimyPlayer.stopPlayer();
                            }
                        }
                    });
                }

                @Override
                public void onSeekToAction(final long pos) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (gimyPlayer != null && gimyPlayer.isPlayerActive()) {
                                gimyPlayer.getVideoView().seekTo((int) pos);
                                if (gimyMediaSession != null) {
                                    gimyMediaSession.updatePlaybackState(gimyPlayer.getVideoView().isPlaying() ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED);
                                }
                            }
                        }
                    });
                }

                @Override
                public int getCurrentPosition() {
                    return (gimyPlayer != null && gimyPlayer.getVideoView() != null) ? gimyPlayer.getVideoView().getCurrentPosition() : 0;
                }
            });
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // 1. Delegate video playback controls to GimyPlayer Component
        if (gimyPlayer != null && gimyPlayer.handlePlayerKeyDown(keyCode, event)) {
            return true;
        }

        // 2. Main split UI focus handling on BACK key
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            View currentFocus = getCurrentFocus();
            View lastFocusedCard = gridPanelManager.getLastFocusedCard();
            if (currentFocus != null && currentFocus != lastFocusedCard && lastFocusedCard != null) {
                lastFocusedCard.requestFocus();
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (gimyPlayer != null) {
            gimyPlayer.pausePlaybackOnBackground();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (gimyMediaSession != null) {
            gimyMediaSession.release();
            gimyMediaSession = null;
        }
    }

    @Override
    protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(android.content.Intent intent) {
        if (intent != null) {
            if (intent.hasExtra("listState") && intent.hasExtra("movieId")) {
                String movieId = intent.getStringExtra("movieId");
                int state = -1;
                try {
                    String stateStr = intent.getStringExtra("listState");
                    if (stateStr != null) {
                        state = Integer.parseInt(stateStr);
                    } else {
                        state = intent.getIntExtra("listState", -1);
                    }
                } catch (Exception e) {
                    state = intent.getIntExtra("listState", -1);
                }
                Log.i(TAG, "📥 handleIntent: Direct listState request for ID: " + movieId + " to state: " + state);
                if (movieId != null && !movieId.isEmpty() && state != -1) {
                    movieStore.setListState(movieId, state);
                    refreshMovieGrid();
                }
            }

            if (intent.hasExtra("seekPositionMs")) {
                int seekMs = -1;
                try {
                    String seekStr = intent.getStringExtra("seekPositionMs");
                    if (seekStr != null) {
                        seekMs = Integer.parseInt(seekStr);
                    } else {
                        seekMs = intent.getIntExtra("seekPositionMs", -1);
                    }
                } catch (Exception e) {
                    seekMs = intent.getIntExtra("seekPositionMs", -1);
                }
                
                Log.i(TAG, "📥 handleIntent: Direct seek request to " + seekMs + " ms");
                if (seekMs != -1 && gimyPlayer != null && gimyPlayer.isPlayerActive()) {
                    int targetMs = seekMs;
                    if (seekMs < 0) {
                        targetMs = gimyPlayer.getVideoView().getDuration() + seekMs;
                    }
                    Log.i(TAG, "Direct seek to resolved position: " + targetMs + " ms");
                    gimyPlayer.getVideoView().seekTo(targetMs);
                    gimyPlayer.getVideoView().start();
                } else if (seekMs != -1) {
                    pendingSeekMs = seekMs;
                }
            }

            if (detailPanelManager != null) {
                String movieId = intent.getStringExtra("movieId");
                Log.i(TAG, "📥 handleIntent received. Movie ID: " + movieId);
                if (movieId != null && !movieId.isEmpty()) {
                    String title = intent.getStringExtra("movieTitle");
                    String imageUrl = intent.getStringExtra("imageUrl");
                    String subtitle = intent.getStringExtra("subtitle");
                    
                    boolean autoPlay = intent.getBooleanExtra("autoPlay", false) || intent.hasExtra("seekPositionMs");
                    Log.i(TAG, "📥 Restoring Watch Next / Deep Link for: " + title + " | autoPlay: " + autoPlay);
                    isDeepLinkActive = true;
                    // Load movie details asynchronously, and auto-focus play
                    detailPanelManager.loadMovieDetails(movieId, title != null ? title : "", imageUrl != null ? imageUrl : "", "", subtitle != null ? subtitle : "", !autoPlay, autoPlay);
                }
            }
        }
    }
}
