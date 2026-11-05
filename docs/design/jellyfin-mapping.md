# Jellyfin data → UI

Which server data drives which piece of UI. All of this lives in the shared Kotlin Multiplatform core (API client, repositories, playback reporting, download queue) — the native UIs only render the state it exposes. Paths are for Jellyfin 10.9 / 10.10+; check them against your server's OpenAPI (`/api-docs`) and prefer a generated or official SDK over hand-written calls.

## Home rows

| Row | Source | Notes |
| --- | --- | --- |
| Continue watching (includes Next up) | `GET /UserItems/Resume`, then `GET /Shows/NextUp` | Movies and episodes; 16:9 `ContinueCard`. In-progress items first, newest first, then one Next up episode per series, hiding any series that already has an item in progress. Progress bar on in-progress cards only; Next up cards show "S2 E5". |
| Recently added — {library} | `GET /Items/Latest?parentId={viewId}` | One row per library from `GET /UserViews`. Posters; episodes grouped by series. When `/UserViews` fails, one "Recently added" row shows the error and retries it. |
| Collections / genres | `GET /Items?genreIds=` by community rating, 20 items | Every genre the server lists, the one with the most titles first (`GET /Genres` counts), titled with the genre; "See all" opens the genre's page. A `BoxSet` parent is not used yet. |
| My List | Items with `UserData.IsFavorite = true` | Jellyfin has no watchlist; VibeyFin uses Favorites for "My List". |

### Home category buttons and their pages

| Button | Source | Notes |
| --- | --- | --- |
| Shows | `GET /Items?recursive=true&includeItemTypes=Series&sortBy=SortName` | Opens a page titled "Shows". |
| Movies | `GET /Items?recursive=true&includeItemTypes=Movie&sortBy=SortName` | Opens a page titled "Movies". |
| New | `GET /Items` sorted by `DateCreated` descending | What was added to the server most recently, newest first, across movies and shows. |
| Categories | `GET /Genres?fields=ItemCounts` for the sheet; `genreIds` on `GET /Items` for the page | The page is titled with the genre ("Action"). Each row shows `MovieCount` + `SeriesCount`; a genre with none is left out. |

A page's Sort and Genre buttons change the same call: Name is `sortBy=SortName` ascending, Recently added is
`DateCreated` descending, Rating is `CommunityRating,SortName` descending; Genre adds `genreIds=` (a genre's own page
keeps its genre). The Genre picker is `GET /Genres?fields=ItemCounts` with `includeItemTypes` set to the page's kind
(Movie, Series, or both) and counts `MovieCount`, `SeriesCount` or their sum to match; a genre with none is left out.

Every page is `GET /Items` across every library (movies and shows for a genre), a page of 60 at a
time. Home's own rows (Continue watching, Recently added, genres) are not filtered, so none of the endpoints behind them change.

## Detail

| UI | Source | Notes |
| --- | --- | --- |
| Hero backdrop, title | `Backdrop` + `Logo` images | Phone: Backdrop center-cropped to 2:3; tablet: 16:9 as is. No Logo → `brand-display` title from `Name`. |
| Facts line | `ProductionYear` (series: `ProductionYear`–`EndDate`), `OfficialRating`, `RunTimeTicks`, `Genres` | Series show the season count instead of runtime. |
| Overview | `Overview` | 3 lines + "More". |
| Cast | `People` (type `Actor`; `Role`) | Person image: `GET /Items/{personId}/Images/Primary`. |
| More like this | `GET /Items/{id}/Similar` | Posters. |
| Seasons | `GET /Shows/{id}/Seasons` | One button each, in server order (Specials included). |
| Episodes | `GET /Shows/{id}/Episodes?seasonId={seasonId}` | Fetch on season change; cache per season. |
| Play / Resume target (series) | `GET /Shows/NextUp?seriesId={id}` | Its `ParentIndexNumber` picks the season the row opens on. |
| Trailer action | `LocalTrailerCount` → `GET /Items/{id}/LocalTrailers`; else `RemoteTrailers[].Url` | Hide the action when both are empty. Tile trailers depend on the content having trailers. |
| Like (cut: not built) | `UserData.Likes` | `POST /UserItems/{id}/Rating?likes=true or false`, `DELETE` to clear — check against your server's OpenAPI. |

## Search

- `GET /Search/Hints?searchTerm=` (debounced) for the results grid, grouped by `Type`.

## Card state

- Progress = `UserData.PlaybackPositionTicks / RunTimeTicks` (ticks are 100ns: 10,000,000 per second). Draw the bar only when the item is in the resume list.
- Watched = `UserData.Played`. The server decides it from its resume thresholds (defaults 5% / 90% / 5 min minimum) — don't recompute it client-side.
- "New episode" badge: series whose latest episode `DateCreated` is newer than the user's last play of that series.
- `38m left` = (`RunTimeTicks` − `PlaybackPositionTicks`), rounded to minutes.

## Images

