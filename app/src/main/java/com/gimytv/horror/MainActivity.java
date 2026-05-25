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
    private String selectedMovieImageUrl = "";
    private String selectedMovieSubtitle = "";
    private GimyMediaSession gimyMediaSession = null;
    private View lastFocusedCard = null;

    // Encapsulated Video Player Component
    private GimyPlayer gimyPlayer;



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

        // Initialize GimyPlayer Component
        gimyPlayer = new GimyPlayer(this, rootContainer, movieStore, new GimyPlayer.PlayerListener() {
            @Override
            public void onPlaybackStopped() {
                mainSplitLayout.setVisibility(View.VISIBLE);
                if (btnPlayRef != null && btnPlayRef.getTag() != null) {
                    updatePlayButtons((String) btnPlayRef.getTag());
                }
                localRefreshGrid();
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

                    String queryUrl = "https://gimyplus.com/genre/horror.html?class=" + URLEncoder.encode(regionParam, "UTF-8") + "&year=" + yearParam + "&by=" + sortParam;
                    String html = GimyParser.fetchHtml(queryUrl);
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

                // Status tag - Displays previous playback progress dynamically
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
        loadMovieDetails(id, title, imageUrl, note, subtitle, false);
    }

    private void loadMovieDetails(final String id, final String title, final String imageUrl, final String note, final String subtitle, final boolean focusPlay) {
        selectedMovieId = id;
        selectedMovieTitle = title;
        selectedMovieImageUrl = imageUrl;
        selectedMovieSubtitle = subtitle;
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
                            if (focusPlay && btnPlayRef != null && btnPlayRef.isEnabled()) {
                                btnPlayRef.requestFocus();
                            }
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

    private void updatePlayButtons(final String playPath) {
        updatePlayButtons(playPath, false);
    }

    private void updatePlayButtons(final String playPath, final boolean focusListButton) {
        playButtonLayout.removeAllViews();
        int size = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 48, getResources().getDisplayMetrics());

        if (selectedMovieId == null || selectedMovieId.isEmpty()) {
            return;
        }

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

            final Button btnPlayNew = new Button(this);
            btnPlayNew.setText("▶");
            btnPlayNew.setTag(playPath);
            btnPlayNew.setTextSize(22);
            btnPlayNew.setTextColor(Color.WHITE);
            btnPlayNew.setFocusable(true);
            btnPlayNew.setBackgroundColor(Color.parseColor("#137333")); // Dark green
            btnPlayNew.setPadding(0, 0, 0, 0);
            
            LinearLayout.LayoutParams lpPlay = new LinearLayout.LayoutParams(size, size);
            lpPlay.setMargins(0, 0, 20, 0);
            btnPlayNew.setLayoutParams(lpPlay);

            btnPlayNew.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean hasFocus) {
                    if (hasFocus) {
                        v.setBackgroundColor(Color.parseColor("#34A853"));
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
                    playMovie(playPath, hasProgress);
                }
            });

            playButtonLayout.addView(btnPlayNew);
            this.btnPlayRef = btnPlayNew;
            this.btnPlay = btnPlayNew;

            if (hasProgress) {
                Button btnRestart = new Button(this);
                btnRestart.setText("↺");
                btnRestart.setTextSize(22);
                btnRestart.setTextColor(Color.WHITE);
                btnRestart.setFocusable(true);
                btnRestart.setBackgroundColor(Color.parseColor("#3C4043"));
                btnRestart.setPadding(0, 0, 0, 0);
                
                LinearLayout.LayoutParams lpRestart = new LinearLayout.LayoutParams(size, size);
                lpRestart.setMargins(0, 0, 20, 0);
                btnRestart.setLayoutParams(lpRestart);

                btnRestart.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                    @Override
                    public void onFocusChange(View v, boolean hasFocus) {
                        if (hasFocus) {
                            v.setBackgroundColor(Color.parseColor("#EA4335"));
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
                        playMovie(playPath, false);
                    }
                });

                playButtonLayout.addView(btnRestart);
            }
        }

        // Playlist State Button (+) / (📝) / (❤️) / (💩)
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
        btnListState.setBackgroundColor(Color.parseColor("#3C4043"));
        btnListState.setPadding(0, 0, 0, 0);
        
        LinearLayout.LayoutParams lpList = new LinearLayout.LayoutParams(size, size);
        lpList.setMargins(0, 0, 20, 0);
        btnListState.setLayoutParams(lpList);

        btnListState.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    v.setBackgroundColor(Color.parseColor("#1A73E8")); // Google Blue focus for list state
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
                int curr = getListState(selectedMovieId);
                final int next = (curr + 1) % 4;
                setListState(selectedMovieId, next);

                // Instantly update current playlist state text
                String updatedText = "+";
                if (next == 1) updatedText = "📝";
                else if (next == 2) updatedText = "❤️";
                else if (next == 3) updatedText = "💩";
                btnListState.setText(updatedText);

                // Synchronously update left grid card title in real time!
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

                // If focus preservation requested, re-trigger update buttons and lock focus
                updatePlayButtons(playPath, true);
            }
        });

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
        gimyPlayer.startPlayer(m3u8Url, resume, selectedMovieId, selectedMovieTitle, selectedMovieImageUrl, selectedMovieSubtitle);
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
            String movieId = intent.getStringExtra("movieId");
            if (movieId != null && !movieId.isEmpty()) {
                String title = intent.getStringExtra("movieTitle");
                String imageUrl = intent.getStringExtra("imageUrl");
                String subtitle = intent.getStringExtra("subtitle");
                
                // Load movie details asynchronously, and auto-focus play
                loadMovieDetails(movieId, title != null ? title : "", imageUrl != null ? imageUrl : "", "", subtitle != null ? subtitle : "", true);
            }
        }
    }
}
