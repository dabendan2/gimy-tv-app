package com.gimytv.horror;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class GimyReceiver extends BroadcastReceiver {
    private static final String TAG = "GimyReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        Log.i(TAG, "📥 GimyReceiver: Broadcast received with action: " + action);
        
        if ("com.gimytv.horror.UPDATE_LIST".equals(action)) {
            if (intent.hasExtra("movieId") && intent.hasExtra("listState")) {
                String movieId = intent.getStringExtra("movieId");
                int state = -1;
                
                // Handle both string and int values for flexibility over ADB
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
                
                Log.i(TAG, "Updating list state in background for ID: " + movieId + " to: " + state);
                if (movieId != null && !movieId.isEmpty() && state != -1) {
                    MovieStore store = new MovieStore(context);
                    store.setListState(movieId, state);
                    
                    // Heal metadata proactively if passed via broadcast extras
                    if (intent.hasExtra("title")) {
                        String title = intent.getStringExtra("title");
                        String imageUrl = intent.getStringExtra("imageUrl");
                        String subtitle = intent.getStringExtra("subtitle");
                        store.saveMovieMetadata(movieId, title, imageUrl, "已收藏", subtitle);
                    }
                }
            }
        } else if ("com.gimytv.horror.UPDATE_SEARCH".equals(action)) {
            if (intent.hasExtra("searchQuery")) {
                String query = intent.getStringExtra("searchQuery");
                Log.i(TAG, "Updating search query in background to: " + query);
                context.getSharedPreferences("GimyHorror", Context.MODE_PRIVATE)
                    .edit()
                    .putString("search_query", query)
                    .apply();
            }
        }
    }
}
