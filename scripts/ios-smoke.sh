#!/bin/sh
# iOS smoke test: builds the app, installs it fresh on a simulator and runs the XCUITest onboarding
# flow (launch, add server, who's watching, sign in) against the local test server.
#
#   scripts/ios-smoke.sh                       # default simulator "iPhone 17"
#   IOS_SIMULATOR="iPhone 17e" scripts/ios-smoke.sh
#   TEST_RUNNER_VIBEYFIN_TEST_SERVER=host:port scripts/ios-smoke.sh   # other server, see the test file
set -eu
cd "$(dirname "$0")/.."

SIMULATOR="${IOS_SIMULATOR:-iPhone 17}"
BUNDLE_ID="app.vibeyfin"
DERIVED="${IOS_DERIVED_DATA:-build/ios-smoke}"
RESULTS="$DERIVED/results.xcresult"

# The test server must answer on localhost:8196 (the simulator shares the host network).
if ! curl -sf http://localhost:8196/System/Info/Public >/dev/null; then
  echo "Starting the local test server"
  scripts/test-server/start.sh
fi

# The test expects a first run: remove the app so no servers or profiles are left over.
xcrun simctl boot "$SIMULATOR" 2>/dev/null || true
xcrun simctl bootstatus "$SIMULATOR" -b >/dev/null
xcrun simctl uninstall "$SIMULATOR" "$BUNDLE_ID" 2>/dev/null || true

rm -rf "$RESULTS"
xcodebuild test \
  -project iosApp/iosApp.xcodeproj \
  -scheme iosAppUITests \
  -destination "platform=iOS Simulator,name=$SIMULATOR" \
  -derivedDataPath "$DERIVED" \
  -resultBundlePath "$RESULTS"

echo "iOS smoke test passed. Screenshots per step: xcrun xcresulttool or open $RESULTS in Xcode."
