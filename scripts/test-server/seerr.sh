#!/usr/bin/env bash
# Starts (or creates) a Seerr linked to the local Jellyfin test server and makes it ready: setup done as the
# Jellyfin admin, the Shows and Movies libraries enabled, a library scan started. Run by start.sh after Jellyfin
# is up (SEERR=0 skips it); idempotent. Seerr looks titles up on TMDB, so it needs an internet connection.
# State lives in scripts/test-server/data/seerr (git-ignored).
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

mkdir -p "${DATA}/seerr"
if podman container exists "$SEERR_NAME"; then
  podman start "$SEERR_NAME" >/dev/null
else
  # Runs as root inside the container: the mounted folder belongs to the host user, not to Seerr's `node` user.
  podman run -d --name "$SEERR_NAME" --init --user 0 -p "${SEERR_PORT}:5055" \
    -v "${DATA}/seerr:/app/config" -e LOG_LEVEL=info "$SEERR_IMAGE" >/dev/null
fi

echo -n "Waiting for Seerr"
for _ in $(seq 1 90); do
  curl -sf "${SEERR_URL}/api/v1/settings/public" >/dev/null && break
  echo -n "."; sleep 2
done
echo
PUBLIC="$(curl -sf "${SEERR_URL}/api/v1/settings/public")" || { echo "Seerr did not come up"; exit 1; }

JAR="$(mktemp)"
trap 'rm -f "$JAR"' EXIT
seerr() { curl -sf -b "$JAR" -c "$JAR" -H 'Content-Type: application/json' "$@"; }

sign_in() { seerr -X POST "${SEERR_URL}/api/v1/auth/jellyfin" -d "{\"username\":\"${JF_USER}\",\"password\":\"${JF_PASS}\"$1}" >/dev/null; }
if ! echo "$PUBLIC" | grep -q '"initialized":true'; then
  # Setup: the first Jellyfin sign-in links Seerr to the server and makes that user Seerr's admin. Once the server is
  # linked Seerr refuses those fields, so a setup that stopped half way signs in plainly.
  sign_in ",\"hostname\":\"${SEERR_JF_HOST}\",\"port\":${JF_PORT},\"useSsl\":false,\"urlBase\":\"\",\"email\":\"admin@vibeyfin.test\",\"serverType\":2" || sign_in ""
  LIBS="$(seerr -X POST "${SEERR_URL}/api/v1/settings/jellyfin/library/sync")"
  for id in $(echo "$LIBS" | python3 -I -c "
import sys, json
print(' '.join(l['id'] for l in json.load(sys.stdin) if l['name'] in ('Shows', 'Movies')))"); do
    seerr -X PUT "${SEERR_URL}/api/v1/settings/jellyfin/library/${id}" -d '{"enabled":true}' >/dev/null
  done
  seerr -X POST "${SEERR_URL}/api/v1/settings/initialize" >/dev/null
else
  sign_in ""
fi
# A full scan matches the library to TMDB, so the Seerr fixtures show as available.
seerr -X POST "${SEERR_URL}/api/v1/settings/jobs/jellyfin-full-scan/run" >/dev/null || true

echo "Seerr test server: ${SEERR_URL}  (sign in with the Jellyfin users; from the Android emulator use http://10.0.2.2:${SEERR_PORT})"
