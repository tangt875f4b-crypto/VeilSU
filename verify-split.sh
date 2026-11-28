#!/usr/bin/env bash
# verify-split.sh — acceptance check for per-arch split APKs.
# Usage: bash verify-split.sh [dist-dir]   (default: dist)
# Fails (exit 1) on: wrong file count, wrong name, lib/ containing more
# than one arch, missing ksud entry (>= 1 MB .so), bad signature.
# Requires: unzip, apksigner (Android SDK build-tools) on PATH or in SDK.
set -eu
DIST="${1:-dist}"
ARCHES="arm64-v8a armeabi-v7a x86_64 riscv64"

# Same shape as Downloader.kt CI pattern:
# ^VeilSU_.+_<versionCode>-<abi>-release\.apk$
CI_RE='^VeilSU_.+_[0-9]+-(arm64-v8a|armeabi-v7a|x86_64|riscv64)-release\.apk$'

echo "== files in $DIST =="
ls -la "$DIST"/*.apk
COUNT=$(ls "$DIST"/*.apk | wc -l)
[ "$COUNT" -eq 4 ] || { echo "[FAIL] expected 4 APKs, found $COUNT"; exit 1; }

for arch in $ARCHES; do
  apk=$(ls "$DIST"/*-"$arch"-release.apk 2>/dev/null | head -n 1)
  [ -n "$apk" ] || { echo "[FAIL] no APK for $arch"; exit 1; }
  name=$(basename "$apk")
  echo "$name" | grep -Eq "$CI_RE" || { echo "[FAIL] $name does not match Downloader CI pattern"; exit 1; }

  # lib/ must contain exactly this one arch
  lib_arches=$(unzip -l "$apk" | awk '{print $4}' | grep '^lib/' | cut -d/ -f2 | sort -u | tr '\n' ' ')
  [ "$lib_arches" = "$arch " ] || { echo "[FAIL] $name lib arches: [$lib_arches] (want [$arch])"; exit 1; }

  # ksud entry: a >= 1 MB .so under lib/<arch>/ (name is randomized per build)
  ksud=$(unzip -l "$apk" | awk -v a="$arch" '$1 >= 1000000 && $4 ~ "^lib/"a"/.*\\.so$" {print $4}')
  [ -n "$ksud" ] || { echo "[FAIL] $name has no >=1MB ksud .so for $arch"; exit 1; }

  apksigner verify --verbose "$apk" | grep -Eq 'Verified using v2 scheme \(APK Signature Scheme v2\): true' \
    || { echo "[FAIL] $name apksigner v2 verify failed"; exit 1; }
  echo "[OK] $name  lib=[$lib_arches] ksud=$ksud size=$(stat -c%s "$apk") bytes"
done

echo "== size table =="
ls -la "$DIST"/*.apk
echo "[OK] all 4 split APKs verified"
