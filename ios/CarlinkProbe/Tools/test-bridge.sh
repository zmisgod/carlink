#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/../../.."
probe_temp=$(mktemp -d)
trap 'rm -rf "$probe_temp"' EXIT
swiftc -parse-as-library ios/CarlinkProbe/Sources/BridgeClient.swift \
  ios/CarlinkProbe/Tools/ProbeCLI.swift -o "$probe_temp/probe-cli"
export CARLINK_PROBE_CLIENT="$probe_temp/probe-cli"
if [ -z "${JAVA_HOME:-}" ] && [ -d '/Applications/Android Studio.app/Contents/jbr/Contents/Home' ]; then
  export JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home'
fi
# Force execution: Gradle does not track the optional external client executable as an input.
./gradlew :app:testSideloadDebugUnitTest --tests com.carlink.bridge.ProbeServerTest --rerun-tasks --console=plain
