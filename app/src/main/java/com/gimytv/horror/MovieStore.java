package com.gimytv.horror;

import android.content.Context;
import android.content.SharedPreferences;

public class MovieStore {
    private static final String PREFS_NAME = "GimyHorror";
    private final SharedPreferences prefs;
    private final Context context;
    private final java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newSingleThreadExecutor();

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

    public int getSelectedLine(String movieId) {
        if (movieId == null || movieId.isEmpty()) return 0;
        return prefs.getInt("selected_line_" + movieId, 0);
    }

    public void setSelectedLine(String movieId, int lineIndex) {
        if (movieId == null || movieId.isEmpty()) return;
        prefs.edit().putInt("selected_line_" + movieId, lineIndex).apply();
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
        savePlaybackProgress(movieId, position, duration, false);
    }

    public void savePlaybackProgress(String movieId, int position, int duration, boolean force) {
        if (movieId == null || movieId.isEmpty()) return;
        if (duration > 0) {
            int existingPos = getProgressPos(movieId);
            boolean allowed = TimeUtils.isProgressSaveAllowed(position, duration, existingPos, force);
            
            if (!allowed) {
                android.util.Log.i("GimyHorror_Store", "⚠️ Prevented overwriting high progress (" + existingPos + " ms) with progress (" + position + " ms) for Movie ID: " + movieId);
                return;
            }
            
            // Use commit() to guarantee disk synchronization before any process death or teardown
            prefs.edit()
                .putInt("progress_pos_" + movieId, position)
                .putInt("progress_dur_" + movieId, duration)
                .commit();
            exportStoreToExternal();
        }
    }

    public void clearPlaybackProgress(String movieId) {
        if (movieId == null || movieId.isEmpty()) return;
        // Use commit() to guarantee disk synchronization immediately
        prefs.edit()
            .remove("progress_pos_" + movieId)
            .remove("progress_dur_" + movieId)
            .commit();
        exportStoreToExternal();
    }

    private void exportStoreToExternal() {
        if (context == null) return;
        executor.execute(new Runnable() {
            @Override
            public void run() {
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
                        if (key.startsWith("progress_pos_") || key.startsWith("progress_dur_") || key.startsWith("list_state_") || key.startsWith("selected_line_") || key.startsWith("meta_")) {
                            if (!first) {
                                sb.append(",\n");
                            }
                            first = false;
                            sb.append("  \"").append(key).append("\": \"").append(val).append("\"");
                        }
                    }
                    sb.append("\n}");
                    
                    java.io.FileWriter writer = new java.io.FileWriter(file);
                    writer.write(sb.toString());
                    writer.close();
                    android.util.Log.i("GimyHorror_Store", "💾 Exported MovieStore JSON (Async) to: " + file.getAbsolutePath());
                } catch (Exception e) {
                    android.util.Log.e("GimyHorror_Store", "Error exporting MovieStore to external", e);
                }
            }
        });
    }

    public void saveMovieMetadata(String movieId, String title, String imageUrl, String note, String subtitle) {
        if (movieId == null || movieId.isEmpty()) return;
        prefs.edit()
            .putString("meta_title_" + movieId, title)
            .putString("meta_image_" + movieId, imageUrl)
            .putString("meta_note_" + movieId, note)
            .putString("meta_subtitle_" + movieId, subtitle)
            .apply();
        exportStoreToExternal();
    }

    public java.util.ArrayList<Movie> getWatchlistMovies() {
        java.util.ArrayList<Movie> list = new java.util.ArrayList<>();
        java.util.Map<String, ?> all = prefs.getAll();
        for (java.util.Map.Entry<String, ?> entry : all.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith("list_state_")) {
                Object val = entry.getValue();
                int state = 0;
                if (val instanceof Integer) {
                    state = (Integer) val;
                } else if (val instanceof String) {
                    try { state = Integer.parseInt((String) val); } catch (Exception e) {}
                }
                if (state == 1 || state == 2) { // 1 = Watch List, 2 = Liked/Favorite
                    String id = key.substring("list_state_".length());
                    String title = prefs.getString("meta_title_" + id, null);
                    String imageUrl, note, subtitle;
                    if (title == null || title.isEmpty() || title.startsWith("收藏影片 #") || title.contains("線上看") || title.contains("劇迷")) {
                        // On-the-fly self-healing for missing/placeholder/corrupt metadata
                        android.util.Log.i("GimyHorror_Store", "Healing metadata for movie ID: " + id);
                        String html = GimyParser.fetchHtml("https://gimyplus.com/vod/" + id + ".html");
                        if (html != null && !html.isEmpty()) {
                            Movie healed = GimyParser.parseMovieFromDetailPage(id, html);
                            title = healed.title;
                            imageUrl = healed.imageUrl;
                            note = healed.note;
                            subtitle = healed.subtitle;
                            // Persist so it's cached next time
                            saveMovieMetadata(id, title, imageUrl, note, subtitle);
                        } else {
                            title = "收藏影片 #" + id;
                            imageUrl = "";
                            note = "已收藏";
                            subtitle = "";
                        }
                    } else {
                        imageUrl = prefs.getString("meta_image_" + id, "");
                        note = prefs.getString("meta_note_" + id, "已加入");
                        subtitle = prefs.getString("meta_subtitle_" + id, "");
                    }
                    list.add(new Movie(id, title, imageUrl, note, subtitle));
                }
            }
        }
        return list;
    }

    public SharedPreferences getPrefs() {
        return prefs;
    }
}
