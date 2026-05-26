#!/usr/bin/env python3
import sys
import os
import json
import subprocess
from unittest.mock import patch, MagicMock

# Add scripts directory to path
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "../scripts")))

# A mock function to intercept subprocess.run
def mock_subprocess_run(cmd, *args, **kwargs):
    mock_res = MagicMock(spec=subprocess.CompletedProcess)
    mock_res.returncode = 0
    mock_res.stdout = ""
    mock_res.stderr = ""
    
    cmd_str = " ".join(cmd) if isinstance(cmd, list) else cmd
    
    if "devices" in cmd_str:
        mock_res.stdout = "List of devices attached\n100.87.89.52:5555\tdevice\n"
    elif "dumpsys window" in cmd_str:
        mock_res.stdout = "mCurrentFocus=Window{ac5eca u0 com.gimytv.horror/com.gimytv.horror.MainActivity}"
    elif "logcat" in cmd_str:
        mock_res.stdout = """
05-26 23:06:46.131 19990 19990 I GimyHorror_Player: ⏹ stopping playback for Movie ID: 255334
05-26 23:06:46.139 19990 19990 I GimyHorror_UI: 🎯 FocusState: Filter focused -> Sort: 熱門推薦
05-26 23:16:46.467 19990 19990 I GimyHorror_Player: ⏸ pausePlaybackOnBackground triggered.
05-26 23:23:30.537 19990 19990 I GimyHorror_UI: 📥 handleIntent received. Movie ID: 256828
05-26 23:23:30.537 19990 19990 I GimyHorror_UI: 📥 Restoring Watch Next / Deep Link for: 破墓 | autoPlay: true
"""
    elif "pull" in cmd_str:
        dest = cmd[5] if len(cmd) > 5 else cmd[4] if len(cmd) > 4 else "/tmp/GimyHorror_Store.json"
        mock_store = {
            "list_state_255334": 2,
            "progress_pos_255334": 6078233,
            "progress_dur_255334": 6078417
        }
        with open(dest, "w", encoding="utf-8") as f:
            json.dump(mock_store, f)
    elif "am start" in cmd_str:
        mock_res.stdout = "Starting: Intent { act=android.intent.action.MAIN ... }"
    elif "keyevent" in cmd_str:
        mock_res.stdout = ""
    
    return mock_res

# Conditionally apply patch based on GIMY_REAL_DEVICE environment variable
use_real_device = os.environ.get("GIMY_REAL_DEVICE") == "1"
patcher = None

if not use_real_device:
    print("ℹ️ Running in SAFE MOCK MODE (will not affect your TV). Set GIMY_REAL_DEVICE=1 to run on actual TV.")
    patcher = patch("subprocess.run", side_effect=mock_subprocess_run)
    patcher.start()

from gimy_mcp_server import gimy_search_movies, gimy_launch_movie, gimy_playback_control, gimy_get_tv_state

def test_closed_loop():
    print("==================================================")
    print("🎬 STARTING GIMY TV APP MCP CLOSED-LOOP TESTING")
    print("==================================================")
    
    # 1. Test Search Movie (with Synopsis)
    print("\n🔍 Step 1: Testing Search Movie for '破墓'...")
    search_res_json = gimy_search_movies("破墓", limit=3)
    try:
        search_results = json.loads(search_res_json)
        print(f"✅ Search Succeeded! Found {len(search_results)} results.")
        for idx, item in enumerate(search_results):
            print(f"\nResult #{idx+1}:")
            print(f"  - ID: {item.get('movieId')}")
            print(f"  - Title: {item.get('movieTitle')}")
            print(f"  - Image URL: {item.get('imageUrl')}")
            print(f"  - Status: {item.get('status')}")
            print(f"  - Subtitle: {item.get('subtitle')}")
            print(f"  - Synopsis: {item.get('synopsis')}")
            
            # Verify required keys are present and not empty
            assert item.get('movieId'), "Missing movieId"
            assert item.get('movieTitle'), "Missing movieTitle"
            assert item.get('imageUrl'), "Missing imageUrl"
            assert item.get('synopsis'), "Missing synopsis"
        print("\n✅ Step 1: Search Verification Passed! (Synopsis is included!)")
    except Exception as e:
        print(f"❌ Step 1 Failed: {e}")
        sys.exit(1)

    # 2. Test Get TV State
    print("\n📺 Step 2: Testing Get TV State...")
    state_res_json = gimy_get_tv_state()
    try:
        state = json.loads(state_res_json)
        print("✅ TV State Succeeded!")
        print(f"  - Device IP: {state.get('deviceIp')}")
        print(f"  - Is Online: {state.get('isOnline')}")
        print(f"  - Current Focus: {state.get('currentFocus')}")
        print(f"  - Recent Logs Count: {len(state.get('recentLogs', []))}")
        for log in state.get('recentLogs', [])[-5:]:
            print(f"    [LOG] {log}")
    except Exception as e:
        print(f"❌ Step 2 Failed: {e}")
        sys.exit(1)

    # 3. Test Launch Movie (Deep-Link Intent)
    # Using '256828' (破墓)
    target_movie_id = "256828"
    print(f"\n🚀 Step 3: Testing Launch Movie (Deep-Link) for ID: {target_movie_id}...")
    launch_res_json = gimy_launch_movie(movieId=target_movie_id)
    try:
        launch_res = json.loads(launch_res_json)
        print(f"✅ Launch Result: {json.dumps(launch_res, ensure_ascii=False, indent=2)}")
        assert launch_res.get('success'), "Launch unsuccessful"
    except Exception as e:
        print(f"❌ Step 3 Failed: {e}")
        sys.exit(1)

    # 4. Test Playback Control (PLAY action to start/pause)
    print("\n🎛️ Step 4: Testing Playback Control (PLAY)...")
    play_res_json = gimy_playback_control(action="PLAY")
    try:
        play_res = json.loads(play_res_json)
        print(f"✅ Playback Control Result: {json.dumps(play_res, ensure_ascii=False, indent=2)}")
        assert play_res.get('success'), "Playback action failed"
    except Exception as e:
        print(f"❌ Step 4 Failed: {e}")
        sys.exit(1)

    # 5. Test Get TV State Again to check Focus and Logs
    print("\n📊 Step 5: Refreshing TV State after Launch/Playback...")
    state_res_json = gimy_get_tv_state()
    try:
        state = json.loads(state_res_json)
        print("✅ Final TV State Checked!")
        print(f"  - Final Focus: {state.get('currentFocus')}")
        print(f"  - Final Logs Count: {len(state.get('recentLogs', []))}")
        for log in state.get('recentLogs', [])[-8:]:
            print(f"    [LOG] {log}")
    except Exception as e:
        print(f"❌ Step 5 Failed: {e}")
        sys.exit(1)

    print("\n==================================================")
    print("🏆 ALL GIMY TV MCP TOOLS PASSED CLOSED-LOOP TESTING!")
    print("==================================================")

if __name__ == "__main__":
    test_closed_loop()
    if patcher is not None:
        patcher.stop()
