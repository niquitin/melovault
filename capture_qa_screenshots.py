import subprocess
import time
import os
import sys

ADB = ["adb", "-s", "QV725WEQ54"]
OUT_DIR = r"Z:\MusicAssistant\AgentBridge\artifacts\e91-shareable\screenshots_e93_01"

def run(cmd):
    print("RUN:", cmd)
    res = subprocess.run(cmd, shell=True, capture_output=True, text=True)
    if res.returncode != 0:
        print("WARN:", res.stderr.strip())
    return res.stdout.strip()

def adb_shell(cmd):
    return run(f"adb -s QV725WEQ54 shell \"{cmd}\"")

def capture(name):
    path = os.path.join(OUT_DIR, name)
    print(f"Capturing screenshot: {path}")
    with open(path, "wb") as f:
        res = subprocess.run(["adb", "-s", "QV725WEQ54", "exec-out", "screencap", "-p"], stdout=f)
    size = os.path.getsize(path)
    print(f"Captured {name}: {size} bytes")
    return size

def restore():
    print("RESTORE: Resetting display to native physical size and density...")
    adb_shell("wm size reset")
    adb_shell("wm density reset")
    adb_shell("settings put system accelerometer_rotation 1")
    adb_shell("settings put system user_rotation 0")
    time.sleep(2)

def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    try:
        # Step 1: Ensure native portrait 1080x2520 @420dpi
        restore()
        adb_shell("settings put system accelerometer_rotation 0")
        adb_shell("settings put system user_rotation 0")
        adb_shell("am start -n app.melovault.player/com.bsdung.musicassistantcar.data.telegram.TelegramPreviewActivity")
        time.sleep(3)

        # 01: Native portrait main library with playing mini-player
        capture("01_sony_portrait_edge_pro_playing.png")

        # 02: Open full screen Now Playing dialog (tap mini-player or tab 'Đang phát')
        # Tap tab 'Đang phát' at bounds [801,2257][1048,2378] -> center (924, 2317)
        adb_shell("input tap 924 2317")
        time.sleep(2)
        capture("02_sony_portrait_full_player.png")

        # 03: Open Queue dialog from full player
        # In NowPlaying, 'Hàng đợi' button is in extras row near bottom
        # Let's tap 'Hàng đợi' or call showUpcomingQueue
        # Let's find queue button coordinates or tap center right near bottom
        adb_shell("uiautomator dump /sdcard/ui.xml")
        res = adb_shell("grep -o 'bounds=\"\\[[0-9]*,[0-9]*\\]\\[[0-9]*,[0-9]*\\]\"' /sdcard/ui.xml | tail -n 10")
        print("Full player dump bounds:", res)
        # Tap queue button (approx center bottom right)
        adb_shell("input tap 780 2160")
        time.sleep(2)
        capture("03_sony_portrait_queue.png")
        # Close queue dialog by tapping "Xong" (approx top right)
        adb_shell("input tap 950 160")
        time.sleep(1)
        # Close full player by tapping "⌄" (approx top left)
        adb_shell("input tap 100 160")
        time.sleep(1)

        # 04: Sources tab (tap '☷ Nguồn' at [288,2257][534,2378] -> center 411, 2317)
        adb_shell("input tap 411 2317")
        time.sleep(2)
        capture("04_sony_portrait_sources.png")

        # 05: Playlist tab (tap '☆ Playlist' at [544,2257][791,2378] -> center 667, 2317)
        adb_shell("input tap 667 2317")
        time.sleep(2)
        capture("05_sony_portrait_playlist.png")

        # Switch back to library (tap '♫ Thư viện' at center 155, 2317)
        adb_shell("input tap 155 2317")
        time.sleep(1)

        # 06: Search / IME keyboard active
        # Tap search box near top [16, 380][1064, 480] -> center 540, 430
        adb_shell("input tap 540 430")
        time.sleep(2)
        capture("06_sony_portrait_search_ime.png")
        # Hide keyboard
        adb_shell("input keyevent KEYCODE_BACK")
        time.sleep(1)

        # 07: Overflow menu showing theme switcher
        # Overflow button is at top right: bounds [975, 100][1064, 200] -> center 1020, 150
        adb_shell("input tap 1020 150")
        time.sleep(2)
        capture("07_sony_portrait_overflow_theme.png")
        # Close overflow by tapping outside
        adb_shell("input tap 500 1000")
        time.sleep(1)

        # 08: Sony native landscape (2520x1080 @420dpi)
        adb_shell("settings put system user_rotation 1")
        time.sleep(3)
        capture("08_sony_landscape_wide.png")

        # 09: FYT 2/3 proxy (1096x1027 @240)
        adb_shell("wm size 1096x1027")
        adb_shell("wm density 240")
        adb_shell("settings put system user_rotation 0")
        time.sleep(3)
        capture("09_fyt_2_3_proxy_1096x1027_240dpi.png")

        # 10: FYT 1/2 proxy (960x1200 @240)
        adb_shell("wm size 960x1200")
        adb_shell("wm density 240")
        time.sleep(3)
        capture("10_fyt_1_2_proxy_960x1200_240dpi.png")

        # 11: FYT 1/3 proxy (640x1200 @240)
        adb_shell("wm size 640x1200")
        adb_shell("wm density 240")
        time.sleep(3)
        capture("11_fyt_1_3_proxy_640x1200_240dpi.png")

        print("QA screenshot collection complete!")

    finally:
        restore()

if __name__ == "__main__":
    main()
