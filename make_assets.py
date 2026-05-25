import os
from PIL import Image, ImageDraw, ImageFont, ImageFilter

WORKDIR = "/home/ubuntu/gimy_tv_app"
res_drawable_dir = os.path.join(WORKDIR, "res", "drawable")
os.makedirs(res_drawable_dir, exist_ok=True)

# Colors
COLOR_BG_DARK = (14, 14, 16)      # #0E0E10
COLOR_BG_RED_GLOW = (36, 16, 20)  # #241014
COLOR_G_BLUE = (66, 133, 244)     # #4285F4
COLOR_G_RED = (234, 67, 53)       # #EA4335
COLOR_G_YELLOW = (251, 188, 5)    # #FBBC05
COLOR_G_GREEN = (52, 168, 83)     # #34A853

font_path = "/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc"

def create_radial_gradient(width, height, color_inner, color_outer):
    # Create radial gradient canvas
    base = Image.new("RGB", (width, height), color_outer)
    draw = ImageDraw.Draw(base)
    for r in range(width, 0, -8):
        # Interpolate color
        ratio = r / width
        color = (
            int(color_inner[0] * (1 - ratio) + color_outer[0] * ratio),
            int(color_inner[1] * (1 - ratio) + color_outer[1] * ratio),
            int(color_inner[2] * (1 - ratio) + color_outer[2] * ratio)
        )
        # Draw circle centered
        draw.ellipse([width//2 - r, height//2 - r, width//2 + r, height//2 + r], fill=color)
    # Blur the gradient for smooth look
    return base.filter(ImageFilter.GaussianBlur(30))

def draw_single_row_gimy(image, target_width=400, y_offset=0):
    draw = ImageDraw.Draw(image)
    # Start with a reasonable font size
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

# ==================== 1. Generate TV Banner (960x540) ====================
print("Generating TV Banner (960x540)...")
banner = create_radial_gradient(960, 540, COLOR_BG_RED_GLOW, COLOR_BG_DARK)
# A wide banner can display "Gimy" larger, e.g., target width 600px
draw_single_row_gimy(banner, target_width=600, y_offset=0)
banner.save(os.path.join(res_drawable_dir, "tv_banner.png"))

# ==================== 2. Generate Square Icon (512x512) ====================
print("Generating App Icon (512x512)...")
icon = create_radial_gradient(512, 512, COLOR_BG_RED_GLOW, COLOR_BG_DARK)
# Square icon needs "Gimy" to fit within the circular crop safe zone (e.g. 380px wide)
draw_single_row_gimy(icon, target_width=380, y_offset=0)
icon.save(os.path.join(res_drawable_dir, "ic_launcher.png"))

print("Assets successfully generated in res/drawable/!")
