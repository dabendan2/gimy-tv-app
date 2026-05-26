# TV D-Pad Focus Lock & Navigation Design Patterns 📺🕹️

This document outlines the class-level Java patterns for robust physical D-Pad remote control navigation on Android TV / Leanback platforms, developed during real-world box deployments.

---

## 1. Strict Vertical D-Pad Panel Confinement (Focus Leak Prevention)

### The Problem
On Android TV, when the focus is on a layout element near the screen boundary (e.g., buttons at the bottom of a sidebar) and the user presses **UP** or **DOWN**, the default Android `FocusFinder` searches globally across the screen. 
Since a neighboring panel (like a left-side movie grid) has elements extending the full height of the screen, the focus frequently "leaks" horizontally to the other panel, frustrating users.

### The Solution
Intercept `KEYCODE_DPAD_UP` and `KEYCODE_DPAD_DOWN` on the focusable elements, manually redirecting the focus within the desired panel, and returning `true` to consume the event and prevent global focus searches.

```java
btnPlay.setOnKeyListener(new View.OnKeyListener() {
    @Override
    public boolean onKey(View v, int keyCode, KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                // Focus back to the scrollable text reader above
                rightScrollView.requestFocus();
                // Smooth scroll up so buttons slide out of view naturally
                rightScrollView.smoothScrollBy(0, -100);
                return true; // Consume event!
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                return true; // Lock at the bottom, prevent leaking down/left!
            }
        }
        return false;
    }
});
```

---

## 2. ScrollView-to-Button Continuous Focus Transition

### The Pattern
Instead of making each text paragraph focusable (which creates weird selection boxes), make the overall `ScrollView` container focusable.
1. When the `ScrollView` is focused, highlight its background (e.g., `#303134`).
2. Intercept key events on the `ScrollView`:
   - If pressing **DOWN**, scroll down. If at the bottom, automatically shift focus to the first button below.
   - If pressing **UP**, scroll up. If at the top, consume the event to lock focus on the panel.
   - If pressing **OK/CENTER**, instantly jump focus to the primary button (Play button).

```java
rightScrollView.setFocusable(true);
rightScrollView.setFocusableInTouchMode(true);

rightScrollView.setOnFocusChangeListener(new View.OnFocusChangeListener() {
    @Override
    public void onFocusChange(View v, boolean hasFocus) {
        v.setBackgroundColor(hasFocus ? Color.parseColor("#303134") : Color.TRANSPARENT);
    }
});

rightScrollView.setOnClickListener(new View.OnClickListener() {
    @Override
    public void onClick(View v) {
        if (btnPlayRef != null && btnPlayRef.isEnabled()) {
            btnPlayRef.requestFocus(); // OK click jumps to action
        }
    }
});

rightScrollView.setOnKeyListener(new View.OnKeyListener() {
    @Override
    public boolean onKey(View v, int keyCode, KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            int currentScrollY = rightScrollView.getScrollY();
            int scrollViewHeight = rightScrollView.getHeight();
            int contentHeight = rightScrollContent.getHeight();
            
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                if (currentScrollY + scrollViewHeight < contentHeight - 15) {
                    rightScrollView.smoothScrollBy(0, 100);
                    return true;
                } else {
                    if (btnPlayRef != null) {
                        btnPlayRef.requestFocus(); // Dock to action button
                        return true;
                    }
                }
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                if (currentScrollY > 0) {
                    rightScrollView.smoothScrollBy(0, -100);
                    return true;
                } else {
                    return true; // Lock focus at top of panel
                }
            }
        }
        return false;
    }
});
```

---

## 3. Focus-Stealing Prevention during Async Reloads

### The Problem
When the user is active on the screen (e.g., navigating top filter rows) and an asynchronous network thread finishes and calls `.removeAllViews()` / `.populateGrid()`, the newly generated list often steals the focus and forces it down to the first item, interrupting the user's filter interactions.

### The Solution
Before requesting focus on newly populated list views, dynamically inspect the current active focus. If the current focused view belongs to the filter hierarchy, skip forcing focus to the new list items.

```java
View currentFocus = null;
if (context instanceof Activity) {
    currentFocus = ((Activity) context).getCurrentFocus();
}

boolean isFilterFocused = false;
if (currentFocus != null) {
    ViewParent parent = currentFocus.getParent();
    // Verify if the active focus resides inside the filter rows container
    if (parent != null && parent.getParent() instanceof HorizontalScrollView) {
        isFilterFocused = true;
    }
}

// Only auto-focus the first item on boot or when not interacting with filters
if (!isFilterFocused) {
    if (gridContainer.getChildCount() > 0) {
        LinearLayout firstRow = (LinearLayout) gridContainer.getChildAt(0);
        if (firstRow.getChildCount() > 0) {
            firstRow.getChildAt(0).requestFocus();
        }
    }
}
```

---

## 4. Sibling Tag Deselection inside Horizontal Filter Rows

