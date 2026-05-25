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
import android.widget.MediaController;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.VideoView;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.media.MediaMetadata;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;

public class MainActivity extends Activity {

    // Filters state
    private String selectedSort = "熱門推薦";
    private String selectedRegion = "全部";
    private String selectedYear = "全部";

    // Filter Options
    private final String[] SORTS = {"熱門推薦", "最新上架", "好評高分"};
    private final String[] REGIONS = {"全部", "泰國", "日本", "韓國", "美國", "台灣", "香港"};
    private final String[] YEARS = {"全部", "2026", "2025", "2024", "2023", "2022", "2021", "2020"};

    // Layout elements
    private LinearLayout mainSplitLayout;
    private FrameLayout playerContainer;
    private VideoView videoView;
    private LinearLayout gridContainer;
    private ScrollView gridScrollView;
    private ScrollView rightScrollView;
    private LinearLayout playButtonLayout;

    // Detail Panel elements
    private TextView tvDetailTitle;
    private TextView tvDetailMeta;
    private TextView tvDetailSynopsis;
    private View btnPlay;
    private View btnPlayRef = null;

    // Persisted preferences & state
    private MovieStore movieStore;
    private ArrayList<Movie> currentMoviesList = new ArrayList<>();
    private String selectedMovieId = "";
    private String selectedMovieTitle = "";
    private GimyMediaSession gimyMediaSession = null;
    private View lastFocusedCard = null;

    // Global loading and custom playback indicators
    private TextView tvLoadingIndicator;
    private TextView tvPlaybackIndicator;
    private TextView tvPlayerTitle;
    private final android.os.Handler indicatorHandler = new android.os.Handler();
    private final Runnable hideIndicatorRunnable = new Runnable() {
        @Override
        public void run() {
            if (tvPlaybackIndicator != null) {
                tvPlaybackIndicator.setVisibility(View.GONE);
            }
        }
    };

