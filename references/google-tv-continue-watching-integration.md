# Google TV & Android TV "Continue Watching" (Watch Next) Integration Spec 📺🔄

This reference provides the technical specification, critical pitfalls, and code patterns for integrating the Gimy TV App with the Google TV **Continue Watching (Watch Next / Play Next)** row.

Because Gimy TV App is a **Pure Java, ultra-lightweight APK built via custom CLI scripts**, this specification covers both:
1. **Dynamic ContentProvider Calls (No External Dependencies)**: Recommended to keep compilation lightning fast and APK size minimal.
2. **Direct Framework API 26+ Calls**: Since the compile target is API 26+, standard framework classes can be directly imported.

---

## 1. Core Mechanics & Architecture
Under the hood, Google TV's home screen queries a system-level SQLite database managed by the **TV Provider**. The specific table for this row is represented by the authority `android.media.tv` and the table path `watch_next_program`.

Any app with correct EPG permissions can insert, update, and delete rows in this database. Android automatically sandboxes these operations so that your app can only read/write its own entries (filtering by your package name).

### Required Manifest Permissions
Add the following declarations to `AndroidManifest.xml` to interact with the TV ContentProvider:
```xml
<!-- Required to query and read existing "Continue Watching" entries to avoid duplicates -->
<uses-permission android:name="com.android.providers.tv.permission.READ_EPG_DATA" />
<!-- Required to insert, update, or remove entries -->
<uses-permission android:name="com.android.providers.tv.permission.WRITE_EPG_DATA" />
```

---

## 2. Dynamic Contract Definition (`WatchNextContract.java`)
To avoid bringing in heavy AndroidX libraries, use this self-contained, compile-time-independent contract class:

```java
package com.gimytv.horror;

import android.net.Uri;

public final class WatchNextContract {
    private WatchNextContract() {}

    public static final String AUTHORITY = "android.media.tv";
    public static final String PATH_WATCH_NEXT_PROGRAM = "watch_next_program";
    public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/" + PATH_WATCH_NEXT_PROGRAM);

    // Columns
    public static final String COLUMN_ID = "_id";
    public static final String COLUMN_TYPE = "type"; // Movie = 0
    public static final String COLUMN_WATCH_NEXT_TYPE = "watch_next_type"; // Continue = 0
    public static final String COLUMN_INTERNAL_PROVIDER_ID = "internal_provider_id"; // App's unique movie key
    
    public static final String COLUMN_TITLE = "title";
    public static final String COLUMN_SHORT_DESCRIPTION = "short_description";
    public static final String COLUMN_POSTER_ART_URI = "poster_art_uri"; // Fully qualified HTTPS URL
    public static final String COLUMN_POSTER_ART_ASPECT_RATIO = "poster_art_aspect_ratio"; // 16:9 = 0, Movie Poster (2:3) = 4
    
    public static final String COLUMN_INTENT_URI = "intent_uri"; // Deep Link URI
    public static final String COLUMN_LAST_PLAYBACK_POSITION_MILLIS = "last_playback_position_millis";
    public static final String COLUMN_DURATION_MILLIS = "duration_millis";
    public static final String COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS = "last_engagement_time_utc_millis";

    // Constants
    public static final int TYPE_MOVIE = 0;
    public static final int WATCH_NEXT_TYPE_CONTINUE = 0;
    public static final int ASPECT_RATIO_16_9 = 0;
    public static final int ASPECT_RATIO_MOVIE_POSTER = 4;
}
```

---

## 3. Playback Synchronization Patterns (`WatchNextManager.java`)

Writing to the TV Provider is an I/O-bound operation. **Always run these queries and updates on a background thread** (using an `ExecutorService` or basic `Thread`) to prevent Application Not Responding (ANR) flags on Google TV.

### ⚠️ Android 10+ (API 29+) Security Restriction & In-Memory Filter Workaround
On newer Android versions, the OS's `TvProvider` forbids non-system apps from performing general queries with custom selections (`selection = "internal_provider_id = ?"`) on the `watch_next_program` table, throwing:
`java.lang.SecurityException: Selection not allowed for content://android.media.tv/watch_next_program`.

**The Workaround**: Invoke the query with `selection = null` and `selectionArgs = null`. The OS will automatically restrict the returned cursor to the app's own package rows (sandboxed). Then, iterate through the cursor in-memory to find the row with the matching `internal_provider_id`:

```java
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

public static Long getWatchNextProgramId(Context context, String internalVideoId) {
    ContentResolver resolver = context.getContentResolver();
    String[] projection = { 
        WatchNextContract.COLUMN_ID, 
        WatchNextContract.COLUMN_INTERNAL_PROVIDER_ID 
    };

    // Use null selection to avoid SecurityException on Android 10+ (OS filters by package automatically)
    try (Cursor cursor = resolver.query(WatchNextContract.CONTENT_URI, projection, null, null, null)) {
        if (cursor != null) {
            int idIdx = cursor.getColumnIndex(WatchNextContract.COLUMN_ID);
            int providerIdIdx = cursor.getColumnIndex(WatchNextContract.COLUMN_INTERNAL_PROVIDER_ID);
            
            while (cursor.moveToNext()) {
                String providerId = cursor.getString(providerIdIdx);
                if (internalVideoId.equals(providerId)) {
                    return cursor.getLong(idIdx);
                }
            }
        }
    } catch (Exception e) {
        Log.e("GimyHorror_Store", "Error querying Watch Next", e);
    }
    return null;
}
```

