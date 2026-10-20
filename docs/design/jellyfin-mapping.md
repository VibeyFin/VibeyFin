# Jellyfin data → UI

Which server data drives which piece of UI. All of this lives in the shared Kotlin Multiplatform core (API client, repositories, playback reporting, download queue) — the native UIs only render the state it exposes. Paths are for Jellyfin 10.9 / 10.10+; check them against your server's OpenAPI (`/api-docs`) and prefer a generated or official SDK over hand-written calls.

## Home rows

| Row | Source | Notes |
| --- | --- | --- |
| Continue watching (includes Next up) | `GET /UserItems/Resume`, then `GET /Shows/NextUp` | Movies and episodes; 16:9 `ContinueCard`. In-progress items first, newest first, then one Next up episode per series, hiding any series that already has an item in progress. Progress bar on in-progress cards only; Next up cards show "S2 E5". |
| Recently added — {library} | `GET /Items/Latest?parentId={viewId}` | One row per library from `GET /UserViews`. Posters; episodes grouped by series. |
| Collections / genres | `GET /Items` with `genreIds` or a `BoxSet` parent | Optional rows after the core four. |
| My List | Items with `UserData.IsFavorite = true` | Jellyfin has no watchlist; VibeyFin uses Favorites for "My List". |

## Card state

- Progress = `UserData.PlaybackPositionTicks / RunTimeTicks` (ticks are 100ns: 10,000,000 per second). Draw the bar only when the item is in the resume list.
- Watched = `UserData.Played`. The server decides it from its resume thresholds (defaults 5% / 90% / 5 min minimum) — don't recompute it client-side.
- "New episode" badge: series whose latest episode `DateCreated` is newer than the user's last play of that series.
- `38m left` = (`RunTimeTicks` − `PlaybackPositionTicks`), rounded to minutes.

## Images

- `GET /Items/{id}/Images/{type}?fillWidth={px}&quality=90&tag={imageTag}` — always pass the tag (cache key) and the rendered width × density.
- Poster → `Primary`. Thumbnail → `Thumb`, else `Backdrop`, else episode `Primary`. Hero → `Backdrop` + `Logo`.
- Placeholder color from `ImageBlurHashes` on the item.

## Playback

- Skip intro / recap: `GET /MediaSegments/{itemId}` → segments of type `Intro`, `Recap`, `Outro`, `Preview`, `Commercial` (10.10+, populated by a segment provider plugin). No segments → no skip button.
- Trickplay: the item's `Trickplay` info (tile width/height, interval, thumbnail count) → tiles at `GET /Videos/{itemId}/Trickplay/{width}/{index}.jpg`. Cache a sheet, crop the tile in the client.
- Report: `POST /Sessions/Playing` (start), `/Sessions/Playing/Progress` (every 10s, pause, seek), `/Sessions/Playing/Stopped` (stop, background).
- Choose direct play over transcoding when the device can decode the stream; show "Transcoding" in the player's info sheet when it can't.

## Actions

- Mark watched / unwatched: `POST` / `DELETE /UserPlayedItems/{id}`.
- My List: `POST` / `DELETE /UserFavoriteItems/{id}`.
- Sign-in: `POST /Users/AuthenticateByName`, or Quick Connect (`/QuickConnect/Initiate` → poll → authenticate with the secret).
