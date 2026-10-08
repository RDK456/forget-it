#!/usr/bin/env bash
# Builds the signed release APKs, writes SHA256SUMS, adds delta patches from the previous release, and publishes a GitHub release.
# Usage: tools/release.sh [--dry-run]     (set versionName and versionCode in app/build.gradle.kts first)
# Needs: JAVA_HOME (JDK 17), keystore.properties, and the gh CLI logged in.
set -euo pipefail
shopt -s nullglob
REPO="RDK456/forget-it"
VERSION=$(grep -m1 'versionName' app/build.gradle.kts | sed -E 's/.*"([^"]+)".*/\1/')
OUT="build/release/$VERSION"
ROOT="$(pwd)"; command -v cygpath >/dev/null 2>&1 && ROOT="$(cygpath -m "$ROOT")"
rm -rf "$OUT" && mkdir -p "$OUT"

./gradlew assembleRelease
for abi in arm64-v8a armeabi-v7a x86_64 universal; do
  cp "app/build/outputs/apk/release/app-$abi-release.apk" "$OUT/forget-it-$VERSION-$abi.apk"
done
(cd "$OUT" && sha256sum forget-it-*.apk > SHA256SUMS)

# Delta patches: one per APK of the previous release, named for the checksum of the APK it patches.
PREV=$(gh release list --repo "$REPO" --limit 1 --json tagName --jq '.[0].tagName' 2>/dev/null || true)
if [ -n "${PREV:-}" ] && [ "$PREV" != "v$VERSION" ]; then
  mkdir -p "$OUT/prev"
  gh release download "$PREV" --repo "$REPO" --pattern '*.apk' --dir "$OUT/prev"
  for abi in arm64-v8a armeabi-v7a x86_64; do   # the universal APK is too large to diff comfortably; it is downloaded whole
    old=$(ls "$OUT"/prev/*-"$abi".apk 2>/dev/null | head -1 || true)
    new="$OUT/forget-it-$VERSION-$abi.apk"
    [ -n "$old" ] && [ -f "$new" ] || continue
    sha12=$(sha256sum "$old" | cut -c1-12)
    ./gradlew :app:makeDelta -q -PoldApk="$ROOT/$old" -PnewApk="$ROOT/$new" -Ppatch="$ROOT/$OUT/delta-$sha12-to-$(basename "$new").patch"
  done
  rm -rf "$OUT/prev"
fi

ls -la "$OUT"
if [ "${1:-}" = "--dry-run" ]; then echo "dry run: nothing published"; exit 0; fi

NOTES="${NOTES_FILE:-}"
if [ -n "$NOTES" ]; then
  gh release create "v$VERSION" "$OUT"/*.apk "$OUT"/SHA256SUMS "$OUT"/delta-*.patch --repo "$REPO" --title "Forget-it $VERSION" --notes-file "$NOTES"
else
  gh release create "v$VERSION" "$OUT"/*.apk "$OUT"/SHA256SUMS "$OUT"/delta-*.patch --repo "$REPO" --title "Forget-it $VERSION" --generate-notes
fi
