import subprocess
import time
import os
import struct
import sys

DEVICE_ID = "QV725WEQ54"
OUT_DIR = r"Z:\MusicAssistant\AgentBridge\artifacts\e91-shareable\screenshots_e93_01e"

def run(cmd):
    res = subprocess.run(cmd, shell=True, capture_output=True, text=True)
    if res.returncode != 0 and res.stderr:
        print("WARN:", res.stderr.strip())
    return res.stdout.strip()

def adb_shell(cmd):
    return run(f'adb -s {DEVICE_ID} shell "{cmd}"')

def capture(name, expected_wh=None):
    path = os.path.join(OUT_DIR, name)
    print(f"\nCapturing screenshot: {name}")
    with open(path, "wb") as f:
        subprocess.run(["adb", "-s", DEVICE_ID, "exec-out", "screencap", "-p"], stdout=f)
    size = os.path.getsize(path)
    with open(path, "rb") as f:
        head = f.read(24)
    if head.startswith(b"\x89PNG\r\n\x1a\n"):
        w, h = struct.unpack(">II", head[16:24])
    else:
        w, h = 0, 0
    rot = adb_shell("dumpsys window | grep -i mCurrentRotation")
    print(f"Captured {name}: {w}x{h} ({size} bytes) | {rot}")
    if expected_wh:
        if (w, h) != expected_wh:
            print(f"ERROR: Expected {expected_wh[0]}x{expected_wh[1]}, but captured {w}x{h}!")
            raise AssertionError(f"Geometry mismatch for {name}: expected {expected_wh}, got {(w, h)}")
        else:
            print(f"PASS: Verified geometry matches expected {w}x{h}")
    return w, h, size

def restore():
    print("\nRESTORE: Resetting display to native physical 1080x2520, 420dpi, auto-rotation=1, user_rotation=0...")
    adb_shell("wm size reset")
    adb_shell("wm density reset")
    adb_shell("settings put system accelerometer_rotation 1")
    adb_shell("settings put system user_rotation 0")
    time.sleep(2)

def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    try:
        # Step 0: Ensure native physical portrait 1080x2520 @420dpi, lock rotation
        restore()
        adb_shell("settings put system accelerometer_rotation 0")
        adb_shell("settings put system user_rotation 0")
        adb_shell("am start -n app.melovault.player/com.bsdung.musicassistantcar.data.telegram.TelegramPreviewActivity")
        time.sleep(3)

        # Ensure track 0 is playing so mini-player has live playing state
        adb_shell("input tap 540 560")
        time.sleep(2)

        # 01: Native portrait main library with playing mini-player & thumbnail rows
        capture("01_sony_portrait_edge_pro_playing.png", expected_wh=(1080, 2520))

        # 02: Open full screen Now Playing dialog
        # Tap '▶ Đang phát' at center (924, 2317)
        adb_shell("input tap 924 2317")
        time.sleep(2)
        capture("02_sony_portrait_nowplaying_chevron.png", expected_wh=(1080, 2520))

        # 03: Open Queue dialog from full player
        # Tap queue button (center bottom right of full player, ~780, 2160)
        adb_shell("input tap 780 2160")
        time.sleep(2)
        capture("03_sony_portrait_queue.png", expected_wh=(1080, 2520))
        # Close queue dialog by back key
        adb_shell("input keyevent KEYCODE_BACK")
        time.sleep(1)
        # Close full player by tapping the top-left chevron close button (X=100, Y=140)
        adb_shell("input tap 100 140")
        time.sleep(1)

        # 04: Sources tab (tap '☷ Nguồn' at center 411, 2317)
        adb_shell("input tap 411 2317")
        time.sleep(2)
        capture("04_sony_portrait_sources.png", expected_wh=(1080, 2520))

        # 05: Playlist tab (tap '☆ Playlist' at center 667, 2317)
        adb_shell("input tap 667 2317")
        time.sleep(2)
        capture("05_sony_portrait_playlist.png", expected_wh=(1080, 2520))

        # Switch back to library (tap '♫ Thư viện' at center 155, 2317)
        adb_shell("input tap 155 2317")
        time.sleep(1)

        # 06: Search / IME keyboard active
        # Tap search box near top (540, 400)
        adb_shell("input tap 540 400")
        time.sleep(2)
        capture("06_sony_portrait_search_ime.png", expected_wh=(1080, 2520))
        # Hide keyboard cleanly
        adb_shell("input keyevent KEYCODE_BACK")
        time.sleep(1)

        # 07: Sony native landscape (2520x1080 @420dpi) - Wide navigation before scroll
        adb_shell("settings put system user_rotation 1")
        time.sleep(3)
        capture("07_sony_landscape_wide_nav_before_scroll.png", expected_wh=(2520, 1080))

        # 08: Scroll song list upward in landscape to verify top nav DOES NOT auto-collapse
        adb_shell("input swipe 1260 700 1260 300 300")
        time.sleep(2)
        capture("08_sony_landscape_wide_nav_after_scroll.png", expected_wh=(2520, 1080))

        # 09: Open NowPlaying in landscape to verify top-left chevron down button
        # Tap 'Đang phát' tab at center (2070, 246) or mini-player at (1000, 1000)
        adb_shell("input tap 2070 246")
        time.sleep(2)
        capture("09_sony_landscape_nowplaying_chevron.png", expected_wh=(2520, 1080))

        # Test closing NowPlaying using top-left chevron button (X=120, Y=116) or back key
        adb_shell("input tap 120 116")
        time.sleep(1)
        # Fallback back key to ensure dialog is dismissed before next steps
        adb_shell("input keyevent KEYCODE_BACK")
        time.sleep(1)

        # Reset rotation back to portrait before wm size overrides
        adb_shell("settings put system user_rotation 0")
        time.sleep(2)

        # 10: FYT 2/3 proxy (1096x1027 @240)
        adb_shell("wm size 1096x1027")
        adb_shell("wm density 240")
        adb_shell("settings put system user_rotation 0")
        time.sleep(3)
        capture("10_fyt_2_3_proxy_1096x1027_240dpi.png", expected_wh=(1096, 1027))

        # 11: FYT 1/2 proxy (960x1200 @240)
        adb_shell("wm size 960x1200")
        adb_shell("wm density 240")
        adb_shell("settings put system user_rotation 0")
        time.sleep(3)
        capture("11_fyt_1_2_proxy_960x1200_240dpi.png", expected_wh=(960, 1200))

        # 12: FYT 1/3 proxy (640x1200 @240)
        adb_shell("wm size 640x1200")
        adb_shell("wm density 240")
        adb_shell("settings put system user_rotation 0")
        time.sleep(3)
        capture("12_fyt_1_3_proxy_640x1200_240dpi.png", expected_wh=(640, 1200))

        # 13: Short landscape proxy (1200x640 requested @240dpi -> Sony physical panel clamps to 1096x584 @ 1.876 aspect ratio)
        adb_shell("wm size 1200x640")
        adb_shell("wm density 240")
        adb_shell("settings put system user_rotation 0")
        time.sleep(3)
        capture("13_short_landscape_1200x640_240dpi.png", expected_wh=(1096, 584))

        print("\nQA screenshot collection complete! All 13 screenshots verified.")

    finally:
        restore()

if __name__ == "__main__":
    main()
