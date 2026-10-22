# Plan 13: Unified search and My List (fifth part of roadmap Phase 4)

Goal: a Search tab that finds movies, shows, episodes, people and collections, and a My List row and page on Home. Android and iOS.

**Status (2026-10-22):** shared logic with tests, Android built, iOS written and only parsed (no Xcode).

## Decisions

1. **Search tab** is the second tab (Home, Search, Library, Downloads), with its own back stack. The field takes the keyboard when the tab opens empty.
2. **`GET /Search/Hints`** with `includeItemTypes=Movie,Series,Episode,Person,BoxSet`, at most 40, asked 300 ms after the last key; a newer text drops the older request. Studios, genres and other hint types are left out.
3. **Grouped** in the order Movies, Shows, Episodes, People, Collections, a group only when it has a match. A row is a poster (56 wide), a still (112 wide) for an episode or a round portrait for a person, the title, and a line: the year, or "Show · S1 E4".
4. **A tap opens** the item's page (movie, show, episode), the person's page, or the collection's page. Seerr's "not in your library" section joins in Phase 8.
5. **States:** an empty field invites a search; the first search shows grey rows; a newer search dims the older answer; no match says so with a hint; a failure says why with Try again; a 401 goes through `ConnectionManager.expireSession`.
6. **My List** is a Home row under Continue watching (`GET /Items?filters=IsFavorite`, 20, newest added first), hidden while empty, with "See all" opening a category page. It reloads when a favourite changes. The page's Genre picker counts the titles on the list, not the library's: the server cannot count a genre among favourites, so the list (up to 500) is read with its genres and counted on the device.

## Not in this plan

Recent searches, filters in the results, voice search, thumbs (`UserData.likes`).
