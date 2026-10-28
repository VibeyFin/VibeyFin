# Plan 19: Home hero carousel, 1 to 5 items or off

> **Status:** in progress. Work it in the phases below, stopping for review after each.

**Goal:** The Home hero can feature up to five titles in a swipeable carousel, the number set per profile. One item, the
default, is the hero as it is today; Off replaces the "Featured title on Home" switch.

**Related:** issue #17, issue #16 (customisation: rules become defaults), plan 10 (Home rows, where the hero was built).
Mockups: the `HeroCarousel` card and the updated Home section of `SettingsPhone` in the shared mockups artifact.

## Decisions

- **The old rule changes.** `guardrails.md` said "one item per app session" and "**Never** an auto-rotating carousel". It now
  says: up to the profile's number of items, picked once per session; a carousel only when that number is 2 or more; auto-advance
  under the limits below. One stays the default, so a profile that never touches the setting sees no change.
- **Setting:** "Featured titles on Home": Off, 1 title, 2 titles … 5 titles. Default 1. A profile that had the switch off reads as
  Off. Six choices, drawn the way each app draws "Genre rows on Home".
- **Picking:** today's rules per item (a `Backdrop` and a `Logo`, not watched, from recently added and My List), drawn at random
  without repeats, at most the setting's number. Fewer qualify: only those show. One qualifies: a single card, no dots. None: no
  hero, no gap.
- **Auto-advance: yes, slowly, once through.** Every 8 seconds the carousel moves to the next item and rests on the last one; it
  never loops back to the first. It stops for the rest of the visit once the user swipes it themselves, pauses while a finger is
  on it, and only runs while Home is on screen with the app in front. It never moves under Reduce Motion (iOS) / Remove
  animations (Android) or while VoiceOver / TalkBack is on. Rationale: slow enough to read the title and reach Play, and once
  through means it never keeps moving under someone who is just reading the rows below.
- **Page dots** sit centred under the card, outside the artwork, so they never meet the buttons or the logo. iOS uses
  `UIPageControl` (wrapped), the system's own control, which VoiceOver can adjust; Android draws a small row of dots (M3 has no
  pager indicator): the current dot is `ink`, the others `ink-subtle`. Never aqua: aqua is Play and progress only.
- **Actions follow the page.** Play and My List act on the item showing; the page passes its item id, so a swipe in flight can't
  play the wrong title.
- **Accessibility.** Each page is labelled "Featured, 2 of 3: Starlit Crown, opens its page". On iOS the carousel is one
  adjustable element (swipe up/down moves item), on Android the pager's pages are announced as they scroll.
- **Loading.** The skeleton is still in the first frame. With the setting at 2 or more it includes the dots row's height, so the
  card does not move when the hero arrives; if fewer than two then qualify, the dots row goes with the same fade as any skeleton.
- **Storage.** One new column `homeHeroItems` (1 to 5, default 1) on `account_settings`, by auto-migration to version 14. The
  existing `showHomeHero` column stays the off switch, so no data has to move: the setting reads `0` when `showHomeHero` is false,
  else `homeHeroItems`. Writing 0 clears `showHomeHero`; writing 1 to 5 sets both.

## Not in this plan

Choosing which rows feed the hero, a hero trailer, a peek of the next card, per-item ordering, a speed setting for auto-advance.

## Phases

### Phase 1: shared logic and settings

1. **Storage.** `AccountSettingsEntity.homeHeroItems` (default 1), version 14 with `AutoMigration(13, 14)`, schema exported,
   `MigrationTest` checks the column and the `13 → 14` move. DAO: observe and set it, and a write of both columns in one query.
2. **`ProfileSettings.homeHeroItems: Flow<Int?>`** (0 to 5) and `setHomeHeroItems(value)`, accepting only `HOME_HERO_CHOICES`
   (`0..5`). `showHomeHero` and `setShowHomeHero` go.
3. **Settings.** `SettingKey.ShowHero` becomes `HomeHeroItems`, a `ChoiceItem` with the six options; `SettingsValues.homeHeroItems`.
   Android's settings icon mapping follows the key. Tests in `SettingsViewModelTest` and `SettingsModelTest` move with it.
4. **Picking.** `pickHeroes(candidates, count, random)` replaces `pickHero`: distinct, qualified, shuffled, the first `count`.
   `HomeHeroTest` covers count, no repeats, fewer qualifying, none.
5. **`HomeViewModel`.** `HomeUiState.heroes: List<MediaItem>` replaces `hero`; `heroSlots: Int` (0 when off, else the setting)
   tells the skeleton whether to keep room for the dots. `onHeroPlay(itemId)` and `onHeroMyList(itemId)` act on that item; the
   My List undo keeps working per item. The setting changing in Settings picks again (as switching the hero on does today).
   iOS facade (`HomeModel`) follows.
6. **Strings.** `homeHeroItemsTitle`, `homeHeroItemsHelp`, `homeHeroItemsOff`, `homeHeroItemsCount(n)`,
   `featuredPage(title, index, count)`; the old `homeHeroTitle`, `homeHeroHelp` go once nothing reads them.

Both apps are adjusted only as far as needed to build: they draw the first of `heroes` as today. Done when
`./gradlew :shared:allTests` and `:androidApp:assembleDebug` pass and the iOS app builds.

### Phase 2: the carousel on both platforms

1. **Android** (`HomeHeroSection.kt`): a `HorizontalPager` of `HomeHeroSection` cards with the dots under it when there are two
   or more, the auto-advance (a `LaunchedEffect` keyed on the page, cancelled by a drag, off under
   `Settings.Global.ANIMATOR_DURATION_SCALE == 0` or an enabled touch-exploration `AccessibilityManager`, paused off screen via the
   lifecycle), the skeleton's dots row. Page semantics with `featuredPage`.
2. **iOS** (`HomeHeroView.swift`): a `TabView` with `.tabViewStyle(.page(indexDisplayMode: .never))` and a wrapped
   `UIPageControl` under it; auto-advance by a `Task` loop that checks `accessibilityReduceMotion` and
   `UIAccessibility.isVoiceOverRunning`, stops on a drag (`onChange` of the selection not made by the timer) and on `scenePhase`
   leaving `.active`. `accessibilityAdjustableAction` moves item.
3. **Docs:** `screens.md` **Home hero**, `android.md`, `ios.md` rows, `ROADMAP.md`, the phone-pass checklist lines for the hero.
4. **UITest:** the onboarding flow sets the setting to 3 in Settings and checks that Home shows the dots ("Featured, 1 of 3").
5. **Verified** against the test server: Saga Show, Starlit Crown and Starlit Crown II are the three candidates
   (`docs/test-server.md`), so 2, 3 and 5 all show the carousel (5 shows three).

Done when the tests and both builds pass, `scripts/ios-smoke.sh` passes, and the carousel has been run on Android against the
test server.
