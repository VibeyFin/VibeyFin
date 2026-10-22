# Plan 12: Person page and special features (fourth part of roadmap Phase 4)

Goal: a cast member's picture or name on a detail page opens their own page, and a detail page shows its extras.

**Status (2026-10-22):** shared logic and Android built (shared with tests; Android installed on the owner's phone but not seen on the emulator, which hung); iOS written and only parsed (no Xcode).

## Decisions

1. **Person page** (`PersonViewModel`): `GET /Items?ids={id}` for the person (biography `Overview`, birth year from `PremiereDate`, first `ProductionLocations`, the `Primary` picture) and `GET /Items?personIds={id}&includeItemTypes=Movie,Series&sortBy=PremiereDate,SortName&sortOrder=Descending` for what they appear in. The two load on their own: a failed list leaves the person and offers Try again.
2. **Layout:** a round portrait (initials on a grey disc when there is none), the name in `brand-display`, "Born 1999 · Place", the biography (four lines, More / Less; "No biography on the server." when none), then Movies and Shows as poster rows, newest first. Nothing in the library says so.
3. **Cast tiles are buttons** that open the page. Most cast have no picture on a server that never fetched person images from a metadata provider (the test server returns 404 for them); that is the server's data, not a loading fault.
4. **Extras** (`GET /Items/{id}/SpecialFeatures`, a bare array) are a row of wide tiles on a movie's or show's page, hidden when empty; each opens like any item.
5. **Phone detail picture collapses** *(owner's request)*: the picture is pinned and shrinks from the bottom 1.7x as fast as the text scrolls, and the page's content scrolls over it, so the page is reached without scrolling past the whole picture.

## Not in this plan

Unified search (people open this page from there later), the person's own favourite or image refresh.