### B. Performing Upsert (Insert or Update)
Coordinate the insert/update workflow on a background executor. Note that setting `COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS` to the current system timestamp is what pushes your card to the far left (most recent) of the Google TV home row.

```java
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;

public static void upsertWatchNext(Context context, String internalId, String title, String description, 
                                   String posterUrl, String deepLinkUri, long currentPositionMs, long durationMs) {
    // Throttled execution on background thread
    new Thread(() -> {
        ContentResolver resolver = context.getContentResolver();
        Long existingId = getWatchNextProgramId(context, internalId);

        ContentValues values = new ContentValues();
        values.put(WatchNextContract.COLUMN_LAST_PLAYBACK_POSITION_MILLIS, currentPositionMs);
        values.put(WatchNextContract.COLUMN_DURATION_MILLIS, durationMs);
        values.put(WatchNextContract.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS, System.currentTimeMillis());

        if (existingId != null) {
            // Update existing entry
            Uri programUri = Uri.withAppendedPath(WatchNextContract.CONTENT_URI, String.valueOf(existingId));
            resolver.update(programUri, values, null, null);
        } else {
            // Build new entry
            values.put(WatchNextContract.COLUMN_TYPE, WatchNextContract.TYPE_MOVIE);
            values.put(WatchNextContract.COLUMN_WATCH_NEXT_TYPE, WatchNextContract.WATCH_NEXT_TYPE_CONTINUE);
            values.put(WatchNextContract.COLUMN_INTERNAL_PROVIDER_ID, internalId);
            values.put(WatchNextContract.COLUMN_TITLE, title);
            values.put(WatchNextContract.COLUMN_SHORT_DESCRIPTION, description);
            values.put(WatchNextContract.COLUMN_POSTER_ART_URI, posterUrl);
            values.put(WatchNextContract.COLUMN_POSTER_ART_ASPECT_RATIO, WatchNextContract.ASPECT_RATIO_16_9);
            values.put(WatchNextContract.COLUMN_INTENT_URI, deepLinkUri);

            resolver.insert(WatchNextContract.CONTENT_URI, values);
        }
    }).start();
}
```

### C. Deleting Completed Content
Once the user finishes watching the video (e.g., progress > 95%), remove the entry entirely to prevent the home screen from getting cluttered:

```java
public static void removeWatchNext(Context context, String internalId) {
    new Thread(() -> {
        Long existingId = getWatchNextProgramId(context, internalId);
        if (existingId != null) {
            Uri programUri = Uri.withAppendedPath(WatchNextContract.CONTENT_URI, String.valueOf(existingId));
            context.getContentResolver().delete(programUri, null, null);
        }
    }).start();
}
```

---

## 4. Key Performance Guidelines & Best Practices

1. **Throttled Syncing**: Updating the system database on every second of video playback will lag the video player and slow down the TV. Trigger the database updates **only when**:
   - The user pauses the playback.
   - The user exits the player (in `onStop()` / `onDestroy()`).
   - During continuous playback, limit writes using a throttle/timer to **once every 30 to 60 seconds**.
2. **Deep Link Matching**: Your `COLUMN_INTENT_URI` must be matched by an `<intent-filter>` in your `AndroidManifest.xml` (e.g., custom scheme `gimy://play?id=VIDEO_ID&position=MILLIS`). In `MainActivity.java`, capture and parse this URI inside `onCreate` / `onNewIntent` to seamlessly launch GimyPlayer at the exact saved millisecond.
3. **Poster Image Resolution & HTTPS**: Google TV's shell downloader rejects raw local byte arrays (via Binder) and may fail on relative URLs. Always provide a fully-qualified `https://` URL (e.g., prefix `/upload/` with `https://gimyplus.com`) and target a standard 16:9 aspect ratio thumbnail.

---

## 5. TV-Specific Integration Pitfalls

### ⚠️ A. The TV Launcher Autofocus Race Condition (Focus Theft)
When a TV app launches via a deep link, it immediately starts rendering the targeted detail page asynchronously on the right. Concurrently, the app fetches and populates the left content grid. Once grid loading completes, the TV focus-engine automatically requests focus on the first item of the grid. This triggers the card-focus listener, overwriting the detail panel with the first grid movie and erasing the deep link.

**The Fix (The Transient Flag Pattern)**:
Declare a transient boolean flag (e.g., `isDeepLinkActive = false;`) in your `MainActivity`.
- When a deep link is received, set `isDeepLinkActive = true;`.
- In your grid card `OnFocusChangeListener`, check this flag:
  ```java
  @Override
  public void onMovieCardFocused(Movie movie, View card) {
      if (isDeepLinkActive) {
          Log.i(TAG, "⚠️ Deep Link active, skipping initial automatic focus loading of " + movie.title);
          isDeepLinkActive = false; // Reset so manual focus navigation is enabled
          return;
      }
      detailPanelManager.loadMovieDetails(movie.id, movie.title, ...);
  }
  ```

### ⚠️ B. Google TV Home Launcher Partner Whitelist (Sideload Limitation)
On modern Google TV home launchers (such as Chromecast with Google TV), Google strictly filters the "Continue Watching" row. Sideloaded / third-party APKs not hosted on Google Play (or linked under Google Account Settings -> "Your Services") will have their cards **hidden** from the main home launcher, despite successfully writing to the system TV database.

**Verification**: To verify that your `watch_next_program` rows are being written successfully, use custom launchers that bypass Google TV launcher restrictions (e.g., **Projectivy Launcher** or **FLauncher**). If rows are written properly, they will display instantly in these custom launchers' "Continue Watching" rows.
