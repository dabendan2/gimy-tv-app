package com.gimytv.horror;

import android.content.Context;
import android.content.SharedPreferences;

public class MovieStore {
    private static final String PREFS_NAME = "GimyHorror";
    private final SharedPreferences prefs;
    private final Context context;

    public MovieStore(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        exportStoreToExternal();
    }

    public int getListState(String movieId) {
        if (movieId == null || movieId.isEmpty()) return 0;
        return prefs.getInt("list_state_" + movieId, 0);
    }

    public void setListState(String movieId, int state) {
        if (movieId == null || movieId.isEmpty()) return;
        prefs.edit().putInt("list_state_" + movieId, state).apply();
        exportStoreToExternal();
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
            exportStoreToExternal();
        }
    }

    private void exportStoreToExternal() {
        if (context == null) return;
        try {
            java.io.File dir = context.getExternalFilesDir(null);
            if (dir == null) return;
            java.io.File file = new java.io.File(dir, "GimyHorror_Store.json");
            
            StringBuilder sb = new StringBuilder();
            sb.append("{\n");
            
            java.util.Map<String, ?> all = prefs.getAll();
            boolean first = true;
            for (java.util.Map.Entry<String, ?> entry : all.entrySet()) {
                String key = entry.getKey();
                Object val = entry.getValue();
                if (key.startsWith("progress_pos_") || key.startsWith("progress_dur_") || key.startsWith("list_state_")) {
                    if (!first) {
                        sb.append(",\n");
                    }
                    first = false;
                    sb.append("  \"").append(key).append("\": ").append(val);
                }
            }
            sb.append("\n}");
            
            java.io.FileWriter writer = new java.io.FileWriter(file);
            writer.write(sb.toString());
            writer.close();
            android.util.Log.i("GimyHorror_Store", "💾 Exported MovieStore JSON to: " + file.getAbsolutePath());
        } catch (Exception e) {
            android.util.Log.e("GimyHorror_Store", "Error exporting MovieStore to external", e);
        }
    }

    public SharedPreferences getPrefs() {
        return prefs;
    }
}
