package com.gimytv.horror;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;

public class TvWatchNextHelper {

    /**
     * Updates or inserts a movie playback progress in Android TV's global "Watch Next / 繼續觀賞" row.
     * Uses the native ContentResolver TvContract API (fully offline & lightweight).
     */
    public static void updateWatchNext(Context context, String movieId, String title, String imageUrl, String subtitle, int position, int duration) {
        if (Build.VERSION.SDK_INT < 26) {
            return; // Watch Next is supported on Android 8.0 (API 26) and above
        }

        try {
            ContentResolver resolver = context.getContentResolver();
            Uri contentUri = Uri.parse("content://android.media.tv/watch_next_program");

            // 1. Delete any existing entries for this movie to prevent duplicates
            resolver.delete(contentUri, "content_id=?", new String[]{movieId});

            // 2. If the user finished the movie (>95%) or barely started (<3%), don't show it in Watch Next
            if (duration > 0 && position > 0) {
                double pct = (double) position / duration;
                if (pct > 0.95 || pct < 0.03) {
                    return; // completed or barely started, cleanup and exit
                }
            } else {
                return; // invalid durations
            }

            // 3. Create the deep link intent to launch Gimy TV and restore this movie's playback details
            Intent playIntent = new Intent(context, MainActivity.class);
            playIntent.setAction(Intent.ACTION_VIEW);
            playIntent.putExtra("movieId", movieId);
            playIntent.putExtra("movieTitle", title);
            playIntent.putExtra("imageUrl", imageUrl);
            playIntent.putExtra("subtitle", subtitle);
            playIntent.putExtra("autoPlay", true); // Automatically start player if preferred!
            
            // Convert intent to deep-link string format (URI_INTENT_SCHEME)
            String intentUriString = playIntent.toUri(Intent.URI_INTENT_SCHEME);

            // 4. Construct content values for TvContract.WatchNextPrograms
            ContentValues values = new ContentValues();
            values.put("type", 0); // TvContract.WatchNextPrograms.TYPE_MOVIE (0)
            values.put("watch_next_type", 0); // TvContract.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE (0)
            values.put("last_engagement_time_utc_millis", System.currentTimeMillis());
            values.put("title", title);
            values.put("short_description", subtitle.isEmpty() ? "Gimy 鬼魅劇場" : subtitle);
            values.put("poster_art_uri", imageUrl);
            values.put("intent_uri", intentUriString);
            values.put("content_id", movieId);
            values.put("progress_max", duration);
            values.put("progress_value", position);

            // 5. Insert into TvContract
            resolver.insert(contentUri, values);
            System.out.println("TvWatchNextHelper: Successfully updated Watch Next row for " + title);
        } catch (Exception e) {
            System.err.println("TvWatchNextHelper: Error updating Watch Next: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Removes a movie from the Watch Next row (e.g. when completed or deleted).
     */
    public static void removeWatchNext(Context context, String movieId) {
        if (Build.VERSION.SDK_INT < 26) return;
        try {
            ContentResolver resolver = context.getContentResolver();
            Uri contentUri = Uri.parse("content://android.media.tv/watch_next_program");
            resolver.delete(contentUri, "content_id=?", new String[]{movieId});
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
