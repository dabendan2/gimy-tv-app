import os
from PIL import Image, ImageDraw, ImageFont, ImageFilter

WORKDIR = "/home/ubuntu/gimy_tv_app"
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

def draw_two_rows_gimy(image, font_size, line_spacing_factor=0.9, y_offset=0):
    draw = ImageDraw.Draw(image)
    try:
        font = ImageFont.truetype(font_path, font_size)
    except Exception as e:
        print(f"Error loading font, falling back: {e}")
        font = ImageFont.load_default()
        
    spacing = int(font_size * 0.05)
    
    # 1. Calculate Row 1 width ("G", "i")
    w1 = 0
    for char, color in row1:
        bbox = draw.textbbox((0, 0), char, font=font)
        w = bbox[2] - bbox[0]
        w1 += w
    w1 += spacing * (len(row1) - 1)
    
    # 2. Calculate Row 2 width ("m", "y")
    w2 = 0
    for char, color in row2:
        bbox = draw.textbbox((0, 0), char, font=font)
        w = bbox[2] - bbox[0]
        w2 += w
    w2 += spacing * (len(row2) - 1)
    
    # Height of one line
    bbox_G = draw.textbbox((0, 0), "G", font=font)
    h_line = bbox_G[3] - bbox_G[1]
    
    # Total height of both rows
    total_height = int(h_line * 2 * line_spacing_factor)
    
    # Starting Y position to center vertically
    start_y = (image.height - total_height) // 2 + y_offset
    
    # Draw Row 1 ("Gi")
    x1 = (image.width - w1) // 2
    current_x = x1
    for char, color in row1:
        draw.text((current_x, start_y), char, fill=color, font=font)
        bbox = draw.textbbox((current_x, start_y), char, font=font)
        current_x += (bbox[2] - bbox[0]) + spacing
        
    # Draw Row 2 ("my")
    x2 = (image.width - w2) // 2
    current_x = x2
    y2 = start_y + int(h_line * line_spacing_factor)
    for char, color in row2:
        draw.text((current_x, y2), char, fill=color, font=font)
        bbox = draw.textbbox((current_x, y2), char, font=font)
        current_x += (bbox[2] - bbox[0]) + spacing

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
draw_single_row_gimy(icon, target_width=440, y_offset=0)
icon.save(os.path.join(res_drawable_dir, "ic_launcher.png"))

print("Assets successfully generated in res/drawable/!")