- `GET /Items/{id}/Images/{type}?fillWidth={px}&quality=90&tag={imageTag}` — always pass the tag (cache key) and the rendered width × density.
- Poster → `Primary`. Thumbnail → `Thumb`, else `Backdrop`, else episode `Primary`. Hero → `Backdrop` + `Logo`.
- Placeholder color from `ImageBlurHashes` on the item: `{"<ImageType>": {"<imageTag>": "<hash>"}}`, so look it up by the same type and tag as the URL (an older picture's hash can sit beside the current one). `BlurHash.decode` (shared) turns it into a 32-px picture at the tile's shape; the real image fades in over it (`dur-base`, none with Reduce Motion).

## Playback

- Skip intro / recap: `GET /MediaSegments/{itemId}` → segments of type `Intro`, `Recap`, `Outro`, `Preview`, `Commercial` (10.10+, populated by a segment provider plugin). No segments → no skip button.
- Trickplay: the item's `Trickplay` info (tile width/height, interval, thumbnail count) → tiles at `GET /Videos/{itemId}/Trickplay/{width}/{index}.jpg`. Cache a sheet, crop the tile in the client.
- Report: `POST /Sessions/Playing` (start), `/Sessions/Playing/Progress` (every 10s, pause, seek), `/Sessions/Playing/Stopped` (stop, background).
- Quality (Settings → Playback → Quality) maps to the stream request's max bitrate: Maximum sends no practical cap (original, direct play when possible), the others 40, 10, 4 and 1.5 Mbps. The same value feeds the device profile and the `PlaybackInfo` request.
- Choose direct play over transcoding when the device can decode the stream; show "Transcoding" in the player's info sheet when it can't.

## Actions

- Mark watched / unwatched: `POST` / `DELETE /UserPlayedItems/{id}`.
- My List: `POST` / `DELETE /UserFavoriteItems/{id}`.
- Like (thumbs, cut from the roadmap): `POST` / `DELETE /UserItems/{id}/Rating`, read from `UserData.Likes`.
- Sign-in: `POST /Users/AuthenticateByName`, or Quick Connect (`/QuickConnect/Initiate` → poll → authenticate with the secret).

## Removing from Continue watching

Only a card in progress has Remove; a Next Up card does not. Jellyfin has no "hide from Continue watching" flag, so:

- **Any card:** `POST /UserItems/{id}/UserData` with `{ "PlaybackPositionTicks": 0 }` takes it out of `/UserItems/Resume` on every client and leaves `Played`, `PlayCount` and `LastPlayedDate` as they are. Undo posts the old position back. Never `DELETE /UserPlayedItems/{id}`: it also resets the play count and last played date.
- **An episode in progress:** with its position at 0 the same episode comes straight back from `/Shows/NextUp`, so the show is also hidden from Next Up at the episode Next Up names after the reset, with no server call. The series ID and that episode are kept per profile in Room (`dismissed_next_up`) and the entry is filtered out of the row. When Next Up for the series names another episode the entry is dropped and the show is back.

## Seerr

Seerr is optional: nothing below shows until the profile is signed in to one (Settings › Requests › Seerr), and never on a kids
profile. Base path `/api/v1`; the session is the `connect.sid` cookie (see roadmap Phase 8).

| UI | Seerr field / endpoint | Notes |
| --- | --- | --- |
| "Not in your library" in Search | `GET /search?query=` (percent-encode everything but `A–Z a–z 0–9 - . _ ~`) | Movies and shows only. Leave out a result whose `mediaInfo.jellyfinMediaId` is set or whose `mediaInfo.status` is 4 or 5: the library's own groups have it. |
| Poster marker | `mediaInfo.status` (+ latest request's `status`) | 2 Requested, 3 Approved (Failed when the latest request is 4), 4 Partly available, 5 Available; nothing: the + disc; latest request 3 Declined. Ink on `scrim`, never aqua; one badge per card. |
| Opens the library's page | `mediaInfo.jellyfinMediaId` | Missing from Seerr's spec but sent; when set (and the title is available or partly available) the Seerr page gives way to the Jellyfin item. |
| Title page | `GET /movie/{id}`, `GET /tv/{id}`, `.../recommendations` | Facts: year, US certification (`releases` / `contentRatings`), `runtime` or `episodeRunTime[0]`. Trailer: first `relatedVideos` with `site` YouTube and `type` Trailer. Seasons' state: `mediaInfo.seasons[].status`, else an open request for that season. |
| Request | `POST /request` | `seasons` a list of numbers (never `all` from the picker, so Specials stay out unless picked); `is4k` only with the permission (bits 1024/2048/4096, or admin 2) and `movie4kEnabled` / `series4kEnabled`. `202` "No seasons available" means nothing was left to ask for; `409` already requested; `403` quota or permission (read `/user/{id}/quota` to tell them apart). |
| Cancel request | `DELETE /request/{id}` | Only the requester's own, while it waits for approval (`status` 1). |
| My requests | `GET /request?requestedBy={me}&sort=modified` | The list has no titles or posters: each is read with `/movie/{id}` or `/tv/{id}`, a few at a time, once per session. Home's row: on their way, or arrived / declined in the last 7 days. |
| Discover rows | `/discover/trending`, `/discover/movies`, `/discover/tv`, `/discover/movies/upcoming` + `/discover/tv/upcoming` | 20 each; Coming soon merges the two upcoming lists by `releaseDate` / `firstAirDate` and shows the day. People are left out. |
| Artwork | TMDB: `https://image.tmdb.org/t/p/w342{posterPath}`, `w1280{backdropPath}`, `w185{profilePath}` | As Seerr's own web app loads it. |

