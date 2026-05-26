#!/usr/bin/env python3
import urllib.request
import urllib.parse
import re
import subprocess
import json
import os
from mcp.server.fastmcp import FastMCP

mcp = FastMCP("gimy-tv")

def parse_time_to_seconds(time_str: str, duration_sec: int = 0) -> int:
    if not time_str:
        return -1
    
    time_str = str(time_str).strip().lower()
    
    # 1. Check standard format HH:MM:SS or MM:SS first!
    if ":" in time_str:
        parts = time_str.split(":")
        try:
            if len(parts) == 3: # HH:MM:SS
                h, m, s = int(parts[0]), int(parts[1]), int(parts[2])
                return h * 3600 + m * 60 + s
            elif len(parts) == 2: # MM:SS
                m, s = int(parts[0]), int(parts[1])
                return m * 60 + s
        except ValueError:
            pass

    is_relative = "last" in time_str or "countdown" in time_str or "倒數" in time_str or "剩" in time_str
    
    match = re.search(r'(\d+)\s*([a-z分秒鐘時]*)', time_str)
    if not match:
        return -1

    val = int(match.group(1))
    unit = match.group(2).strip()
    
    # Resolve unit to seconds
    sec = val
    if unit in ["m", "min", "分", "分鐘", "鐘"]:
        sec = val * 60
    elif unit in ["h", "hr", "hour", "hours", "時", "小時"]:
        sec = val * 3600
    elif unit in ["s", "sec", "second", "seconds", "秒", "秒鐘"]:
        sec = val
    else:
        if is_relative:
            sec = val * 60 # relative countdown defaults to minutes
        else:
            if val < 360:
                sec = val * 60
            else:
                sec = val
                
    if is_relative:
        if duration_sec > 0:
            return max(0, duration_sec - sec)
        else:
            return -sec
            
    return sec

def pull_movie_store(device_ip: str) -> dict:
    """Pull the MovieStore JSON from the TV to get playback progresses and list states."""
    local_path = "/tmp/GimyHorror_Store.json"
    remote_path = "/sdcard/Android/data/com.gimytv.horror/files/GimyHorror_Store.json"
    
    # Try to pull the file
    try:
        subprocess.run(
            ["adb", "-s", f"{device_ip}:5555", "pull", remote_path, local_path],
            capture_output=True, text=True, timeout=3
        )
        if os.path.exists(local_path):
            with open(local_path, "r", encoding="utf-8") as f:
                return json.load(f)
    except Exception:
        pass
    return {}

def format_seconds_to_time(sec: int) -> str:
    m, s = divmod(sec, 60)
    h, m = divmod(m, 60)
    if h > 0:
        return f"{h:02d}:{m:02d}:{s:02d}"
    return f"{m:02d}:{s:02d}"

def fetch_movie_details(movie_id: str):
    url = f"https://gimyplus.com/vod/{movie_id}.html"
    req = urllib.request.Request(
        url, 
        headers={'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36'}
    )
    title = ""
    image_url = ""
    subtitle = ""
    try:
        with urllib.request.urlopen(req, timeout=10) as response:
            html = response.read().decode('utf-8')
            
            title_match = re.search(r'<h1 class="text-overflow">([^<]+)</h1>', html)
            if title_match:
                title = title_match.group(1).strip()
            
            img_match = re.search(r'data-original="([^"]+)"', html)
            if img_match:
                image_url = img_match.group(1).strip()
            else:
                img_style = re.search(r'url\(\'([^\']+)\'\)', html)
                if img_style:
                    image_url = img_style.group(1).strip()
                    
            sub_match = re.search(r'主演：</span>(.*?)</div>', html, re.DOTALL)
            if not sub_match:
                sub_match = re.search(r'主演：</span>(.*?)</li>', html, re.DOTALL)
            if sub_match:
                subtitle = re.sub(r'<[^>]*>', '', sub_match.group(1)).replace("&nbsp;", " ").strip()
                subtitle = re.sub(r'\s+', ' ', subtitle).strip()
                if "年代：" in subtitle:
                    subtitle = subtitle.split("年代：")[0].strip()
    except Exception:
        pass
    return title, image_url, subtitle