    // Custom TV Seek Controller state
    private boolean isSeekingMode = false;
    private long lastAutoSaveTime = 0;
    private int targetSeekTime = 0;
    private int originalPositionBeforeSeek = 0;
    private LinearLayout seekOverlayLayout;
    private TextView tvSeekCurrent;
    private TextView tvSeekTotal;
    private android.widget.SeekBar seekSeekBar;
    private final android.os.Handler seekHandler = new android.os.Handler();

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
                        if (gimyMediaSession != null) gimyMediaSession.updatePlaybackState(videoView.isPlaying() ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED);
                    }
                }

                // Auto-save progress every 60 seconds (60,000 ms) of active playback
                long now = android.os.SystemClock.elapsedRealtime();
                if (now - lastAutoSaveTime >= 60000) {
                    savePlaybackProgress();
                    lastAutoSaveTime = now;
                }

                seekHandler.postDelayed(this, 1000);
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
                    showPlaybackIndicator("▶");
                }
                seekOverlayLayout.setVisibility(View.GONE);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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

        // Filter Rows
        filterContainer.addView(createFilterRow("排序：", SORTS, "Sort"));
        filterContainer.addView(createFilterRow("地區：", REGIONS, "Region"));
        filterContainer.addView(createFilterRow("年份：", YEARS, "Year"));
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

        // 2. Right Scroll Content Container (Zero海報, Title starting at the very top!)
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
        
        // --- TV Premium Scroll and Focus Setup ---
        tvDetailSynopsis.setFocusable(true);
        tvDetailSynopsis.setClickable(true);
        tvDetailSynopsis.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (btnPlayRef != null && btnPlayRef.isEnabled()) {
                    btnPlayRef.requestFocus();
                }
            }
        });
        
        tvDetailSynopsis.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    v.setBackgroundColor(Color.parseColor("#303134")); // Highlight
                    tvDetailSynopsis.setTextColor(Color.WHITE);
                } else {
                    v.setBackgroundColor(Color.TRANSPARENT);
                    tvDetailSynopsis.setTextColor(Color.parseColor("#DADCE0"));
                }
            }
        });

        tvDetailSynopsis.setOnKeyListener(new View.OnKeyListener() {
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
                            if (btnPlayRef != null) {
                                btnPlayRef.requestFocus(); // Focus to play/resume button
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
        rightScrollContent.addView(tvDetailSynopsis);

        // Action Buttons Container (Holds compact buttons, aligned to the LEFT!)
        playButtonLayout = new LinearLayout(this);
        playButtonLayout.setOrientation(LinearLayout.HORIZONTAL);
        playButtonLayout.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL); // Align LEFT!
        LinearLayout.LayoutParams playBtnContainerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        playBtnContainerParams.setMargins(0, 30, 0, 0); // space above buttons
        playButtonLayout.setLayoutParams(playBtnContainerParams);
        rightScrollContent.addView(playButtonLayout);

        // Initial empty state for buttons
        updatePlayButtons("");

        rightScrollView.addView(rightScrollContent);
        rightPanel.addView(rightScrollView);

        // Assemble split screen
        mainSplitLayout.addView(leftPanel);
        mainSplitLayout.addView(rightPanel);
        rootContainer.addView(mainSplitLayout);

        // 2. Full-Screen Video Player Layer
        playerContainer = new FrameLayout(this);
        playerContainer.setBackgroundColor(Color.BLACK);
        playerContainer.setVisibility(View.GONE);

        videoView = new VideoView(this);
        FrameLayout.LayoutParams playerParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER);
        videoView.setLayoutParams(playerParams);
        playerContainer.addView(videoView);

        // Loading overlay
        tvLoadingIndicator = new TextView(this);
        tvLoadingIndicator.setText("影片載入中，請稍候...");
        tvLoadingIndicator.setTextSize(20);
        tvLoadingIndicator.setTextColor(Color.WHITE);
        FrameLayout.LayoutParams loaderParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        tvLoadingIndicator.setLayoutParams(loaderParams);
        playerContainer.addView(tvLoadingIndicator);

        // Custom playback action indicator (focus-free and intuitive TV player!)
        tvPlaybackIndicator = new TextView(this);
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
        tvPlayerTitle = new TextView(this);
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
        seekOverlayLayout = new LinearLayout(this);
        seekOverlayLayout.setOrientation(LinearLayout.HORIZONTAL);
        seekOverlayLayout.setGravity(Gravity.CENTER_VERTICAL);
        seekOverlayLayout.setBackgroundColor(Color.parseColor("#CC121212")); // 80% opacity dark grey
        seekOverlayLayout.setPadding(50, 30, 50, 30);
        seekOverlayLayout.setVisibility(View.GONE);
        
        FrameLayout.LayoutParams seekOverlayParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        seekOverlayLayout.setLayoutParams(seekOverlayParams);

        // Current Seek Time
        tvSeekCurrent = new TextView(this);
        tvSeekCurrent.setText("00:00");
        tvSeekCurrent.setTextColor(Color.WHITE);
        tvSeekCurrent.setTextSize(14);
        tvSeekCurrent.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        tvSeekCurrent.setPadding(0, 0, 30, 0);
        seekOverlayLayout.addView(tvSeekCurrent);

        // SeekBar (Timeline)
        seekSeekBar = new android.widget.SeekBar(this);
        seekSeekBar.setFocusable(false); // DO NOT allow remote control focus to get stuck!
        seekSeekBar.setClickable(false);
        LinearLayout.LayoutParams seekBarParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f); // Takes up all middle space
        seekSeekBar.setLayoutParams(seekBarParams);
        seekOverlayLayout.addView(seekSeekBar);

        // Total Duration Time
        tvSeekTotal = new TextView(this);
        tvSeekTotal.setText("00:00");
        tvSeekTotal.setTextColor(Color.parseColor("#9AA0A6"));
        tvSeekTotal.setTextSize(14);
        tvSeekTotal.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        tvSeekTotal.setPadding(30, 0, 0, 0);
        seekOverlayLayout.addView(tvSeekTotal);

        playerContainer.addView(seekOverlayLayout);

        rootContainer.addView(playerContainer);
        setContentView(rootContainer);

        // Kick off default load
        refreshMovieGrid();
    }

    private View createFilterRow(String label, final String[] options, final String type) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, 5, 0, 5);

        TextView lbl = new TextView(this);
        lbl.setText(label);
        lbl.setTextSize(14);
        lbl.setTextColor(Color.parseColor("#9AA0A6"));
        lbl.setPadding(0, 0, 15, 0);
        lbl.setMinimumWidth(100);
        row.addView(lbl);

        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        scroll.setLayoutParams(scrollParams);

        final LinearLayout itemsLayout = new LinearLayout(this);
        itemsLayout.setOrientation(LinearLayout.HORIZONTAL);

        for (final String opt : options) {
            final TextView item = new TextView(this);
            item.setText(opt);
            item.setTextSize(14);
            item.setPadding(24, 12, 24, 12);
            item.setFocusable(true);
            
            LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            itemParams.setMargins(0, 0, 15, 0);
            item.setLayoutParams(itemParams);

            updateFilterItemStyle(item, opt, type, false);

            item.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean hasFocus) {
                    updateFilterItemStyle(item, opt, type, hasFocus);
                    if (hasFocus) {
                        v.setScaleX(1.08f);
                        v.setScaleY(1.08f);
                    } else {
                        v.setScaleX(1.0f);
                        v.setScaleY(1.0f);
                    }
                }
            });

            item.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (type.equals("Sort")) selectedSort = opt;
                    else if (type.equals("Region")) selectedRegion = opt;
                    else if (type.equals("Year")) selectedYear = opt;

                    for (int i = 0; i < itemsLayout.getChildCount(); i++) {
                        TextView child = (TextView) itemsLayout.getChildAt(i);
                        updateFilterItemStyle(child, options[i], type, child.isFocused());
                    }
                    refreshMovieGrid();
                }
            });

            itemsLayout.addView(item);
        }

        scroll.addView(itemsLayout);
        row.addView(scroll);
        return row;
    }

    private void updateFilterItemStyle(TextView item, String opt, String type, boolean hasFocus) {
        boolean isSelected = false;
        if (type.equals("Sort") && selectedSort.equals(opt)) isSelected = true;
        else if (type.equals("Region") && selectedRegion.equals(opt)) isSelected = true;
        else if (type.equals("Year") && selectedYear.equals(opt)) isSelected = true;

        if (hasFocus) {
            item.setBackgroundColor(Color.parseColor("#4285F4")); // Google Blue focus
            item.setTextColor(Color.WHITE);
            item.setTypeface(Typeface.DEFAULT_BOLD);
        } else if (isSelected) {
            if (type.equals("Region")) {
                item.setBackgroundColor(Color.parseColor("#EA4335")); // Google Red
            } else if (type.equals("Sort")) {
                item.setBackgroundColor(Color.parseColor("#FBBC05")); // Google Yellow
            } else {
                item.setBackgroundColor(Color.parseColor("#34A853")); // Google Green
            }
            item.setTextColor(Color.WHITE);
            item.setTypeface(Typeface.DEFAULT_BOLD);
        } else {
            item.setBackgroundColor(Color.parseColor("#1C1D1F"));
            item.setTextColor(Color.parseColor("#9AA0A6"));
            item.setTypeface(Typeface.DEFAULT);
        }
    }

    private void refreshMovieGrid() {
        gridContainer.removeAllViews();
        
        TextView tvLoading = new TextView(this);
        tvLoading.setText("正在召喚恐怖電影壁牆...");
        tvLoading.setTextSize(18);
        tvLoading.setTextColor(Color.parseColor("#FBBC05"));
        tvLoading.setGravity(Gravity.CENTER);
        tvLoading.setPadding(0, 100, 0, 0);
        gridContainer.addView(tvLoading);

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String[] params = new String[12];
                    for (int i = 0; i < params.length; i++) params[i] = "";
                    params[0] = "10";
                    if (!selectedRegion.equals("全部")) {
                        params[1] = URLEncoder.encode(selectedRegion, "UTF-8");
                    }
                    params[2] = selectedSort.equals("最新上架") ? "time" : (selectedSort.equals("熱門推薦") ? "hits" : "score");
                    if (!selectedYear.equals("全部")) {
                        params[11] = selectedYear;
                    }

                    StringBuilder sb = new StringBuilder("https://gimyplus.com/show/");
                    for (int i = 0; i < params.length; i++) {
                        sb.append(params[i]);
                        if (i < params.length - 1) sb.append("-");
                    }
                    sb.append(".html");
                    String url = sb.toString();

                    final String html = GimyParser.fetchHtml(url);
                    final ArrayList<Movie> parsedMovies = GimyParser.parseMoviesFromHtml(html);

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            populateGrid(parsedMovies);
                        }
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private void populateGrid(final ArrayList<Movie> movies) {
        gridContainer.removeAllViews();
        if (movies.isEmpty()) {
            this.currentMoviesList = movies;
            TextView empty = new TextView(this);
            empty.setText("在陰間迷路了，未找到相符的鬼片！\n請更換篩選條件看看。");
            empty.setTextSize(16);
            empty.setTextColor(Color.parseColor("#9AA0A6"));
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, 120, 0, 0);
            gridContainer.addView(empty);
            return;
        }

        // Re-arrange list: pull watchlist (list_state == 1) to the very front
        java.util.HashMap<String, Integer> statesMap = new java.util.HashMap<>();
        for (Movie m : movies) {
            statesMap.put(m.id, movieStore.getListState(m.id));
        }
        final ArrayList<Movie> sortedMovies = MovieSorter.sortMovies(movies, statesMap);
        this.currentMoviesList = sortedMovies; // Cache sorted list for rapid local refresh

        int rowCount = (int) Math.ceil(sortedMovies.size() / 3f);
        for (int r = 0; r < rowCount; r++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            row.setPadding(0, 10, 0, 10);

            for (int c = 0; c < 3; c++) {
                final int idx = r * 3 + c;
                if (idx >= sortedMovies.size()) {
                    View spacer = new View(this);
                    LinearLayout.LayoutParams spacerParams = new LinearLayout.LayoutParams(0, 1, 1f);
                    spacerParams.setMargins(10, 0, 10, 0);
                    spacer.setLayoutParams(spacerParams);
                    row.addView(spacer);
                    continue;
                }

                final Movie m = sortedMovies.get(idx);

                // Movie Card Container
                final LinearLayout card = new LinearLayout(this);
                card.setOrientation(LinearLayout.VERTICAL);
                LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                cardParams.setMargins(10, 0, 10, 0);
                card.setLayoutParams(cardParams);
                card.setPadding(15, 15, 15, 15);
                card.setBackgroundColor(Color.parseColor("#1C1D1F"));
                card.setFocusable(true);
                card.setClickable(true);

                // Image Poster
                ImageView ivPoster = new ImageView(this);
                LinearLayout.LayoutParams imgParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 200);
                ivPoster.setLayoutParams(imgParams);
                ivPoster.setScaleType(ImageView.ScaleType.CENTER_CROP);
                ivPoster.setBackgroundColor(Color.BLACK);
                ImageLoader.loadImage(m.imageUrl, ivPoster);
                card.addView(ivPoster);

                // Title
                final TextView tvTitle = new TextView(this);
                int listState = movieStore.getListState(m.id);
                String prefix = "";
                if (listState == 1) prefix = "📝 ";
                else if (listState == 2) prefix = "❤️ ";
                else if (listState == 3) prefix = "💩 ";
                tvTitle.setText(prefix + m.title);
                tvTitle.setTextSize(14);
                tvTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
                tvTitle.setTextColor(Color.WHITE);
                tvTitle.setSingleLine(true);
                tvTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
                tvTitle.setPadding(0, 12, 0, 4);
                card.addView(tvTitle);

                // Status tag - Displays previous playback progress dynamically (replacing "HD")
                TextView tvNote = new TextView(this);
                int savedPos = movieStore.getProgressPos(m.id);
                int savedDur = movieStore.getProgressDur(m.id);
                String progressText = "";
                if (savedDur > 0 && savedPos > 0) {
                    int pct = (savedPos * 100) / savedDur;
                    pct = Math.max(1, Math.min(100, pct));
                    progressText = "▶ " + pct + "%";
                }

                if (progressText.isEmpty()) {
                    tvNote.setVisibility(View.GONE);
                } else {
                    tvNote.setVisibility(View.VISIBLE);
                    tvNote.setText(progressText);
                }
                tvNote.setTextSize(11);
                tvNote.setTextColor(Color.parseColor("#FBBC05")); // Google Yellow progress tag
                tvNote.setSingleLine(true);
                card.addView(tvNote);

                // Focus events
                card.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                    @Override
                    public void onFocusChange(View v, boolean hasFocus) {
                        if (hasFocus) {
                            lastFocusedCard = card; // Save current card reference
                            card.setBackgroundColor(Color.parseColor("#303134")); // Light Slate gray
                            card.setScaleX(1.05f);
                            card.setScaleY(1.05f);
                            tvTitle.setTextColor(Color.parseColor("#4285F4")); // Highlight text with Google Blue

                            loadMovieDetails(m.id, m.title, m.imageUrl, m.note, m.subtitle);
                        } else {
                            card.setBackgroundColor(Color.parseColor("#1C1D1F"));
                            card.setScaleX(1.0f);
                            card.setScaleY(1.0f);
                            tvTitle.setTextColor(Color.WHITE);
                        }
                    }
                });
                // Click card to shift focus to the right panel for reading synopsis or playing!
                card.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (btnPlayRef != null && btnPlayRef.isEnabled()) {
                            // Shift focus to the Synopsis Text first, so they stay at the top and can read
                            tvDetailSynopsis.requestFocus();
                        } else {
                            android.widget.Toast.makeText(MainActivity.this, "影片載入中，請稍候...", android.widget.Toast.LENGTH_SHORT).show();
                        }
                    }
                });

                row.addView(card);
            }
            gridContainer.addView(row);
        }

        if (gridContainer.getChildCount() > 0) {
            LinearLayout firstRow = (LinearLayout) gridContainer.getChildAt(0);
            if (firstRow.getChildCount() > 0) {
                firstRow.getChildAt(0).requestFocus();
            }
        }
    }

    private void localRefreshGrid() {
        populateGrid(currentMoviesList);
    }

    private void loadMovieDetails(final String id, final String title, final String imageUrl, final String note, final String subtitle) {
        selectedMovieId = id;
        selectedMovieTitle = title;
        if (rightScrollView != null) {
            rightScrollView.scrollTo(0, 0);
        }

        tvDetailTitle.setText("《" + title + "》");
        tvDetailMeta.setText(String.format(" 地區/演員：%s\n 狀態：%s", subtitle.isEmpty() ? "未知" : subtitle, note));
        tvDetailSynopsis.setText("正在通靈獲取恐怖故事簡介...");

        new Thread(new Runnable() {
            @Override
            public void run() {
                final String detailHtml = GimyParser.fetchHtml("https://gimyplus.com/vod/" + id + ".html");
                if (!id.equals(selectedMovieId)) return; // Discard outdated requests

                String[] details = GimyParser.parseMovieDetails(detailHtml);
                final String synopsis = details[0];
                final String playPath = details[1];

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (id.equals(selectedMovieId)) {
                            tvDetailSynopsis.setText(synopsis);
                            updatePlayButtons(playPath);
                        }
                    }
                });
            }
        }).start();
    }

    private int getListState(String movieId) {
        return movieStore.getListState(movieId);
    }

    private void setListState(String movieId, int state) {
        movieStore.setListState(movieId, state);
    }

    // Dynamic Builder for Play Buttons (Compact Square Symbols only, left-aligned)
    private void updatePlayButtons(final String playPath) {
        updatePlayButtons(playPath, false);
    }

    private void updatePlayButtons(final String playPath, final boolean focusListButton) {
        playButtonLayout.removeAllViews();
        int size = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 48, getResources().getDisplayMetrics());

        if (selectedMovieId == null || selectedMovieId.isEmpty()) {
            return;
        }

        // 1. Play / Resume Button OR Disabled Button
        if (playPath == null || playPath.isEmpty()) {
            Button btnDisabled = new Button(this);
            btnDisabled.setText("✕");
            btnDisabled.setTextSize(20);
            btnDisabled.setTextColor(Color.parseColor("#9AA0A6"));
            btnDisabled.setEnabled(false);
            btnDisabled.setBackgroundColor(Color.parseColor("#3C4043"));
            btnDisabled.setPadding(0, 0, 0, 0);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.setMargins(0, 0, 20, 0);
            btnDisabled.setLayoutParams(lp);
            playButtonLayout.addView(btnDisabled);
        } else {
            int pos = movieStore.getProgressPos(selectedMovieId);
            int dur = movieStore.getProgressDur(selectedMovieId);
            final boolean hasProgress = (dur > 0 && pos > 0);

            // Play / Resume Button (▶)
            final Button btnPlayNew = new Button(this);
            btnPlayNew.setText("▶");
            btnPlayNew.setTag(playPath);
            btnPlayNew.setTextSize(22);
            btnPlayNew.setTextColor(Color.WHITE);
            btnPlayNew.setFocusable(true);
            btnPlayNew.setBackgroundColor(Color.parseColor("#137333")); // Dark green
            btnPlayNew.setPadding(0, 0, 0, 0);
            
            LinearLayout.LayoutParams lpPlay = new LinearLayout.LayoutParams(size, size);
            lpPlay.setMargins(0, 0, 20, 0); // space between buttons
            btnPlayNew.setLayoutParams(lpPlay);

            btnPlayNew.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean hasFocus) {
                    if (hasFocus) {
                        v.setBackgroundColor(Color.parseColor("#34A853")); // Google Green focus
                        v.setScaleX(1.08f); v.setScaleY(1.08f);
                    } else {
                        v.setBackgroundColor(Color.parseColor("#137333"));
                        v.setScaleX(1.0f); v.setScaleY(1.0f);
                    }
                }
            });

            btnPlayNew.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    playMovie(playPath, hasProgress); // Resume playback if progress exists!
                }
            });

            playButtonLayout.addView(btnPlayNew);
            this.btnPlayRef = btnPlayNew;
            this.btnPlay = btnPlayNew;

            // Restart/Loop Button (↺) - Only visible if progress exists
            if (hasProgress) {
                Button btnRestart = new Button(this);
                btnRestart.setText("↺"); // standard recycle/loop symbol
                btnRestart.setTextSize(22);
                btnRestart.setTextColor(Color.WHITE);
                btnRestart.setFocusable(true);
                btnRestart.setBackgroundColor(Color.parseColor("#3C4043")); // Charcoal Gray
                btnRestart.setPadding(0, 0, 0, 0);
                
                LinearLayout.LayoutParams lpRestart = new LinearLayout.LayoutParams(size, size);
                lpRestart.setMargins(0, 0, 20, 0);
                btnRestart.setLayoutParams(lpRestart);

                btnRestart.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                    @Override
                    public void onFocusChange(View v, boolean hasFocus) {
                        if (hasFocus) {
                            v.setBackgroundColor(Color.parseColor("#EA4335")); // Google Red focus for restart warning
                            v.setScaleX(1.08f); v.setScaleY(1.08f);
                        } else {
                            v.setBackgroundColor(Color.parseColor("#3C4043"));
                            v.setScaleX(1.0f); v.setScaleY(1.0f);
                        }
                    }
                });

                btnRestart.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        playMovie(playPath, false); // Play from beginning (restart)
                    }
                });

                playButtonLayout.addView(btnRestart);
            }
        }

        // 2. Playlist / State Button (+) / (📝) / (❤️) / (💩)
        final Button btnListState = new Button(this);
        int listState = getListState(selectedMovieId);
        String stateText = "+";
        if (listState == 1) stateText = "📝";
        else if (listState == 2) stateText = "❤️";
        else if (listState == 3) stateText = "💩";

        btnListState.setText(stateText);
        btnListState.setTextSize(20);
        btnListState.setTextColor(Color.WHITE);
        btnListState.setFocusable(true);
        btnListState.setBackgroundColor(Color.parseColor("#3C4043")); // Charcoal Gray
        btnListState.setPadding(0, 0, 0, 0);

        LinearLayout.LayoutParams lpList = new LinearLayout.LayoutParams(size, size);
        btnListState.setLayoutParams(lpList);

        btnListState.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    v.setBackgroundColor(Color.parseColor("#1A73E8")); // Google Blue focus
                    v.setScaleX(1.08f); v.setScaleY(1.08f);
                } else {
                    v.setBackgroundColor(Color.parseColor("#3C4043"));
                    v.setScaleX(1.0f); v.setScaleY(1.0f);
                }
            }
        });

        btnListState.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int current = getListState(selectedMovieId);
                int next = (current + 1) % 4;
                setListState(selectedMovieId, next);

                // Update left panel's card title instantly without full refresh
                if (lastFocusedCard != null && lastFocusedCard instanceof LinearLayout) {
                    LinearLayout card = (LinearLayout) lastFocusedCard;
                    if (card.getChildCount() > 1 && card.getChildAt(1) instanceof TextView) {
                        TextView tvCardTitle = (TextView) card.getChildAt(1);
                        String titleText = tvDetailTitle.getText().toString();
                        if (titleText.startsWith("《") && titleText.endsWith("》")) {
                            String originalTitle = titleText.substring(1, titleText.length() - 1);
                            String prefix = "";
                            if (next == 1) prefix = "📝 ";
                            else if (next == 2) prefix = "❤️ ";
                            else if (next == 3) prefix = "💩 ";
                            tvCardTitle.setText(prefix + originalTitle);
                        }
                    }
                }

                updatePlayButtons(playPath, true);
            }
        });

        // Strict vertical focus constraints
        View.OnKeyListener boundKeyListener = new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                        tvDetailSynopsis.requestFocus();
                        return true;
                    } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                        return true; // lock down
                    }
                }
                return false;
            }
        };

        // Apply focus key listener to all active children in playButtonLayout
        for (int i = 0; i < playButtonLayout.getChildCount(); i++) {
            playButtonLayout.getChildAt(i).setOnKeyListener(boundKeyListener);
        }
        btnListState.setOnKeyListener(boundKeyListener);

        playButtonLayout.addView(btnListState);

        if (focusListButton) {
            btnListState.post(new Runnable() {
                @Override
                public void run() {
                    btnListState.requestFocus();
                }
            });
        }
    }

    private void playMovie(final String playPath, final boolean resume) {
        if (playPath == null || playPath.isEmpty()) return;

        tvDetailSynopsis.setText("正在分析驚悚影片流，請稍候...");
        if (btnPlayRef != null) btnPlayRef.setEnabled(false);

        new Thread(new Runnable() {
            @Override
            public void run() {
                final String playHtml = GimyParser.fetchHtml("https://gimyplus.com/" + playPath);
                final String m3u8Url = GimyParser.parseM3U8Url(playHtml);

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (btnPlayRef != null) btnPlayRef.setEnabled(true);
                        updatePlayButtons(playPath);

                        if (m3u8Url.isEmpty()) {
                            tvDetailSynopsis.setText("通靈影片流失敗，該線路或已被陰間屏蔽！");
                        } else {
                            startPlayer(m3u8Url, resume);
                        }
                    }
                });
            }
        }).start();
    }

    private void startPlayer(String m3u8Url, final boolean resume) {
        lastAutoSaveTime = android.os.SystemClock.elapsedRealtime();
        tvLoadingIndicator.setVisibility(View.VISIBLE);
        mainSplitLayout.setVisibility(View.GONE);
        playerContainer.setVisibility(View.VISIBLE);

        initMediaSession();
        if (gimyMediaSession != null) {
            gimyMediaSession.setActive(true);
            gimyMediaSession.updateMediaMetadata(selectedMovieTitle != null && !selectedMovieTitle.isEmpty() ? selectedMovieTitle : "Gimy TV", -1);
            gimyMediaSession.updatePlaybackState(PlaybackState.STATE_BUFFERING);
        }

        videoView.setMediaController(null); // Completely disable clunky default MediaController
        videoView.setVideoPath(m3u8Url);
        videoView.requestFocus();

        final int savedPos = resume ? movieStore.getProgressPos(selectedMovieId) : 0;

        videoView.setOnPreparedListener(new android.media.MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(android.media.MediaPlayer mp) {
                tvLoadingIndicator.setVisibility(View.GONE);
                if (savedPos > 0) {
                    videoView.seekTo(savedPos);
                }
                videoView.start();
                if (gimyMediaSession != null) {
                    gimyMediaSession.updateMediaMetadata(selectedMovieTitle, videoView.getDuration());
                    gimyMediaSession.updatePlaybackState(PlaybackState.STATE_PLAYING);
                }
                // Start the timeline progress update loop!
                seekHandler.removeCallbacks(updateProgressRunnable);
                seekHandler.post(updateProgressRunnable);
            }
        });

        videoView.setOnErrorListener(new android.media.MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(android.media.MediaPlayer mp, int what, int extra) {
                tvLoadingIndicator.setVisibility(View.GONE);
                android.widget.Toast.makeText(MainActivity.this, "影片載入失敗，可能需要切換線路！", android.widget.Toast.LENGTH_LONG).show();
                stopPlayer();
                return true;
            }
        });

        videoView.setOnCompletionListener(new android.media.MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(android.media.MediaPlayer mp) {
                stopPlayer();
            }
        });
    }

    private void savePlaybackProgress() {
        if (videoView != null && selectedMovieId != null && !selectedMovieId.isEmpty()) {
            int pos = videoView.getCurrentPosition();
            int dur = videoView.getDuration();
            if (dur > 0 && pos > 0) {
                movieStore.savePlaybackProgress(selectedMovieId, pos, dur);
            }
        }
    }

    private void stopPlayer() {
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
        mainSplitLayout.setVisibility(View.VISIBLE);
        
        if (btnPlayRef != null && btnPlayRef.getTag() != null) {
            updatePlayButtons((String) btnPlayRef.getTag());
        }

        localRefreshGrid(); // Instantly refresh grid

        if (lastFocusedCard != null) {
            lastFocusedCard.requestFocus(); // Restore focus
        }
    }

    private void showPlaybackIndicator(String text) {
        if (tvPlaybackIndicator == null) return;
        tvPlaybackIndicator.setText(text);
        tvPlaybackIndicator.setVisibility(View.VISIBLE);
        indicatorHandler.removeCallbacks(hideIndicatorRunnable);
        indicatorHandler.postDelayed(hideIndicatorRunnable, 1200); // Fades out after 1.2 seconds
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // 1. If video player is active, handle focus-free custom gesture-like controls!
        if (playerContainer.getVisibility() == View.VISIBLE) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                if (isSeekingMode) {
                    // Confirm seek instantly!
                    isSeekingMode = false;
                    videoView.seekTo(targetSeekTime);
                    videoView.start();
                    setPlayerTitleVisible(false);
                    showPlaybackIndicator("▶");
                    
                    // Keep progress bar visible briefly (2s) so they see play resumed
                    seekHandler.removeCallbacks(hideSeekOverlayRunnable);
                    seekHandler.postDelayed(hideSeekOverlayRunnable, 2000);
                } else {
                    // Toggle pause/play
                    if (videoView.isPlaying()) {
                        videoView.pause();
                        setPlayerTitleVisible(true);
                        showPlaybackIndicator("❚❚");
                    } else {
                        videoView.start();
                        setPlayerTitleVisible(false);
                        showPlaybackIndicator("▶");
                    }
                    // Show progress overlay so they can see current position
                    if (seekOverlayLayout != null) {
                        seekOverlayLayout.setVisibility(View.VISIBLE);
                        seekSeekBar.setMax(videoView.getDuration());
                        seekSeekBar.setProgress(videoView.getCurrentPosition());
                        tvSeekCurrent.setText(formatTime(videoView.getCurrentPosition()));
                        tvSeekTotal.setText(formatTime(videoView.getDuration()));
                    }
                    
                    // Hide overlay after 4 seconds of no activity
                    seekHandler.removeCallbacks(hideSeekOverlayRunnable);
                    seekHandler.postDelayed(hideSeekOverlayRunnable, 4000);
                }
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                if (!isSeekingMode) {
                    isSeekingMode = true;
                    originalPositionBeforeSeek = videoView.getCurrentPosition();
                    targetSeekTime = originalPositionBeforeSeek;
                    videoView.pause();
                    setPlayerTitleVisible(true);
                    showPlaybackIndicator("❚❚");
                    if (seekOverlayLayout != null) {
                        seekOverlayLayout.setVisibility(View.VISIBLE);
                    }
                }
                targetSeekTime = Math.max(0, targetSeekTime - 30000); // 30s jump
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
                
                // Re-schedule auto-commit on 5 seconds of inactivity
                seekHandler.removeCallbacks(hideSeekOverlayRunnable);
                seekHandler.postDelayed(hideSeekOverlayRunnable, 5000);
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                if (!isSeekingMode) {
                    isSeekingMode = true;
                    originalPositionBeforeSeek = videoView.getCurrentPosition();
                    targetSeekTime = originalPositionBeforeSeek;
                    videoView.pause();
                    setPlayerTitleVisible(true);
                    showPlaybackIndicator("❚❚");
                    if (seekOverlayLayout != null) {
                        seekOverlayLayout.setVisibility(View.VISIBLE);
                    }
                }
                targetSeekTime = Math.min(videoView.getDuration(), targetSeekTime + 30000); // 30s jump
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
                
                // Re-schedule auto-commit on 5 seconds of inactivity
                seekHandler.removeCallbacks(hideSeekOverlayRunnable);
                seekHandler.postDelayed(hideSeekOverlayRunnable, 5000);
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_BACK) {
                if (isSeekingMode) {
                    // Cancel seek! Return to original position and resume play
                    isSeekingMode = false;
                    videoView.seekTo(originalPositionBeforeSeek);
                    videoView.start();
                    setPlayerTitleVisible(false);
                    showPlaybackIndicator("▶");
                    if (seekOverlayLayout != null) {
                        seekOverlayLayout.setVisibility(View.GONE);
                    }
                    seekHandler.removeCallbacks(hideSeekOverlayRunnable);
                    return true;
                } else {
                    stopPlayer();
                    return true;
                }
            }
        }

        // 2. Main split UI focus handling on BACK key
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            View currentFocus = getCurrentFocus();
            if (currentFocus != null && currentFocus != lastFocusedCard && lastFocusedCard != null) {
                lastFocusedCard.requestFocus();
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    private void initMediaSession() {
        if (gimyMediaSession == null) {
            gimyMediaSession = new GimyMediaSession(this, new GimyMediaSession.PlaybackController() {
                @Override
                public void onPlayAction() {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (videoView != null && playerContainer.getVisibility() == View.VISIBLE && !videoView.isPlaying()) {
                                videoView.start();
                                setPlayerTitleVisible(false);
                                showPlaybackIndicator("▶");
                                if (gimyMediaSession != null) {
                                    gimyMediaSession.updatePlaybackState(PlaybackState.STATE_PLAYING);
                                }
                            }
                        }
                    });
                }

                @Override
                public void onPauseAction() {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (videoView != null && playerContainer.getVisibility() == View.VISIBLE && videoView.isPlaying()) {
                                videoView.pause();
                                setPlayerTitleVisible(true);
                                showPlaybackIndicator("❚❚");
                                if (gimyMediaSession != null) {
                                    gimyMediaSession.updatePlaybackState(PlaybackState.STATE_PAUSED);
                                }
                            }
                        }
                    });
                }

                @Override
                public void onStopAction() {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (playerContainer.getVisibility() == View.VISIBLE) {
                                stopPlayer();
                            }
                        }
                    });
                }

                @Override
                public void onSeekToAction(final long pos) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (videoView != null && playerContainer.getVisibility() == View.VISIBLE) {
                                videoView.seekTo((int) pos);
                                if (gimyMediaSession != null) {
                                    gimyMediaSession.updatePlaybackState(videoView.isPlaying() ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED);
                                }
                            }
                        }
                    });
                }

                @Override
                public int getCurrentPosition() {
                    return (videoView != null) ? videoView.getCurrentPosition() : 0;
                }
            });
        }
    }

    private void setPlayerTitleVisible(boolean visible) {
        if (tvPlayerTitle != null) {
            if (visible) {
                tvPlayerTitle.setText("《" + selectedMovieTitle + "》 - 暫停");
                tvPlayerTitle.setVisibility(View.VISIBLE);
            } else {
                tvPlayerTitle.setVisibility(View.GONE);
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        seekHandler.removeCallbacks(updateProgressRunnable);
        seekHandler.removeCallbacks(hideSeekOverlayRunnable);
        if (playerContainer != null && playerContainer.getVisibility() == View.VISIBLE) {
            savePlaybackProgress();
            if (videoView != null && videoView.isPlaying()) {
                videoView.pause();
                setPlayerTitleVisible(true);
                showPlaybackIndicator("❚❚");
            }
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
}
