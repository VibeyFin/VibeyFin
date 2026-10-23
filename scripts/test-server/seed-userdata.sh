#!/usr/bin/env bash
# Seeds user data and the box set for the page fixtures, through the Jellyfin API as the admin test user.
# Idempotent: every run sets the same state (it does not toggle). Run it after the library scan has found the
# fixtures from make-media.sh (start.sh does the scan; this script waits for the items, bounded).
#   Collection  "Crown Collection" (BoxSet): Starlit Crown, Starlit Crown II (created once, by name)
#   Watched     Saga Show S01E01
#   Resume      Saga Show S01E02 at 1.2 s of 3 s (40 %), Neon Night at 2 s of 5 s
#   My List     Saga Show, Neon Night, Starlit Crown, Solo Season
#   Cleared     everything else among these fixtures (not played, no resume, not a favourite)
# The expectations are the "Page fixtures" section of docs/test-server.md.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"
export JF_URL JF_USER JF_PASS AUTH_HDR

exec python3 -I - <<'PY'
import json, os, sys, time, urllib.parse, urllib.request

URL, USER, PASS = os.environ["JF_URL"], os.environ["JF_USER"], os.environ["JF_PASS"]
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

watched = {e1["Id"]}
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
print(f"seeded user data for {len(touched)} items; Crown Collection {box_id}")
PY