@mcp.tool()
def gimy_search_movies(query: str, limit: int = 5, deviceIp: str = "100.87.89.52") -> str:
    """
    Search Gimy TV for movie keywords and return structured results with synopsis, playback progress, and list states.
    All times and durations are returned cleanly in seconds or HH:MM:SS format instead of raw milliseconds.
    
    Args:
        query: Movie keyword to search (e.g. '破墓', '黑祭司')
        limit: Max results to return (default: 5)
        deviceIp: The TV's IP to pull watch states (default: 100.87.89.52)
    """
    encoded_keyword = urllib.parse.quote(query)
    url = f"https://gimyplus.com/search/----------1---.html?wd={encoded_keyword}"
    req = urllib.request.Request(
        url, 
        headers={'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36'}
    )
    
    # Pull current watch progress and list states
    store = pull_movie_store(deviceIp)
    
    results = []
    try:
        with urllib.request.urlopen(req, timeout=10) as response:
            html = response.read().decode('utf-8')
            
            blocks = html.split('<div class="col-md-3 col-sm-4 col-xs-3 news-box-txt-l clearfix">')
            for b in blocks[1:limit+1]:
                href_match = re.search(r'href="/vod/(\d+)\.html"', b)
                title_match = re.search(r'title="([^"]+)"', b)
                
                img_match = re.search(r'data-original="([^"]+)"', b)
                if not img_match:
                    img_match = re.search(r"url\('([^']+)'\)", b)
                
                status_match = re.search(r'<span>狀態：</span>\s*(.*?)\s*</li>', b)
                if not status_match:
                    status_match = re.search(r'class="note[^>]*">\s*(.*?)\s*</span>', b)
                
                actors = ""
                actors_match = re.search(r'主演：</span>(.*?)</li>', b, re.DOTALL)
                if actors_match:
                    actors = re.sub(r'<[^>]*>', '', actors_match.group(1)).replace("&nbsp;", " ").strip()
                
                syn_match = re.search(r'<span class="details-content-default">(.*?)</span>', b, re.DOTALL)
                syn = syn_match.group(1).strip() if syn_match else "暫無簡介"
                syn = re.sub(r'<[^>]*>', '', syn).replace("&nbsp;", " ")
                
                movie_id = href_match.group(1) if href_match else ""
                
                # Check list states
                list_state_key = f"list_state_{movie_id}"
                list_state_val = store.get(list_state_key, 0)
                list_state_label = ""
                if list_state_val == 1:
                    list_state_label = "Watch List (待播) 📝"
                elif list_state_val == 2:
                    list_state_label = "Liked (喜歡) ❤️"
                elif list_state_val == 3:
                    list_state_label = "Disliked (不喜歡) 💩"
                
                # Check progress
                pos_key = f"progress_pos_{movie_id}"
                dur_key = f"progress_dur_{movie_id}"
                progress_label = "未觀看"
                if pos_key in store and dur_key in store:
                    pos_ms = store[pos_key]
                    dur_ms = store[dur_key]
                    if dur_ms > 0:
                        pos_sec = pos_ms // 1000
                        dur_sec = dur_ms // 1000
                        pct = (pos_ms / dur_ms) * 100
                        progress_label = f"已觀看 {pct:.1f}% ({format_seconds_to_time(pos_sec)} / {format_seconds_to_time(dur_sec)})"
                
                results.append({
                    "movieId": movie_id,
                    "movieTitle": title_match.group(1) if title_match else "",
                    "imageUrl": img_match.group(1) if img_match else "",
                    "status": status_match.group(1).strip() if status_match else "HD",
                    "subtitle": actors,
                    "synopsis": syn,
                    "listState": list_state_label,
                    "progress": progress_label
                })
    except Exception as e:
        return json.dumps({"error": f"Failed to search: {str(e)}"}, ensure_ascii=False)
        
    return json.dumps(results, ensure_ascii=False, indent=2)

