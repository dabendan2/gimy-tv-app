package com.gimytv.horror;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.KeyEvent;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class DetailPanelManager {

    public interface DetailPanelListener {
        void onPlayMovieRequested(String playPath, boolean resume);
        void onListStateChanged(String movieId, int nextState);
    }

    private final Activity activity;
    private final ScrollView rightScrollView;
    private final LinearLayout playButtonLayout;
    private final TextView tvDetailTitle;
    private final TextView tvDetailMeta;
    private final TextView tvDetailSynopsis;
    private final MovieStore movieStore;
    private final DetailPanelListener listener;

    private String selectedMovieId = "";
    private String selectedMovieTitle = "";
    private String selectedMovieImageUrl = "";
    private String selectedMovieSubtitle = "";

    private Button btnPlayRef = null;

    public DetailPanelManager(Activity activity, ScrollView rightScrollView, LinearLayout playButtonLayout,
                              TextView tvDetailTitle, TextView tvDetailMeta, TextView tvDetailSynopsis,
                              MovieStore movieStore, DetailPanelListener listener) {
        this.activity = activity;
        this.rightScrollView = rightScrollView;
        this.playButtonLayout = playButtonLayout;
        this.tvDetailTitle = tvDetailTitle;
        this.tvDetailMeta = tvDetailMeta;
        this.tvDetailSynopsis = tvDetailSynopsis;
        this.movieStore = movieStore;
        this.listener = listener;

        // Initialize empty state
        updatePlayButtons("");
    }

    public String getSelectedMovieId() {
        return selectedMovieId;
    }

    public String getSelectedMovieTitle() {
        return selectedMovieTitle;
    }

    public String getSelectedMovieImageUrl() {
        return selectedMovieImageUrl;
    }

    public String getSelectedMovieSubtitle() {
        return selectedMovieSubtitle;
    }

    public void loadMovieDetails(final String id, final String title, final String imageUrl, final String note, final String subtitle) {
        loadMovieDetails(id, title, imageUrl, note, subtitle, false);
    }

    public void loadMovieDetails(final String id, final String title, final String imageUrl, final String note, final String subtitle, final boolean focusPlay) {
        this.selectedMovieId = id;
        this.selectedMovieTitle = title;
        this.selectedMovieImageUrl = imageUrl;
        this.selectedMovieSubtitle = subtitle;

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

                activity.runOnUiThread(new Runnable() {
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

    public void updatePlayButtons(final String playPath) {
        updatePlayButtons(playPath, false);
    }

    public void updatePlayButtons(final String playPath, final boolean focusListButton) {
        playButtonLayout.removeAllViews();
        int size = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 48, activity.getResources().getDisplayMetrics());

        if (selectedMovieId == null || selectedMovieId.isEmpty()) {
            return;
        }

        if (playPath == null || playPath.isEmpty()) {
            Button btnDisabled = new Button(activity);
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

            final Button btnPlayNew = new Button(activity);
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
                        Log.i("GimyHorror_UI", "🎯 FocusState: Play Button (New) focused");
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
                    if (listener != null) {
                        listener.onPlayMovieRequested(playPath, hasProgress);
                    }
                }
            });

            btnPlayNew.setOnKeyListener(new View.OnKeyListener() {
                @Override
                public boolean onKey(View v, int keyCode, KeyEvent event) {
                    if (event.getAction() == KeyEvent.ACTION_DOWN) {
                        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                            if (rightScrollView != null) {
                                rightScrollView.requestFocus();
                                rightScrollView.smoothScrollBy(0, -100);
                                return true;
                            }
                        } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                            return true; // Lock at the bottom
                        }
                    }
                    return false;
                }
            });

            playButtonLayout.addView(btnPlayNew);
            this.btnPlayRef = btnPlayNew;

            if (hasProgress) {
                Button btnRestart = new Button(activity);
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
                            Log.i("GimyHorror_UI", "🎯 FocusState: Restart Button focused");
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
                        if (listener != null) {
                            listener.onPlayMovieRequested(playPath, false);
                        }
                    }
                });

                btnRestart.setOnKeyListener(new View.OnKeyListener() {
                    @Override
                    public boolean onKey(View v, int keyCode, KeyEvent event) {
                        if (event.getAction() == KeyEvent.ACTION_DOWN) {
                            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                                if (rightScrollView != null) {
                                    rightScrollView.requestFocus();
                                    rightScrollView.smoothScrollBy(0, -100);
                                    return true;
                                }
                            } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                                return true; // Lock at the bottom
                            }
                        }
                        return false;
                    }
                });

                playButtonLayout.addView(btnRestart);
            }
        }

        // Playlist State Button (+) / (📝) / (❤️) / (💩)
        final Button btnListState = new Button(activity);
        int listState = movieStore.getListState(selectedMovieId);
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
                    Log.i("GimyHorror_UI", "🎯 FocusState: Playlist Button focused");
                    v.setBackgroundColor(Color.parseColor("#1A73E8")); // Blue focus
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
                int curr = movieStore.getListState(selectedMovieId);
                final int next = (curr + 1) % 4;
                movieStore.setListState(selectedMovieId, next);

                // Update text instantly
                String updatedText = "+";
                if (next == 1) updatedText = "📝";
                else if (next == 2) updatedText = "❤️";
                else if (next == 3) updatedText = "💩";
                btnListState.setText(updatedText);

                if (listener != null) {
                    listener.onListStateChanged(selectedMovieId, next);
                }

                // Focus preservation
                updatePlayButtons(playPath, true);
            }
        });

        btnListState.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                        if (rightScrollView != null) {
                            rightScrollView.requestFocus();
                            rightScrollView.smoothScrollBy(0, -100);
                            return true;
                        }
                    } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                        return true; // Lock at the bottom
                    }
                }
                return false;
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

    public Button getPlayButton() {
        return btnPlayRef;
    }
}
