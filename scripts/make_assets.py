import os
from PIL import Image, ImageDraw, ImageFont, ImageFilter

WORKDIR = "/home/ubuntu/gimy-tv-app"
res_drawable_dir = os.path.join(WORKDIR, "app", "src", "main", "res", "drawable")
os.makedirs(res_drawable_dir, exist_ok=True)

# Colors
COLOR_BG_DARK = (14, 14, 16)      # #0E0E10
COLOR_BG_RED_GLOW = (36, 16, 20)  # #241014
COLOR_G_BLUE = (66, 133, 244)     # #4285F4
COLOR_G_RED = (234, 67, 53)       # #EA4335
COLOR_G_YELLOW = (251, 188, 5)    # #FBBC05
COLOR_G_GREEN = (52, 168, 83)     # #34A853

font_path = "/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc"

row1 = [("G", COLOR_G_BLUE), ("i", COLOR_G_RED)]
row2 = [("m", COLOR_G_YELLOW), ("y", COLOR_G_GREEN)]

def create_radial_gradient(width, height, color_inner, color_outer):
    # Create radial gradient canvas
    base = Image.new("RGB", (width, height), color_outer)
    draw = ImageDraw.Draw(base)
    for r in range(width, 0, -8):
        # Interpolate color
        ratio = r / width
        color = (
            int(color_inner[0] * (1 - ratio) + color_outer[0] * ratio),
            int(color_inner[1] * (1 - ratio) + color_outer[2] * ratio), # Blue channel correction
            int(color_inner[2] * (1 - ratio) + color_outer[2] * ratio)
        )
        # Draw circle centered
        draw.ellipse([width//2 - r, height//2 - r, width//2 + r, height//2 + r], fill=color)
    # Blur the gradient for smooth look
    return base.filter(ImageFilter.GaussianBlur(30))

def draw_single_row_gimy(image, target_width=400, y_offset=0):
    draw = ImageDraw.Draw(image)
    font_size = 140
    try:
        font = ImageFont.truetype(font_path, font_size)
    except Exception as e:
        print(f"Error loading font, falling back: {e}")
        font = ImageFont.load_default()
        
    letters = [("G", COLOR_G_BLUE), ("i", COLOR_G_RED), ("m", COLOR_G_YELLOW), ("y", COLOR_G_GREEN)]
    
    # Adjust font_size programmatically to fit target_width
    for attempt in range(30):
        try:
            font = ImageFont.truetype(font_path, font_size)
        except Exception:
            font = ImageFont.load_default()
        spacing = int(font_size * 0.05)
        total_w = 0
        for char, color in letters:
            bbox = draw.textbbox((0, 0), char, font=font)
            w = bbox[2] - bbox[0]
            total_w += w
        total_w += spacing * (len(letters) - 1)
        
        if total_w > target_width:
            font_size -= 2
        elif total_w < target_width - 10:
            font_size += 2
        else:
            break
            
    # Final draw parameters
    try:
        font = ImageFont.truetype(font_path, font_size)
    except Exception:
        font = ImageFont.load_default()
    spacing = int(font_size * 0.05)
    
    # Calculate exact size for perfect alignment
    total_w = 0
    for char, color in letters:
        bbox = draw.textbbox((0, 0), char, font=font)
        w = bbox[2] - bbox[0]
        total_w += w
    total_w += spacing * (len(letters) - 1)
    
    # Draw centered
    x = (image.width - total_w) // 2
    
    # Measure typical letter height to center vertically
    bbox_G = draw.textbbox((0, 0), "G", font=font)
    h_line = bbox_G[3] - bbox_G[1]
    y = (image.height - h_line) // 2 + y_offset - int(h_line * 0.1) # slight manual baseline adjust
    
    current_x = x
    for char, color in letters:
        draw.text((current_x, y), char, fill=color, font=font)
        bbox = draw.textbbox((current_x, y), char, font=font)
        current_x += (bbox[2] - bbox[0]) + spacing

def draw_two_rows_gimy(image, font_size, y_offset=0):
    draw = ImageDraw.Draw(image)
    try:
        font = ImageFont.truetype(font_path, font_size)
    except Exception as e:
        print(f"Error loading font, falling back: {e}")
        font = ImageFont.load_default()
        
    spacing = int(font_size * 0.05)
    
    # --- Dynamic Perfect 2x2 Grid Centering (Horizontal & Vertical) ---
    bbox_G = draw.textbbox((0, 0), "G", font=font)
    bbox_i = draw.textbbox((0, 0), "i", font=font)
    bbox_m = draw.textbbox((0, 0), "m", font=font)
    bbox_y = draw.textbbox((0, 0), "y", font=font)
    
    # 1. Horizontal Calculations
    col_dist = int(font_size * 0.84) # tighter column spacing (prevents overlap, keeps it highly cohesive)
    
    w_G_half = (bbox_G[2] - bbox_G[0]) / 2.0
    w_m_half = (bbox_m[2] - bbox_m[0]) / 2.0
    left_ext = max(w_G_half, w_m_half)
    
    w_i_half = (bbox_i[2] - bbox_i[0]) / 2.0
    w_y_half = (bbox_y[2] - bbox_y[0]) / 2.0
    right_ext = max(w_i_half, w_y_half)
    
    # Solved equations for Left padding == Right padding:
    cx2 = (image.width - right_ext + left_ext + col_dist) / 2.0
    cx1 = cx2 - col_dist
    
    # 2. Vertical Calculations
    t1 = min(bbox_G[1], bbox_i[1]) # top of row 1 ink
    b1 = max(bbox_G[3], bbox_i[3]) # bottom of row 1 ink
    
    t2 = min(bbox_m[1], bbox_y[1]) # top of row 2 ink
    b2 = max(bbox_m[3], bbox_y[3]) # bottom of row 2 ink
    
    # Beautiful vertical breathing gap (15% of font size) between bottom of Row 1 and top of Row 2
    gap_y = int(font_size * 0.15)
    dy = b1 - t2 + gap_y
    
    # Solved equations for Top padding == Bottom padding:
    y1 = (image.height - dy - b2 - t1) // 2 + y_offset
    y2 = y1 + dy
    
    # --- Draw Letters ---
    # Row 1 Left: "G" (Centered at cx1)
    draw.text((cx1 - w_G_half, y1), "G", fill=COLOR_G_BLUE, font=font)
    # Row 1 Right: "i" (Centered at cx2)
    draw.text((cx2 - w_i_half, y1), "i", fill=COLOR_G_RED, font=font)
    # Row 2 Left: "m" (Centered at cx1)
    draw.text((cx1 - w_m_half, y2), "m", fill=COLOR_G_YELLOW, font=font)
    # Row 2 Right: "y" (Centered at cx2)
    draw.text((cx2 - w_y_half, y2), "y", fill=COLOR_G_GREEN, font=font)

# ==================== 1. Generate TV Banner (960x540) ====================
print("Generating TV Banner (960x540)...")
banner = create_radial_gradient(960, 540, COLOR_BG_RED_GLOW, COLOR_BG_DARK)
# Widescreen TV banner looks extremely elegant with Gimy in a single horizontal line
draw_single_row_gimy(banner, target_width=600, y_offset=0)
banner.save(os.path.join(res_drawable_dir, "tv_banner.png"))

# ==================== 2. Generate Square Icon (512x512) ====================
print("Generating App Icon (512x512)...")
# Flat solid dark background to make the colored font pop perfectly as requested
icon = Image.new("RGB", (512, 512), COLOR_BG_DARK)
draw_two_rows_gimy(icon, font_size=220, y_offset=0)
icon.save(os.path.join(res_drawable_dir, "ic_launcher.png"))

print("Assets successfully generated in res/drawable/!")
