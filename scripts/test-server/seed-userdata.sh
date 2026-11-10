#!/usr/bin/env bash
# Seeds user data and the box set for the page fixtures, through the Jellyfin API as the admin test user.
# Idempotent: every run sets the same state (it does not toggle). Run it after the library scan has found the
# fixtures from make-media.sh (start.sh does the scan; this script waits for the items, bounded).
#   Collection  "Crown Collection" (BoxSet): Starlit Crown, Starlit Crown II (created once, by name)
#   Watched     Saga Show S01E01, Bare Item
#   Resume      Saga Show S01E02 at 1.2 s of 3 s (40 %), Neon Night at 2 s of 5 s
#   My List     Saga Show, Neon Night, Starlit Crown, Solo Season
#   Kids user   "kids" (password vibeyfin-kids): MaxParentalRating 10 (PG), blocked tag no-kids, schedule Everyday 0-24 h
#   User        "nodownload" (a second user, downloading switched off), created once and kept that way
#   User        "nopassword" (no password: a tile that signs in without a sheet)
#   User        "hidden" (password vibeyfin-test, hidden from the sign-in screen: only "Other user" reaches it)
#   Sign-in     every other user is listed on the sign-in screen (Jellyfin hides new users by default)
#   Cleared     everything else among these fixtures (not played, no resume, not a favourite)
# The expectations are the "Page fixtures" section of docs/test-server.md.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"
export JF_URL JF_USER JF_PASS AUTH_HDR JF_NODL_USER JF_NODL_PASS
export KIDS_USER="kids" KIDS_PASS="vibeyfin-kids"
export NOPW_USER="nopassword" HIDDEN_USER="hidden" HIDDEN_PASS="vibeyfin-test"

exec python3 -I - <<'PY'
import json, os, sys, time, urllib.parse, urllib.request

URL, USER, PASS = os.environ["JF_URL"], os.environ["JF_USER"], os.environ["JF_PASS"]
KIDS_USER, KIDS_PASS = os.environ["KIDS_USER"], os.environ["KIDS_PASS"]
AUTH = os.environ["AUTH_HDR"].split(": ", 1)[1]
TICKS = 10_000_000


def call(path, token=None, body=None, method=None):
    headers = {"Authorization": AUTH + (f', Token="{token}"' if token else ""), "Content-Type": "application/json"}
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(URL + path, data=data, headers=headers, method=method)
    with urllib.request.urlopen(req, timeout=30) as r:
        raw = r.read()
        return json.loads(raw) if raw else None


auth = call("/Users/AuthenticateByName", body={"Username": USER, "Pw": PASS})
token, uid = auth["AccessToken"], auth["User"]["Id"]


def items(**query):
    q = urllib.parse.urlencode(dict(Recursive="true", **query))
    return call(f"/Items?{q}", token)["Items"]


# Wait (bounded) for the scan to find the fixtures.
WANT = {"Series": {"Saga Show", "Solo Season"}, "Movie": {"Starlit Crown", "Starlit Crown II", "Neon Night", "Bare Item"}}
for _ in range(60):
    found = {t: {i["Name"] for i in items(IncludeItemTypes=t)} for t in WANT}
    if all(WANT[t] <= found[t] for t in WANT):
        saga_ids = [i for i in items(IncludeItemTypes="Series") if i["Name"] == "Saga Show"]
        if saga_ids and len(items(IncludeItemTypes="Episode", ParentId=saga_ids[0]["Id"])) >= 13:
            break
    time.sleep(2)
else:
    sys.exit("fixtures not found in the library: run start.sh (or POST /Library/Refresh) first")


def one(kind, name):
    return next(i for i in items(IncludeItemTypes=kind) if i["Name"] == name)


saga = one("Series", "Saga Show")
solo = one("Series", "Solo Season")
neon = one("Movie", "Neon Night")
crown = one("Movie", "Starlit Crown")
crown2 = one("Movie", "Starlit Crown II")
bare = one("Movie", "Bare Item")
episodes = call(f"/Shows/{saga['Id']}/Episodes?userId={uid}", token)["Items"]
by_number = {(e["ParentIndexNumber"], e["IndexNumber"]): e for e in episodes}
e1, e2 = by_number[(1, 1)], by_number[(1, 2)]

