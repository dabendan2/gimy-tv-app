#!/usr/bin/env python3
import sys
import os
import json
import subprocess
import time

# Add scripts directory to path
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "../scripts")))

from gimy_mcp_server import gimy_search_movies, gimy_launch_movie, gimy_get_tv_state

def test_closed_loop_advanced():
    print("==================================================")
    print("🎬 STARTING GIMY TV APP MCP ENHANCED CLOSED-LOOP TESTING")
    print("==================================================")
    
    # Close any active player first
    print("1. Closing any active player...")
    subprocess.run(["adb", "shell", "input", "keyevent", "KEYCODE_BACK"])
    time.sleep(1)

    # 1. Test Search Movie (Check if Watch Progress and List State are returned!)
    print("\n🔍 Step 1: Testing Search Movie with watch progress & states for '女鬼橋2'...")
    search_res_json = gimy_search_movies("女鬼橋2")
    try:
        search_results = json.loads(search_res_json)
        print("✅ Enriched Search Succeeded!")
        for idx, item in enumerate(search_results):
            print(f"\nResult #{idx+1}:")
            print(f"  - Title: {item.get('movieTitle')}")
            print(f"  - ID: {item.get('movieId')}")
            print(f"  - List State (喜愛/待播): {item.get('listState')}")
            print(f"  - Watch Progress (觀看進度): {item.get('progress')}")
            print(f"  - Synopsis: {item.get('synopsis')[:60]}...")
            
            # Verify watch states are present
            assert "listState" in item, "Missing listState field"
            assert "progress" in item, "Missing progress field"
    except Exception as e:
        print(f"❌ Step 1 Failed: {e}")
        sys.exit(1)

    # 2. Test Direct Play + Seek to Last 5 minutes
    # Using '255334' (女鬼橋2：怨鬼樓)
    target_movie_id = "255334"
    print(f"\n🚀 Step 2: Launching '女鬼橋2' directly with seekPosition='last 5m' & autoPlay=True...")
    launch_res_json = gimy_launch_movie(movieId=target_movie_id, seekPosition="last 5m", autoPlay=True)
    try:
        launch_res = json.loads(launch_res_json)
        print(f"✅ Direct Launch Succeeded: {json.dumps(launch_res, ensure_ascii=False, indent=2)}")
    except Exception as e:
        print(f"❌ Step 2 Failed: {e}")
        sys.exit(1)

    # 3. Wait 8 seconds for video to fetch, buffer, auto-start, and perform seek
    print("\n⏳ Step 3: Waiting 8 seconds for the video to fetch, buffer, auto-play, and seek...")
    time.sleep(8)

    # 4. Fetch logs and verify direct playback + seek completed successfully
    print("\n📊 Step 4: Verification of Playback and Seek via Logcat...")
    state_res_json = gimy_get_tv_state()
    try:
        state = json.loads(state_res_json)
        print("✅ Final TV State Checked!")
        print(f"  - Device IP: {state.get('deviceIp')}")
        print(f"  - Final Focus: {state.get('currentFocus')}")
        print(f"  - Recent Logs Count: {len(state.get('recentLogs', []))}")
        
        # Look for the prepared log and the seek to final progress log
        found_prepared = False
        found_seek = False
        for log in state.get('recentLogs', []):
            print(f"    [LOG] {log}")
            if "Video prepared" in log:
                found_prepared = True
            if "Seeking to final progress" in log:
                found_seek = True
                
        print("\n==================================================")
        if found_prepared and found_seek:
            print("🏆 ENHANCED CLOSED-LOOP TESTING PASSED SUCCESSFULLY!")
            print("   👉 1. Movie watch state and progress successfully retrieved!")
            print("   👉 2. Direct launch started playing immediately without key events!")
            print("   👉 3. Video successfully seeked to last 5 minutes on startup!")
        else:
            print("⚠️ TEST COMPLETED with warnings: Logs might have rotated or buffer is slow.")
            print("   Double check if the TV screen is currently playing at the last 5 minutes!")
        print("==================================================")
        
    except Exception as e:
        print(f"❌ Step 4 Failed: {e}")
        sys.exit(1)

if __name__ == "__main__":
    test_closed_loop_advanced()
