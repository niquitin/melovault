#!/usr/bin/env bash
# E9.1-C: isolated, reproducible TDLib native build for arm64-v8a.
# Source code: upstream tdlib/td pinned c15d3f5; no user credentials.
# The only upstream script modification is the OpenSSL ABI loop to arm64-v8a.
set -euo pipefail

export PATH="/c/Program Files/Eclipse Adoptium/jdk-17.0.17.10-hotspot/bin:/c/tools/php85:/c/Android/Sdk/cmake/3.22.1/bin:/ucrt64/bin:/usr/bin:$PATH"
export MSYSTEM=UCRT64
export OSTYPE=msys
# Windows CMake defaults to unavailable NMake. Use installed UCRT64 Ninja
# for native host code-generation and Android NDK cross-builds.
export CMAKE_GENERATOR=Ninja

TDLIB_SRC="/c/Bots/_deps/tdlib-e91-src"
SDK="/c/Android/Sdk"
NDK="28.2.13676358"
OPENSSL_TAG="openssl-3.5.4"
SSL_PREFIX="third-party/openssl-e91"
COMMIT="c15d3f5a5de6e3ba5839822c451152e5e18bb700"

cd "$TDLIB_SRC/example/android"
if [[ "$(git -C ../.. rev-parse HEAD)" != "$COMMIT" ]]; then
  echo "STOP: TDLib source revision mismatch" >&2; exit 31
fi
if [[ ! -f "$SDK/ndk/$NDK/source.properties" ]]; then
  echo "STOP: Android NDK missing" >&2; exit 32
fi
if [[ ! -x "$SDK/cmake/3.22.1/bin/cmake.exe" ]]; then
  echo "STOP: Android SDK CMake missing" >&2; exit 33
fi

# Source official preflight with MSYS2 OSTYPE normalized.
source ./check-environment.sh
echo "E91_HOST_TOOLCHAIN_PASS source=$COMMIT ndk=$NDK openssl=$OPENSSL_TAG"

if [[ ! -f "$SSL_PREFIX/arm64-v8a/lib/libssl.a" ]]; then
  if [[ -e "$SSL_PREFIX" ]]; then
    echo "STOP: incomplete OpenSSL build dir; inspect manually" >&2; exit 34
  fi
  cp -p build-openssl.sh .e91-build-openssl-arm64.sh
  sed -i 's/for ABI in arm64-v8a armeabi-v7a x86_64 x86 ; do/for ABI in arm64-v8a ; do/' .e91-build-openssl-arm64.sh
  grep -q '^for ABI in arm64-v8a ; do' .e91-build-openssl-arm64.sh
  # Dedicated clone directory only; private production files are never touched.
  OSTYPE=msys bash .e91-build-openssl-arm64.sh "$SDK" "$NDK" "$SSL_PREFIX" "$OPENSSL_TAG"
fi

if [[ ! -f "$SSL_PREFIX/arm64-v8a/lib/libssl.a" ]]; then
  echo "STOP: OpenSSL static library not produced" >&2; exit 35
fi
echo "E91_ARM64_OPENSSL_PASS"

# JSONJava uses upstream JsonClient and JNI tdjsonjava, avoids codegen for TdApi.
OSTYPE=msys bash ./build-tdlib.sh "$SDK" "$NDK" "$SSL_PREFIX" "c++_static" "JSONJava" "23" "arm64-v8a"
test -s tdlib/libs/arm64-v8a/libtdjsonjava.so
test -s tdlib/java/org/drinkless/tdlib/JsonClient.java
echo "E91_TDLIB_ARM64_JSONJAVA_PASS"
