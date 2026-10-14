"""Reproducible, secret-safe TDLib Android build prerequisites (no writes to SDK)."""
from __future__ import annotations
import json
from pathlib import Path
import shutil
import subprocess
import sys

SDK = Path("C:/Android/Sdk")
NATIVE = SDK / "ndk" / "28.2.13676358"
CMAKE = SDK / "cmake" / "3.22.1" / "bin" / "cmake.exe"
SRC = Path("C:/Bots/_deps/tdlib-e91-src")
API_ENCRYPTED = Path("C:/Bots/_secrets/melovault-e91-telegram-api/telegram_api.dpapi")
PINNED_COMMIT = "c15d3f5a5de6e3ba5839822c451152e5e18bb700"

def check() -> dict:
    result = {
        "tdlib_upstream": "https://github.com/tdlib/td.git",
        "expected_commit": PINNED_COMMIT,
        "ndk_version": "28.2.13676358",
        "cmake_version": "3.22.1",
        "source_directory_exists": (SRC / ".git").exists(),
        "ndk_installed": (NATIVE / "source.properties").exists(),
        "cmake_installed": CMAKE.is_file(),
        "local_api_credentials_encrypted": API_ENCRYPTED.is_file(),
        "native_library_included_in_android_app": False,
        "telegram_login_implemented": False,
    }
    if result["source_directory_exists"]:
        current = subprocess.run(
            ["git", "-C", str(SRC), "rev-parse", "HEAD"],
            capture_output=True, text=True, timeout=10,
        )
        result["git_commit_matches_pin"] = (
            current.returncode == 0 and current.stdout.strip() == PINNED_COMMIT
        )
    else:
        result["git_commit_matches_pin"] = False

    # These must be available in the actual shell that invokes the TDLib build,
    # not merely installed somewhere under Windows.
    result["host_tools_available_in_parent_path"] = {
        name: shutil.which(name) is not None
        for name in ("gperf", "php", "make", "perl", "jar", "java", "bash", "ninja")
    }
    result["host_toolchain_ready"] = all(
        result["host_tools_available_in_parent_path"].values()
    )
    result["ready_to_build_android_tdlib"] = all([
        result["git_commit_matches_pin"],
        result["ndk_installed"],
        result["cmake_installed"],
        result["host_toolchain_ready"],
    ])
    return result

if __name__ == "__main__":
    result = check()
    print(json.dumps(result, indent=2))
    sys.exit(0 if result["ready_to_build_android_tdlib"] else 2)