@mcp.tool()
def gimy_launch_movie(movieId: str, movieTitle: str = "", imageUrl: str = "", subtitle: str = "", seekPosition: str = "", autoPlay: bool = True, deviceIp: str = "100.87.89.52") -> str:
    """
    Launch Gimy TV App on the TV, load, and play the specific movie directly.
    All times/positions accept values in seconds or flexible representation (e.g. 'last 5m', '01:30:00', '120s').
    
    Args:
        movieId: The movie's Gimy ID (e.g. '256828')
        movieTitle: Movie title (optional, auto-scraped if missing)
        imageUrl: Poster image URL (optional, auto-scraped if missing)
        subtitle: Cast details/subtitle (optional)
        seekPosition: Time to seek directly in seconds or flexible string (e.g. 'last 5m', '45m', '3600s', '01:20:00').
        autoPlay: Directly starts playing without manually pressing PLAY (default: True)
        deviceIp: The TV's IP (default: 100.87.89.52)
    """
    # Pull details if missing
    if not movieTitle or not imageUrl:
        t, img, sub = fetch_movie_details(movieId)
        if t:
            movieTitle = t
        if img:
            imageUrl = img
        if sub and not subtitle:
            subtitle = sub
            
    # Normalize image path
    if imageUrl and imageUrl.startswith("//"):
        imageUrl = "https:" + imageUrl
    elif imageUrl and imageUrl.startswith("/"):
        imageUrl = "https://gimyplus.com" + imageUrl

    # Resolve flexible seek Position to seconds, then convert to ms
    seek_ms = -1
    if seekPosition:
        store = pull_movie_store(deviceIp)
        dur_key = f"progress_dur_{movieId}"
        duration_ms = store.get(dur_key, 0)
        duration_sec = duration_ms // 1000
        
        seek_sec = parse_time_to_seconds(seekPosition, duration_sec)
        if seek_sec != -1:
            seek_ms = seek_sec * 1000

    # Construct the deep link ADB command
    cmd = [
        "adb", "-s", f"{deviceIp}:5555", "shell", "am", "start", "-n", "com.gimytv.horror/.MainActivity",
        "-e", "movieId", f"'{movieId}'",
        "-e", "movieTitle", f"'{movieTitle}'",
        "-e", "imageUrl", f"'{imageUrl}'",
        "-e", "subtitle", f"'{subtitle}'"
    ]
    
    if autoPlay:
        cmd.extend(["--ez", "autoPlay", "true"])
        
    if seek_ms != -1:
        cmd.extend(["-e", "seekPositionMs", f"'{seek_ms}'"])
    
    try:
        res = subprocess.run(cmd, capture_output=True, text=True, timeout=5)
        if res.returncode == 0:
            return json.dumps({
                "success": True,
                "message": f"Successfully launched movie '{movieTitle}' directly playing on TV.",
                "details": {
                    "movieId": movieId,
                    "movieTitle": movieTitle,
                    "imageUrl": imageUrl,
                    "subtitle": subtitle,
                    "autoPlay": autoPlay,
                    "seekPosition": seekPosition,
                    "seekPositionMs": seek_ms,
                    "seekPositionSec": seek_ms // 1000 if seek_ms != -1 else -1
                }
            }, ensure_ascii=False, indent=2)
        else:
            return json.dumps({
                "success": False,
                "error": res.stderr
            }, ensure_ascii=False, indent=2)
    except Exception as e:
        return json.dumps({"success": False, "error": str(e)}, ensure_ascii=False)

@mcp.tool()
def gimy_playback_control(action: str, seekSeconds: int = 30, deviceIp: str = "100.87.89.52") -> str:
    """
    Control movie playback state (PLAY, PAUSE, SEEK_FORWARD, SEEK_BACKWARD, VOLUME_UP, VOLUME_DOWN).
    
    Args:
        action: The playback command ('PLAY', 'PAUSE', 'TOGGLE_PLAY_PAUSE', 'SEEK_FORWARD', 'SEEK_BACKWARD', 'VOLUME_UP', 'VOLUME_DOWN')
        seekSeconds: Seek duration in seconds (defaults to 30)
        deviceIp: The TV's IP (default: 100.87.89.52)
    """
    action = action.upper()
    cmd_sequences = []
    
    if action in ["PLAY", "PAUSE", "TOGGLE_PLAY_PAUSE"]:
        cmd_sequences.append(["adb", "-s", f"{deviceIp}:5555", "shell", "input", "keyevent", "KEYCODE_DPAD_CENTER"])
    elif action == "SEEK_FORWARD":
        cmd_sequences.append(["adb", "-s", f"{deviceIp}:5555", "shell", "input", "keyevent", "KEYCODE_DPAD_RIGHT"])
        cmd_sequences.append(["adb", "-s", f"{deviceIp}:5555", "shell", "input", "keyevent", "KEYCODE_DPAD_CENTER"])
    elif action == "SEEK_BACKWARD":
        cmd_sequences.append(["adb", "-s", f"{deviceIp}:5555", "shell", "input", "keyevent", "KEYCODE_DPAD_LEFT"])
        cmd_sequences.append(["adb", "-s", f"{deviceIp}:5555", "shell", "input", "keyevent", "KEYCODE_DPAD_CENTER"])
    elif action == "VOLUME_UP":
        cmd_sequences.append(["adb", "-s", f"{deviceIp}:5555", "shell", "input", "keyevent", "KEYCODE_VOLUME_UP"])
    elif action == "VOLUME_DOWN":
        cmd_sequences.append(["adb", "-s", f"{deviceIp}:5555", "shell", "input", "keyevent", "KEYCODE_VOLUME_DOWN"])
    else:
        return json.dumps({"success": False, "error": f"Unsupported action: {action}"})
        
    try:
        for cmd in cmd_sequences:
            subprocess.run(cmd, capture_output=True, text=True, timeout=5)
        return json.dumps({"success": True, "action": action, "message": f"Successfully performed '{action}' on TV."})
    except Exception as e:
        return json.dumps({"success": False, "error": str(e)})

