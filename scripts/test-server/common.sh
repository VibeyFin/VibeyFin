# Shared settings for the local test server scripts. Sourced, not executed.
JF_IMAGE="docker.io/jellyfin/jellyfin:12"
JF_NAME="vibeyfin-jellyfin"
JF_PORT="8196"
JF_URL="http://localhost:${JF_PORT}"
# Throwaway test credentials for a local, disposable server. Not a secret.
JF_USER="admin"
JF_PASS="vibeyfin-test"
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DATA="${DIR}/data"
AUTH_HDR='Authorization: MediaBrowser Client="VibeyFin Test", Device="script", DeviceId="vibeyfin-test-script", Version="1.0"'
# A second throwaway user the seed script creates with downloading switched off, for the "no permission" case.
JF_NODL_USER="nodownload"
JF_NODL_PASS="vibeyfin-test"
# Seerr, linked to the Jellyfin above (seerr.sh). 5155, not Seerr's usual 5055, so a real Seerr can run alongside.
SEERR_IMAGE="ghcr.io/seerr-team/seerr:v3.5.1"
SEERR_NAME="vibeyfin-seerr"
SEERR_PORT="5155"
SEERR_URL="http://localhost:${SEERR_PORT}"
# How the Seerr container reaches Jellyfin on the host.
SEERR_JF_HOST="host.containers.internal"
