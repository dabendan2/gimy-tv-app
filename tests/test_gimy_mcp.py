#!/usr/bin/env python3
import sys
import os
import json

# Add scripts directory to path
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "../scripts")))

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
