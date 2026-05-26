# Pillow (PIL) 2D Ink-Bounding-Box Geometric Centering & Column Alignment

This document outlines the mathematical formula and rendering guidelines for achieving perfect vertical and horizontal centering of 2D text-based assets (such as launcher icons or split-grid logos) in Pillow (PIL), bypassing internal font metric offsets and ascender padding bugs.

---

## ⚠️ The Pillow `textbbox` Ascender Pitfall

When drawing characters using `draw.text((x, y), char, font=font)`, standard Pillow aligns the *baseline* or the *font layout box's top* with your specified coordinate.
For CJK, uppercase, or mixed-case characters (e.g., Row 1: "Gi", Row 2: "my"), the font file often reserves a large vertical empty padding (ascender space) at the top of the characters (e.g., up to **90 pixels** for `font_size = 220`).

If you calculate centering using simple line-height metrics:
```python
total_height = font_size * 2 * line_spacing_factor
start_y = (canvas_height - total_height) // 2
```
The text block will be drawn **heavily pushed towards the bottom**, leaving a large empty gap at the top of the canvas, causing letters with descenders (like `y`) to clip or touch the bottom border.

---

## 📐 The Dynamic Ink-Bounding-Box Solution

To achieve perfect optical and geometric centering, you must calculate coordinates based on the **actual drawn ink (pixels)** of the letters.

### 1. Get raw letter boundaries
Query the exact ink bounding box of each letter when drawn at `(0, 0)`:
```python
bbox_G = draw.textbbox((0, 0), "G", font=font) # e.g., (0, 90, 158, 259)
bbox_i = draw.textbbox((0, 0), "i", font=font) # e.g., (0, 77, 67, 256)
bbox_m = draw.textbbox((0, 0), "m", font=font) # e.g., (0, 130, 212, 256)
bbox_y = draw.textbbox((0, 0), "y", font=font) # e.g., (0, 133, 126, 305)
```

### 2. Vertical Centering Formula
Find the exact vertical span of ink for both rows:
*   **Row 1 top ink boundary ($t_1$)**: `t1 = min(bbox_G[1], bbox_i[1])` (e.g., `77`)
*   **Row 1 bottom ink boundary ($b_1$)**: `b1 = max(bbox_G[3], bbox_i[3])` (e.g., `259`)
*   **Row 2 top ink boundary ($t_2$)**: `t2 = min(bbox_m[1], bbox_y[1])` (e.g., `130`)
*   **Row 2 bottom ink boundary ($b_2$)**: `b2 = max(bbox_m[3], bbox_y[3])` (e.g., `305`)

#### Step A: Calculate row separation ($dy$)
To separate Row 1 and Row 2 by a breathing vertical gap of $G_y$ (ideally **15% of the font size**, e.g., `int(font_size * 0.15)`):
$$dy = b_1 - t_2 + G_y$$

#### Step B: Solve for perfect top/bottom margins
We want the top padding of Row 1 to equal the bottom padding of Row 2:
$$y_1 + t_1 = \text{canvas\_height} - (y_2 + b_2)$$
Substitute $y_2 = y_1 + dy$:
$$2 \cdot y_1 = \text{canvas\_height} - dy - b_2 - t_1$$
$$y_1 = \frac{\text{canvas\_height} - dy - b_2 - t_1}{2}$$
$$y_2 = y_1 + dy$$

---

## 📊 Horizontal Column-Alignment Formula

For a 2x2 text grid (Column 1: `G, m`, Column 2: `i, y`), centering each row independently causes left and right edges to misalign, making the grid look skewed. You must align the columns and center the overall bounding box as a single block.

### 1. Calculate column boundaries
Find the maximum half-width of ink for each column from the centers of the characters (accounting for character bearings):
*   **Column 1 left extension ($L_{ext}$)**: `left_ext = max((bbox_G[2]+bbox_G[0])/2.0, (bbox_m[2]+bbox_m[0])/2.0)`
*   **Column 2 right extension ($R_{ext}$)**: `right_ext = max((bbox_i[2]+bbox_i[0])/2.0, (bbox_y[2]+bbox_y[0])/2.0)`

### 2. Solve for perfect left/right margins
Let $cx_1$ and $cx_2$ be the horizontal centers of Column 1 and Column 2. Let the desired distance between column centers be $D_{col}$ (ideally **95% of the font size**, e.g., `int(font_size * 0.95)`):
$$cx_2 - cx_1 = D_{col}$$

For perfect symmetry (Left Padding == Right Padding):
$$cx_1 - L_{ext} = \text{canvas\_width} - (cx_2 + R_{ext})$$
Substitute $cx_1 = cx_2 - D_{col}$ and solve:
$$cx_2 = \frac{\text{canvas\_width} - R_{ext} + L_{ext} + D_{col}}{2}$$
$$cx_1 = cx_2 - D_{col}$$

---

## 🎨 Drawing Coordinates
Render each letter by centering its X around its column center and aligning its Y with its row (accounting for left bearing `bbox[0]` shifts):
```python
# Row 1
draw.text((cx1 - (bbox_G[2] + bbox_G[0])/2.0, y1), "G", fill=COLOR_G_BLUE, font=font)
draw.text((cx2 - (bbox_i[2] + bbox_i[0])/2.0, y1), "i", fill=COLOR_G_RED, font=font)

# Row 2
draw.text((cx1 - (bbox_m[2] + bbox_m[0])/2.0, y2), "m", fill=COLOR_G_YELLOW, font=font)
draw.text((cx2 - (bbox_y[2] + bbox_y[0])/2.0, y2), "y", fill=COLOR_G_GREEN, font=font)
```
This guarantees an mathematically absolute 2D center, with zero clipping and stunning visual symmetry.
