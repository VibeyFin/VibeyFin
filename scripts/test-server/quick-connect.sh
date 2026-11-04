#!/usr/bin/env bash
# Quick Connect on the local test server, for walking the sign-in flow.
#   quick-connect.sh approve <code> [user]   approves a code the app shows, as [user] (default: the admin user)
#   quick-connect.sh on | off                turns Quick Connect on or off for the whole server
#   quick-connect.sh start                   plays the other device: prints a code for the app to approve and waits for it
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"
export JF_URL JF_USER JF_PASS AUTH_HDR
export QC_ACTION="${1:-}" QC_CODE="${2:-}" QC_AS="${3:-}"

exec python3 -I - <<'PY'
import json, os, sys, time, urllib.parse, urllib.request

URL, USER, PASS = os.environ["JF_URL"], os.environ["JF_USER"], os.environ["JF_PASS"]
AUTH = os.environ["AUTH_HDR"].split(": ", 1)[1]
ACTION, CODE, AS = os.environ["QC_ACTION"], os.environ["QC_CODE"], os.environ["QC_AS"] or USER


def call(path, token=None, body=None, method=None):
    headers = {"Authorization": AUTH + (f', Token="{token}"' if token else ""), "Content-Type": "application/json"}
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(URL + path, data=data, headers=headers, method=method)
    with urllib.request.urlopen(req, timeout=30) as r:
        raw = r.read()
        return json.loads(raw) if raw else None


if ACTION == "start":
    # A device of its own, so the app's sign-in is not mistaken for it
    AUTH = AUTH.replace('DeviceId="vibeyfin-test-script"', 'DeviceId="vibeyfin-test-other-device"')
    started = call("/QuickConnect/Initiate", method="POST")
    print(f"code {started['Code'][:3]} {started['Code'][3:]}: approve it in the app (Settings > Quick Connect); waiting up to 5 minutes", flush=True)
    for _ in range(150):
        if call("/QuickConnect/Connect?" + urllib.parse.urlencode({"secret": started["Secret"]}))["Authenticated"]:
            done = call("/Users/AuthenticateWithQuickConnect", body={"Secret": started["Secret"]})
            sys.exit(print(f"approved: this device is signed in as {done['User']['Name']}"))
        time.sleep(2)
    sys.exit("not approved in time")

token = call("/Users/AuthenticateByName", body={"Username": USER, "Pw": PASS})["AccessToken"]

if ACTION == "approve" and CODE:
    user = next((u for u in call("/Users", token) if u["Name"] == AS), None)
    if user is None:
        sys.exit(f"no user named {AS}")
    query = urllib.parse.urlencode({"code": CODE.replace(" ", ""), "userId": user["Id"]})
    call(f"/QuickConnect/Authorize?{query}", token, method="POST")
    print(f"approved {CODE} as {AS}")
elif ACTION in ("on", "off"):
    config = call("/System/Configuration", token)
    config["QuickConnectAvailable"] = ACTION == "on"
    call("/System/Configuration", token, body=config, method="POST")
    print(f"Quick Connect {ACTION}")
else:
    sys.exit("usage: quick-connect.sh approve <code> [user] | on | off | start")
PY
