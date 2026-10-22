#!/usr/bin/env bash
# Starts (or creates) the local Jellyfin test server under podman and makes it ready:
# media generated, first-run wizard done, libraries added with trickplay on.
# Idempotent: run it again any time. Server state lives in scripts/test-server/data (git-ignored).
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

mkdir -p "${DATA}/config" "${DATA}/cache" "${DATA}/media"
"${DIR}/make-media.sh"

if podman container exists "$JF_NAME"; then
  podman start "$JF_NAME" >/dev/null
else
  podman run -d --name "$JF_NAME" -p "${JF_PORT}:8096" \
    -v "${DATA}/config:/config" -v "${DATA}/cache:/cache" -v "${DATA}/media:/media" \
    "$JF_IMAGE" >/dev/null
fi

echo -n "Waiting for Jellyfin"
for _ in $(seq 1 90); do
  curl -sf "${JF_URL}/System/Info/Public" >/dev/null && break
  echo -n "."; sleep 2
done
echo
curl -sf "${JF_URL}/System/Info/Public" >/dev/null || { echo "Jellyfin did not come up"; exit 1; }

json() { curl -sf -H "$AUTH_HDR" -H 'Content-Type: application/json' "$@"; }
for _ in $(seq 1 30); do json "${JF_URL}/Startup/Configuration" >/dev/null 2>&1 && break; sleep 2; done

# First-run wizard, via the Startup API. Skipped once it is complete.
if [ "$(curl -s "${JF_URL}/System/Info/Public" | grep -o '"StartupWizardCompleted":[a-z]*')" != '"StartupWizardCompleted":true' ]; then
  json -X POST "${JF_URL}/Startup/Configuration" -d '{"UICulture":"en-US","MetadataCountryCode":"US","PreferredMetadataLanguage":"en"}' >/dev/null
  json "${JF_URL}/Startup/User" >/dev/null   # creates the initial user record
  json -X POST "${JF_URL}/Startup/User" -d "{\"Name\":\"${JF_USER}\",\"Password\":\"${JF_PASS}\"}" >/dev/null
  json -X POST "${JF_URL}/Startup/RemoteAccess" -d '{"EnableRemoteAccess":true,"EnableAutomaticPortMapping":false}' >/dev/null
  json -X POST "${JF_URL}/Startup/Complete" >/dev/null
fi

TOKEN="$(json -X POST "${JF_URL}/Users/AuthenticateByName" -d "{\"Username\":\"${JF_USER}\",\"Pw\":\"${JF_PASS}\"}" \
  | sed -E 's/.*"AccessToken":"([^"]+)".*/\1/')"
AUTHED=(-H "${AUTH_HDR}, Token=\"${TOKEN}\"")

# Chapter Segments Provider plugin: turns chapters named Intro/Credits/... into media segments.
# 12.2.0 ships no segment provider of its own. A plugin install needs a server restart.
if ! curl -sf "${AUTHED[@]}" "${JF_URL}/Plugins" | grep -q '"Name":"Chapter Segments Provider"'; then
  curl -sf "${AUTHED[@]}" -X POST \
    "${JF_URL}/Packages/Installed/Chapter%20Segments%20Provider?assemblyGuid=698b6f3314ca49b59d79fc3c0ab941f5&version=5.0.0.0" >/dev/null
  sleep 3
  podman restart "$JF_NAME" >/dev/null
  echo -n "Restarting for plugin"
  for _ in $(seq 1 90); do curl -sf "${JF_URL}/System/Info/Public" >/dev/null && break; echo -n "."; sleep 2; done
  echo
fi

# Libraries, with trickplay generation on (off for Failures, so those items have none).
OPTS='{"LibraryOptions":{"EnableTrickplayImageExtraction":true,"ExtractTrickplayImagesDuringLibraryScan":true,"SaveTrickplayWithMedia":false,"EnableChapterImageExtraction":false,"EnableRealtimeMonitor":false}}'
EXISTING="$(curl -sf "${AUTHED[@]}" "${JF_URL}/Library/VirtualFolders")"
NO_TRICKPLAY='{"LibraryOptions":{"EnableTrickplayImageExtraction":false,"ExtractTrickplayImagesDuringLibraryScan":false,"EnableChapterImageExtraction":false,"EnableRealtimeMonitor":false}}'
add_lib() { # name, type, path, options
  echo "$EXISTING" | grep -q "\"Name\":\"$1\"" && return 0
  curl -sf "${AUTHED[@]}" -H 'Content-Type: application/json' -X POST \
    "${JF_URL}/Library/VirtualFolders?name=$1&collectionType=$2&paths=$3&refreshLibrary=false" -d "$4" >/dev/null
}
add_lib Shows tvshows /media/shows "$OPTS"
add_lib Movies movies /media/movies "$OPTS"
add_lib Failures movies /media/failures "$NO_TRICKPLAY"
curl -sf "${AUTHED[@]}" -X POST "${JF_URL}/Library/Refresh" >/dev/null || true

# Wait for the scan to find the items (bounded), then run the trickplay and segment tasks now
# instead of waiting for their schedules.
for _ in $(seq 1 60); do
  N="$(curl -sf "${AUTHED[@]}" "${JF_URL}/Items?Recursive=true&IncludeItemTypes=Episode,Movie" | grep -o '"TotalRecordCount":[0-9]*' | grep -o '[0-9]*' || echo 0)"
  [ "${N:-0}" -ge "$EXPECTED_ITEMS" ] && break
  sleep 2
done
for task in "Generate Trickplay Images" "Media Segment Scan"; do
  ID="$(curl -sf "${AUTHED[@]}" "${JF_URL}/ScheduledTasks" | python3 -I -c "
import sys, json
print(next(t['Id'] for t in json.load(sys.stdin) if t['Name'] == '$task'))")"
  curl -sf "${AUTHED[@]}" -X POST "${JF_URL}/ScheduledTasks/Running/${ID}" >/dev/null || true
done

echo "Jellyfin test server: ${JF_URL}  user=${JF_USER}  password=${JF_PASS}"
echo "Libraries scan in the background; trickplay and segments follow (see docs/test-server.md)."