# Box set: created once, found by name afterwards. Adding members again is harmless.
boxsets = [b for b in items(IncludeItemTypes="BoxSet") if b["Name"] == "Crown Collection"]
if boxsets:
    box_id = boxsets[0]["Id"]
    call(f"/Collections/{box_id}/Items?ids={crown['Id']},{crown2['Id']}", token, method="POST")
else:
    box_id = call(f"/Collections?name={urllib.parse.quote('Crown Collection')}&ids={crown['Id']},{crown2['Id']}",
                  token, method="POST")["Id"]

watched = {e1["Id"], bare["Id"]}
resume = {e2["Id"]: int(1.2 * TICKS), neon["Id"]: 2 * TICKS}
favourites = {saga["Id"], neon["Id"], crown["Id"], solo["Id"]}
touched = [i["Id"] for i in episodes] + [saga["Id"], solo["Id"], neon["Id"], crown["Id"], crown2["Id"], bare["Id"]]
touched += [i["Id"] for i in items(IncludeItemTypes="Episode", ParentId=solo["Id"])]
for item_id in touched:
    call(f"/UserItems/{item_id}/UserData?userId={uid}", token, method="POST", body={
        "Played": item_id in watched,
        "IsFavorite": item_id in favourites,
        "PlaybackPositionTicks": resume.get(item_id, 0),
        "PlayCount": 1 if item_id in watched else 0,
    })

# Kids user: created once by name, policy set on every run. Capped at PG (level 10), blocks the tag "no-kids", and may
# watch at any hour (0 to 24, so a test run never fails by the clock; edit the schedule to try a closed window).
users = {u["Name"]: u for u in call("/Users", token)}
kid = users.get(KIDS_USER) or call("/Users/New", token, body={"Name": KIDS_USER, "Password": KIDS_PASS})
policy = call(f"/Users/{kid['Id']}", token)["Policy"]
policy.update(MaxParentalRating=10, BlockedTags=["no-kids"], IsAdministrator=False,
              AccessSchedules=[{"UserId": kid["Id"], "DayOfWeek": "Everyday", "StartHour": 0.0, "EndHour": 24.0}])
call(f"/Users/{kid['Id']}/Policy", token, body=policy, method="POST")
print(f"kids user {kid['Id']} ready")

# A user who may not download: created once by name, and its policy set every run.
users = call("/Users", token)
nodl = next((u for u in users if u["Name"] == os.environ["JF_NODL_USER"]), None)
if nodl is None:
    nodl = call("/Users/New", token, method="POST", body={"Name": os.environ["JF_NODL_USER"], "Password": os.environ["JF_NODL_PASS"]})
policy = call(f"/Users/{nodl['Id']}", token)["Policy"]
policy["EnableContentDownloading"] = False
call(f"/Users/{nodl['Id']}/Policy", token, method="POST", body=policy)

# Sign-in users: one without a password, one hidden from the sign-in screen. Created once by name; who is listed is set every run.
users = {u["Name"]: u for u in call("/Users", token)}
if os.environ["NOPW_USER"] not in users:
    call("/Users/New", token, method="POST", body={"Name": os.environ["NOPW_USER"]})
if os.environ["HIDDEN_USER"] not in users:
    call("/Users/New", token, method="POST", body={"Name": os.environ["HIDDEN_USER"], "Password": os.environ["HIDDEN_PASS"]})
# Jellyfin hides new users from the sign-in screen; every test user is listed except "hidden".
for user in call("/Users", token):
    policy = call(f"/Users/{user['Id']}", token)["Policy"]
    policy["IsHidden"] = user["Name"] == os.environ["HIDDEN_USER"]
    call(f"/Users/{user['Id']}/Policy", token, method="POST", body=policy)
print(f"seeded user data for {len(touched)} items; Crown Collection {box_id}")
PY
