package com.gimytv.horror;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;

public class TvWatchNextHelper {

    /**
     * Updates or inserts a movie playback progress in Android TV's global "Watch Next / 繼續觀賞" row.
     * Uses individual ContentUri deletion to bypass Android TV sandbox 'Selection not allowed' SecurityException.
     */
    public static void updateWatchNext(Context context, MovieStore movieStore, String movieId, String title, String imageUrl, String subtitle, int position, int duration) {
        if (Build.VERSION.SDK_INT < 26) {
            return; // Watch Next is supported on Android 8.0 (API 26) and above
        }

        try {
            ContentResolver resolver = context.getContentResolver();
            Uri contentUri = Uri.parse("content://android.media.tv/watch_next_program");

            // 1. Delete any existing entry using its system-assigned unique ID (bypasses Sandboxed selection block!)
            long savedId = movieStore.getPrefs().getLong("watch_next_id_" + movieId, -1);
            if (savedId != -1) {
                try {
                    Uri individualUri = ContentUris.withAppendedId(contentUri, savedId);
                    resolver.delete(individualUri, null, null);
                } catch (Exception e) {
                    // Fail-silent if the program was already manually removed/swiped by the user
                }
                movieStore.getPrefs().edit().remove("watch_next_id_" + movieId).apply();
            }

            // 2. If the user finished the movie (>95%) or barely started (<3%), clean up from Watch Next and return
            if (duration > 0 && position > 0) {
                double pct = (double) position / duration;
                if (pct > 0.95 || pct < 0.03) {
                    System.out.println("TvWatchNextHelper: Movie " + title + " progress out of Watch Next bounds (" + (int)(pct*100) + "%), cleared.");
                    return; // exit after deleting the old one
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
            playIntent.putExtra("autoPlay", true);
            
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

            // 5. Insert new program and store its system-assigned ID for future updates
            Uri newUri = resolver.insert(contentUri, values);
            if (newUri != null) {
                long newId = ContentUris.parseId(newUri);
                movieStore.getPrefs().edit().putLong("watch_next_id_" + movieId, newId).apply();
                System.out.println("TvWatchNextHelper: Successfully added Watch Next program for " + title + " with ID " + newId);
            }
        } catch (Exception e) {
            System.err.println("TvWatchNextHelper: Error updating Watch Next: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Removes a movie from the Watch Next row.
     */
    public static void removeWatchNext(Context context, MovieStore movieStore, String movieId) {
        if (Build.VERSION.SDK_INT < 26) return;
        try {
            long savedId = movieStore.getPrefs().getLong("watch_next_id_" + movieId, -1);
            if (savedId != -1) {
                ContentResolver resolver = context.getContentResolver();
                Uri contentUri = Uri.parse("content://android.media.tv/watch_next_program");
                Uri individualUri = ContentUris.withAppendedId(contentUri, savedId);
                resolver.delete(individualUri, null, null);
                movieStore.getPrefs().edit().remove("watch_next_id_" + movieId).apply();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