### The Pattern
In horizontal TV filter rows (e.g., Category, Region, Year), selecting a tag must dynamically clear selections across all sibling tags in the row. Since Android TV keeps visual states, simply redrawing the clicked item is insufficient.

```java
item.setOnClickListener(new View.OnClickListener() {
    @Override
    public void onClick(View v) {
        selectedYear = opt; // Update model

        // Dynamically redraw all sibling views in the parent horizontal row
        for (int i = 0; i < optionsLayout.getChildCount(); i++) {
            View child = optionsLayout.getChildAt(i);
            if (child instanceof TextView) {
                TextView tv = (TextView) child;
                String childOpt = tv.getText().toString();
                // Pass tv.hasFocus() to preserve active cursor styling
                updateFilterItemStyle(tv, childOpt, type, tv.hasFocus());
            }
        }
        refreshMovieGrid();
    }
});
```

---

## 5. Agent-Friendly UI Observability (Passive Focused Logcat)

### The Pattern
Headless AI coding agents or automation rigs testing TV interfaces on remote boxes often suffer from slow, expensive, and non-deterministic visual screen analysis (screenshooting via ADB).
To make an app **Agent-Native** / **Agent-Friendly**, bind lightweight, structured text logging to all focus change and state listeners:

```java
// 1. Movie grid focus changes
@Override
public void onMovieCardFocused(Movie movie, View card) {
    Log.i(TAG, "🎯 FocusState: Movie Card focused -> 《" + movie.title + "》 (ID: " + movie.id + ")");
    ...
}

// 2. Panel focus changes
rightScrollView.setOnFocusChangeListener(new View.OnFocusChangeListener() {
    @Override
    public void onFocusChange(View v, boolean hasFocus) {
        if (hasFocus) {
            Log.i(TAG, "🎯 FocusState: Right Detail Panel focused");
        }
        ...
    }
});

// 3. Operational button focus changes
btnPlayNew.setOnFocusChangeListener(new View.OnFocusChangeListener() {
    @Override
    public void onFocusChange(View v, boolean hasFocus) {
        if (hasFocus) {
            Log.i(TAG, "🎯 FocusState: Play Button (New) focused");
        }
        ...
    }
});
```

An external agent can passively monitor this stream in real-time, instantly knowing focus coordinates without rendering pixels:
```bash
adb logcat -v raw -s GimyHorror_UI:I | grep "🎯 FocusState"
```

---

## 6. Dynamic 2D Asset Centering (Pillow/PIL Ink Bounding Box Alignment)

### The Problem
When drawing 2x2 grid logos/icons using PIL (Pillow), capital letters and lowercase letters with ascenders/descenders (like `G` and `i` vs `m` and `y`) have vastly different internal typography line spacing. Measuring via generic font metrics or layout bounds leaves large vertical gaps (up to 90px empty ascender margin), pushing the actual drawn ink down and causing heavy visual asymmetry (bottom-heavy layout).

### The Solution
Use Pillow's `draw.textbbox` to query the exact, raw coordinates of the **actual drawn ink (pixels)** of each character. Solve geometric equations dynamically to ensure margins are identical.

```python
# 1. Horizontal Calculations (Left margin == Right margin)
w_G_half = (bbox_G[2] - bbox_G[0]) / 2.0
w_m_half = (bbox_m[2] - bbox_m[0]) / 2.0
left_ext = max(w_G_half, w_m_half) # left-most column pixel extent

w_i_half = (bbox_i[2] - bbox_i[0]) / 2.0
w_y_half = (bbox_y[2] - bbox_y[0]) / 2.0
right_ext = max(w_i_half, w_y_half) # right-most column pixel extent

# Calculate centers (cx1, cx2) for columns spaced by col_dist
cx2 = (image_width - right_ext + left_ext + col_dist) / 2.0
cx1 = cx2 - col_dist

# 2. Vertical Calculations (Top margin == Bottom margin)
t1 = min(bbox_G[1], bbox_i[1]) # topmost pixel on row 1
b1 = max(bbox_G[3], bbox_i[3]) # bottommost pixel on row 1

t2 = min(bbox_m[1], bbox_y[1]) # topmost pixel on row 2
b2 = max(bbox_m[3], bbox_y[3]) # bottommost pixel on row 2

# Apply a beautiful breathing room gap (e.g. 15% of font_size)
gap_y = int(font_size * 0.15)
dy = b1 - t2 + gap_y

# Solve top-padding == bottom-padding
y1 = (image_height - dy - b2 - t1) // 2 + y_offset
y2 = y1 + dy

# 3. Draw using centered coordinates
draw.text((cx1 - w_G_half, y1), "G", fill=COLOR_BLUE, font=font)
draw.text((cx2 - w_i_half, y1), "i", fill=COLOR_RED, font=font)
draw.text((cx1 - w_m_half, y2), "m", fill=COLOR_YELLOW, font=font)
draw.text((cx2 - w_y_half, y2), "y", fill=COLOR_GREEN, font=font)
```

