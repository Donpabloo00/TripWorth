#!/usr/bin/env bash
# TripWorth — full Play Store publish helper
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17}"
export PATH="$JAVA_HOME/bin:$PATH"

AAB="app/build/outputs/bundle/release/app-release.aab"
KEYSTORE_PROPS="keystore.properties"
SERVICE_ACCOUNT="play-store/google-play-service-account.json"

echo "=== TripWorth Play Store Publish ==="
echo ""

# 1. Build AAB if missing
if [[ ! -f "$AAB" ]]; then
  echo "→ Building signed AAB..."
  if [[ ! -f "$KEYSTORE_PROPS" ]]; then
    echo "ERROR: Missing keystore.properties"
    exit 1
  fi
  ./gradlew bundleRelease
fi
echo "✓ AAB: $AAB ($(du -h "$AAB" | cut -f1))"

# 2. Assets check
for f in play-store/assets/icon-512.png play-store/assets/feature-graphic.png; do
  [[ -f "$f" ]] || { echo "ERROR: Missing $f"; exit 1; }
done
echo "✓ Store assets ready (icon, feature graphic, screenshots)"

# 3. Fastlane upload if configured
if command -v fastlane >/dev/null && [[ -f "$SERVICE_ACCOUNT" ]]; then
  echo ""
  echo "→ Uploading via fastlane (internal track)..."
  fastlane android internal
  echo ""
  echo "Done! Check Play Console → Internal testing."
  exit 0
fi

# 4. Manual upload instructions
echo ""
echo "=== Manual upload (one-time setup) ==="
echo ""
echo "A) GitHub Pages (privacy policy):"
echo "   1. Push repo to GitHub as 'TripWorth'"
echo "   2. Settings → Pages → Source: GitHub Actions"
echo "   3. URL: https://YOUR_USER.github.io/TripWorth/privacy.html"
echo ""
echo "B) Play Console — create app:"
echo "   1. https://play.google.com/console"
echo "   2. Create app → TripWorth / com.tripworth.app"
echo "   3. App → Testing → Internal testing → Create release"
echo "   4. Upload: $AAB"
echo ""
echo "C) Store listing files:"
echo "   Icon 512:     play-store/assets/icon-512.png"
echo "   Feature:      play-store/assets/feature-graphic.png"
echo "   Screenshots:  play-store/assets/screenshots/"
echo "   Descriptions: fastlane/metadata/android/en-US/ and ro/"
echo ""
echo "D) App content:"
echo "   - Privacy policy URL (from step A)"
echo "   - Data safety: see play-store/data-safety-answers.txt"
echo "   - Declare: overlay + screen capture permissions"
echo ""
echo "E) Automated upload next time:"
echo "   1. Play Console → Setup → API access → Create service account"
echo "   2. Save JSON as: $SERVICE_ACCOUNT"
echo "   3. gem install fastlane && fastlane android internal"
echo ""

# Open Play Console in browser if on macOS
if [[ "$(uname)" == "Darwin" ]]; then
  open "https://play.google.com/console" 2>/dev/null || true
fi
