# Shared settings for the local test server scripts. Sourced, not executed.
JF_IMAGE="docker.io/jellyfin/jellyfin:12.2.20261005-225228"
JF_NAME="vibeyfin-jellyfin"
JF_PORT="8196"
JF_URL="http://localhost:${JF_PORT}"
# Throwaway test credentials for a local, disposable server. Not a secret.
JF_USER="admin"
JF_PASS="vibeyfin-test"
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DATA="${DIR}/data"
AUTH_HDR='Authorization: MediaBrowser Client="VibeyFin Test", Device="script", DeviceId="vibeyfin-test-script", Version="1.0"'
