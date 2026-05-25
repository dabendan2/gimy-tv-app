package com.gimytv.horror;

import android.content.Context;
import android.content.SharedPreferences;

public class MovieStore {
    private static final String PREFS_NAME = "GimyHorror";
    private final SharedPreferences prefs;

    public MovieStore(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public int getListState(String movieId) {
        if (movieId == null || movieId.isEmpty()) return 0;
        return prefs.getInt("list_state_" + movieId, 0);
    }

    public void setListState(String movieId, int state) {
        if (movieId == null || movieId.isEmpty()) return;
        prefs.edit().putInt("list_state_" + movieId, state).apply();
    }

    public int getProgressPos(String movieId) {
        if (movieId == null || movieId.isEmpty()) return 0;
        return prefs.getInt("progress_pos_" + movieId, 0);
    }

    public int getProgressDur(String movieId) {
        if (movieId == null || movieId.isEmpty()) return 0;
        return prefs.getInt("progress_dur_" + movieId, 0);
    }

    public void savePlaybackProgress(String movieId, int position, int duration) {
        if (movieId == null || movieId.isEmpty()) return;
        if (duration > 0) {
            prefs.edit()
                .putInt("progress_pos_" + movieId, position)
                .putInt("progress_dur_" + movieId, duration)
                .apply();
        }
    }

    public SharedPreferences getPrefs() {
        return prefs;
    }
}