@mcp.tool()
def gimy_get_tv_state(deviceIp: str = "100.87.89.52") -> str:
    """
    Get current TV focus state, app package, and recent Gimy player events.
    All durations and times in logs are parsed and formatted cleanly.
    
    Args:
        deviceIp: The TV's IP (default: 100.87.89.52)
    """
    tv_state = {
        "deviceIp": deviceIp,
        "isOnline": False,
        "currentFocus": "Unknown",
        "recentLogs": []
    }
    
    try:
        ping_res = subprocess.run(["adb", "devices"], capture_output=True, text=True, timeout=3)
        if f"{deviceIp}:5555" in ping_res.stdout:
            tv_state["isOnline"] = True
    except Exception:
        pass
        
    if not tv_state["isOnline"]:
        return json.dumps(tv_state, ensure_ascii=False, indent=2)
        
    try:
        focus_res = subprocess.run(["adb", "-s", f"{deviceIp}:5555", "shell", "dumpsys", "window"], capture_output=True, text=True, timeout=3)
        for line in focus_res.stdout.splitlines():
            if "mCurrentFocus" in line:
                tv_state["currentFocus"] = line.strip()
                break
    except Exception:
        pass
        
    try:
        logcat_res = subprocess.run(
            ["adb", "-s", f"{deviceIp}:5555", "logcat", "-d", "-s", "GimyHorror_Player", "GimyHorror_UI", "GimyHorror_Parser", "GimyHorror_Store"],
            capture_output=True, text=True, timeout=3
        )
        logs = logcat_res.stdout.splitlines()[-15:]
        
        # Clean and simplify logs (convert raw milliseconds to seconds/readable text for better agent understanding)
        clean_logs = []
        for l in logs:
            line = l.strip()
            if not line:
                continue
            # Regex match raw milliseconds and format them
            ms_matches = re.findall(r'(\d+)\s*ms', line)
            for ms_str in ms_matches:
                ms_val = int(ms_str)
                # Skip simple integers that might not be timestamps (like movie IDs)
                if ms_val > 1000:
                    sec_val = ms_val // 1000
                    line = line.replace(f"{ms_str} ms", f"{ms_str} ms ({format_seconds_to_time(sec_val)})")
            clean_logs.append(line)
            
        tv_state["recentLogs"] = clean_logs
    except Exception:
        pass
        
    return json.dumps(tv_state, ensure_ascii=False, indent=2)

@mcp.tool()
def gimy_set_movie_list_state(movieId: str, state: int, deviceIp: str = "100.87.89.52") -> str:
    """
    Set a movie's watchlist or favorites list state (None, Watch List, Liked/Favorite, Disliked).
    
    Args:
        movieId: The movie's Gimy ID (e.g. '255334')
        state: The list state to set (0 = None, 1 = Watch List 📝, 2 = Liked/Favorite ❤️, 3 = Disliked 💩)
        deviceIp: The TV's IP (default: 100.87.89.52)
    """
    if state not in [0, 1, 2, 3]:
        return json.dumps({"success": False, "error": f"Invalid state value: {state}. Must be 0, 1, 2, or 3."})
        
    cmd = [
        "adb", "-s", f"{deviceIp}:5555", "shell", "am", "start", "-n", "com.gimytv.horror/.MainActivity",
        "-e", "movieId", f"'{movieId}'",
        "-e", "listState", f"'{state}'"
    ]
    
    try:
        res = subprocess.run(cmd, capture_output=True, text=True, timeout=5)
        if res.returncode == 0:
            return json.dumps({
                "success": True,
                "message": f"Successfully set movie list state for ID '{movieId}' to {state} on TV.",
                "details": {
                    "movieId": movieId,
                    "state": state
                }
            }, ensure_ascii=False, indent=2)
        else:
            return json.dumps({
                "success": False,
                "error": res.stderr
            }, ensure_ascii=False, indent=2)
    except Exception as e:
        return json.dumps({"success": False, "error": str(e)}, ensure_ascii=False)

if __name__ == "__main__":
    mcp.run()
