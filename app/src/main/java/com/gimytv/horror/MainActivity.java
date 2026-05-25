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
        
        // --- TV Premium Scroll and Focus Setup ---
        tvDetailSynopsis.setFocusable(true);
        tvDetailSynopsis.setClickable(true);
        
        tvDetailSynopsis.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (detailPanelManager != null && detailPanelManager.getPlayButton() != null && detailPanelManager.getPlayButton().isEnabled()) {
                    detailPanelManager.getPlayButton().requestFocus();
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
                if (detailPanelManager != null) {
                    detailPanelManager.loadMovieDetails(movie.id, movie.title, movie.imageUrl, movie.note, movie.subtitle);
                }
            }

            @Override
            public void onMovieCardClicked(Movie movie, View card) {
                if (detailPanelManager != null && detailPanelManager.getPlayButton() != null && detailPanelManager.getPlayButton().isEnabled()) {
                    tvDetailSynopsis.requestFocus();
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

    private View createFilterRow(String label, final String[] options, final String type) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, 5, 0, 5);

        TextView lbl = new TextView(this);
        lbl.setText(label);
        lbl.setTextSize(14);
        lbl.setTextColor(Color.parseColor("#9AA0A6"));
        lbl.setPadding(0, 0, 20, 0);
        row.addView(lbl);

        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout optionsLayout = new LinearLayout(this);
        optionsLayout.setOrientation(LinearLayout.HORIZONTAL);

        for (final String opt : options) {
            final TextView item = new TextView(this);
            item.setText(opt);
            item.setTextSize(14);
            item.setFocusable(true);
            item.setClickable(true);
            item.setPadding(30, 10, 30, 10);
            
            updateFilterItemStyle(item, opt, type, false);

            item.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean hasFocus) {
                    updateFilterItemStyle(item, opt, type, hasFocus);
                }
            });

            item.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if ("Sort".equals(type)) selectedSort = opt;
                    else if ("Region".equals(type)) selectedRegion = opt;
                    else if ("Year".equals(type)) selectedYear = opt;

                    refreshMovieGrid();
                }
            });

            optionsLayout.addView(item);
        }
        scroll.addView(optionsLayout);
        row.addView(scroll);
        return row;
    }

    private void updateFilterItemStyle(TextView item, String opt, String type, boolean hasFocus) {
        boolean isSelected = false;
        if ("Sort".equals(type)) isSelected = opt.equals(selectedSort);
        else if ("Region".equals(type)) isSelected = opt.equals(selectedRegion);
        else if ("Year".equals(type)) isSelected = opt.equals(selectedYear);

        if (hasFocus) {
            item.setBackgroundColor(Color.parseColor("#3C4043")); // focused dark grey
            item.setTextColor(Color.WHITE);
        } else if (isSelected) {
            if ("Sort".equals(type)) item.setBackgroundColor(Color.parseColor("#1A73E8")); // Blue tag
            else if ("Region".equals(type)) item.setBackgroundColor(Color.parseColor("#C5221F")); // Red tag
            else if ("Year".equals(type)) item.setBackgroundColor(Color.parseColor("#137333")); // Green tag
            item.setTextColor(Color.WHITE);
        } else {
            item.setBackgroundColor(Color.TRANSPARENT);
            item.setTextColor(Color.parseColor("#9AA0A6"));
        }
    }

    private void refreshMovieGrid() {
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
                    String sortParam = "熱門推薦".equals(selectedSort) ? "hot" : ("最新上架".equals(selectedSort) ? "time" : "score");
                    String regionParam = "全部".equals(selectedRegion) ? "" : selectedRegion;
                    String yearParam = "全部".equals(selectedYear) ? "" : selectedYear;

                    // Construct MacCMS Standard Show URL with exactly 11 hyphens (12 parameters fields)
                    String[] parts = new String[12];
                    parts[0] = "10"; // '10' is the 'Horror' Category ID on gimyplus.com
                    parts[1] = URLEncoder.encode(regionParam, "UTF-8");
                    parts[2] = "hot".equals(sortParam) ? "hits" : sortParam;
                    parts[3] = "";
                    parts[4] = "";
                    parts[5] = "";
                    parts[6] = "";
                    parts[7] = "";
                    parts[8] = "";
                    parts[9] = "";
                    parts[10] = "";
                    parts[11] = yearParam.isEmpty() ? ".html" : yearParam + ".html";

                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < parts.length; i++) {
                        sb.append(parts[i]);
                        if (i < parts.length - 1) {
                            sb.append("-");
                        }
                    }

                    String queryUrl = "https://gimyplus.com/show/" + sb.toString();
                    String html = GimyParser.fetchHtml(queryUrl);
                    final ArrayList<Movie> parsedMovies = GimyParser.parseMoviesFromHtml(html);

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            gridPanelManager.populateGrid(parsedMovies);
                        }
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private void playMovie(final String playPath, final boolean resume) {
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
                String playHtml = GimyParser.fetchHtml("https://gimyplus.com" + playPath);
                final String m3u8Url = GimyParser.parseM3U8Url(playHtml);

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
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
        if (intent != null && detailPanelManager != null) {
            String movieId = intent.getStringExtra("movieId");
            if (movieId != null && !movieId.isEmpty()) {
                String title = intent.getStringExtra("movieTitle");
                String imageUrl = intent.getStringExtra("imageUrl");
                String subtitle = intent.getStringExtra("subtitle");
                
                // Load movie details asynchronously, and auto-focus play
                detailPanelManager.loadMovieDetails(movieId, title != null ? title : "", imageUrl != null ? imageUrl : "", "", subtitle != null ? subtitle : "", true);
            }
        }
    }
}
