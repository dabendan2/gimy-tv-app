package com.gimytv.horror;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.HashMap;

public class GridPanelManager {

    public interface GridPanelListener {
        void onMovieCardFocused(Movie movie, View card);
        void onMovieCardClicked(Movie movie, View card);
    }

    private final Context context;
    private final LinearLayout gridContainer;
    private final MovieStore movieStore;
    private final GridPanelListener listener;
    
    private ArrayList<Movie> currentMoviesList = new ArrayList<>();
    private View lastFocusedCard = null;

    public GridPanelManager(Context context, LinearLayout gridContainer, MovieStore movieStore, GridPanelListener listener) {
        this.context = context;
        this.gridContainer = gridContainer;
        this.movieStore = movieStore;
        this.listener = listener;
    }

    public void populateGrid(final ArrayList<Movie> movies) {
        gridContainer.removeAllViews();
        if (movies == null || movies.isEmpty()) {
            this.currentMoviesList = movies != null ? movies : new ArrayList<Movie>();
            TextView empty = new TextView(context);
            empty.setText("在陰間迷路了，未找到相符的鬼片！\n請更換篩選條件看看。");
            empty.setTextSize(16);
            empty.setTextColor(Color.parseColor("#9AA0A6"));
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, 120, 0, 0);
            gridContainer.addView(empty);
            return;
        }

        // Re-arrange list: pull watchlist (list_state == 1) to the very front
        HashMap<String, Integer> statesMap = new HashMap<>();
        for (Movie m : movies) {
            statesMap.put(m.id, movieStore.getListState(m.id));
        }
        final ArrayList<Movie> sortedMovies = MovieSorter.sortMovies(movies, statesMap);
        this.currentMoviesList = sortedMovies; // Cache sorted list for rapid local refresh

        int rowCount = (int) Math.ceil(sortedMovies.size() / 3f);
        for (int r = 0; r < rowCount; r++) {
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            row.setPadding(0, 10, 0, 10);

            for (int c = 0; c < 3; c++) {
                final int idx = r * 3 + c;
                if (idx >= sortedMovies.size()) {
                    View spacer = new View(context);
                    LinearLayout.LayoutParams spacerParams = new LinearLayout.LayoutParams(0, 1, 1f);
                    spacerParams.setMargins(10, 0, 10, 0);
                    spacer.setLayoutParams(spacerParams);
                    row.addView(spacer);
                    continue;
                }

                final Movie m = sortedMovies.get(idx);

                // Movie Card Container
                final LinearLayout card = new LinearLayout(context);
                card.setOrientation(LinearLayout.VERTICAL);
                LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                cardParams.setMargins(10, 0, 10, 0);
                card.setLayoutParams(cardParams);
                card.setPadding(15, 15, 15, 15);
                card.setBackgroundColor(Color.parseColor("#1C1D1F"));
                card.setFocusable(true);
                card.setClickable(true);

                // Image Poster
                ImageView ivPoster = new ImageView(context);
                LinearLayout.LayoutParams imgParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 200);
                ivPoster.setLayoutParams(imgParams);
                ivPoster.setScaleType(ImageView.ScaleType.CENTER_CROP);
                ivPoster.setBackgroundColor(Color.BLACK);
                ImageLoader.loadImage(m.imageUrl, ivPoster);
                card.addView(ivPoster);

                // Title
                final TextView tvTitle = new TextView(context);
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
                TextView tvNote = new TextView(context);
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

                            if (listener != null) {
                                listener.onMovieCardFocused(m, card);
                            }
                        } else {
                            card.setBackgroundColor(Color.parseColor("#1C1D1F"));
                            card.setScaleX(1.0f);
                            card.setScaleY(1.0f);
                            tvTitle.setTextColor(Color.WHITE);
                        }
                    }
                });
                
                // Click card
                card.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (listener != null) {
                            listener.onMovieCardClicked(m, card);
                        }
                    }
                });

                row.addView(card);
            }
            gridContainer.addView(row);
        }

        android.view.View currentFocus = null;
        if (context instanceof android.app.Activity) {
            currentFocus = ((android.app.Activity) context).getCurrentFocus();
        }
        
        boolean isFilterFocused = false;
        if (currentFocus != null) {
            android.view.ViewParent parent = currentFocus.getParent();
            if (parent != null && parent.getParent() instanceof android.widget.HorizontalScrollView) {
                isFilterFocused = true;
            }
        }

        if (!isFilterFocused) {
            if (gridContainer.getChildCount() > 0) {
                LinearLayout firstRow = (LinearLayout) gridContainer.getChildAt(0);
                if (firstRow.getChildCount() > 0) {
                    firstRow.getChildAt(0).requestFocus();
                }
            }
        }
    }

    public void localRefreshGrid() {
        populateGrid(currentMoviesList);
    }

    public ArrayList<Movie> getCurrentMoviesList() {
        return currentMoviesList;
    }

    public void setCurrentMoviesList(ArrayList<Movie> currentMoviesList) {
        this.currentMoviesList = currentMoviesList;
    }

    public View getLastFocusedCard() {
        return lastFocusedCard;
    }

    public void setLastFocusedCard(View lastFocusedCard) {
        this.lastFocusedCard = lastFocusedCard;
    }
}
