# Translating VibeyFin

VibeyFin's text lives in JSON files in `shared/strings/`, one per language, and both apps read the same files. English (`en.json`) is the source and the fallback. Translations are done on Weblate; nobody edits a translation file by hand.

## Translating

1. Open the [VibeyFin workspace on Weblate](https://translate.lore-le.ch/workspaces/0e11631b-4809-477f-ab13-52e3dff0e7b6/) and pick your language.
2. Translate. Weblate saves your work, commits it to the `weblate` branch and opens a pull request.
3. A maintainer reviews and merges it. The next build ships it.

Rules for translators:

- Keep `{{placeholders}}` exactly as they are (`{{name}}`, `{{count}}`). Move them to where your language needs them, never rename or drop one.
- A key ending in `_one` or `_other` is a plural pair. `{{count}}` is the number. Add the forms your language uses (`_few`, `_many` and so on); English only has `_one` and `_other`.
- Names stay as they are: VibeyFin, Jellyfin, Seerr, Quick Connect.
- Words in the glossary (My List, Who's watching?, Continue watching, Home, Downloads) stay consistent.
- Tab, button and chip labels have a length limit set in Weblate; stay under it.
- System dialogs (permission prompts, the share sheet) use the device language and are not part of these files.

## Adding or changing English text (developers)

1. Add the key to `shared/strings/en.json`: flat, `snake_case`, named after what the text is (`home_continue_watching`). One key per whole sentence; never glue fragments in Kotlin.
2. Read it from `Strings` (or `LoadMessages`, `SignInMessages`, `SeerrMessages`) with `text("key", "name" to value)`, or `plural("key", count)` for counts. Each call stays on one line with a literal key.
3. For a count, add both `key_one` and `key_other`. `plural()` fills `{{count}}`.
4. Numbers, decimals, month names and clock times go through `Formats.current` (`LocaleFormatter`), never hand-built strings.
5. Run `./gradlew :shared:testAndroidHostTest`: `EnglishFileTest` fails on a key that is missing, unused, or missing a placeholder.

Once the English change reaches `main`, the GitHub webhook tells Weblate and the new text shows up for translators on its own.

## Adding a language

1. A Weblate admin starts the language in the Weblate UI. Weblate opens a pull request with the new `shared/strings/<tag>.json` (BCP 47 style: `de`, `pt-BR`).
2. A maintainer adds the rest on that pull request:
   - a branch for the language in `PluralRules.kt` (CLDR rules; `de` and `en` are one/other),
   - an entry in `shared/languages.json` with the language's own name (`{ "tag": "de", "name": "Deutsch" }`),
   - the tag in `androidApp/src/main/res/xml/locales_config.xml`,
   - the tag in `CFBundleLocalizations` in `iosApp/iosApp/Info.plist`.
3. `scripts/check-languages.sh` fails CI until the three lists agree and the language has a plural rule. A language file that is not listed yet is still being translated: it may be empty, `{}` or partial and CI does not mind. Run the script locally to check.

## Choosing the language in the app

Android has a Language row in Settings (Device language or any shipped language). On iOS there is no in-app row: the app follows the per-app language in the system Settings app (Settings > VibeyFin > Language), which iOS lists once `CFBundleLocalizations` names more than one language.

## Merging Weblate pull requests

Weblate pull requests are squash-merged like every other pull request in this repo. Lock the component on Weblate while a pull request is under review, so no new changes land in it.

Weblate's own docs caution against squash merging, because its local commits then have no match upstream. If a push to the `weblate` branch ever starts failing after a merge, an admin runs Repository maintenance, "Reset and reapply translations", on the component. That keeps the pending translations and resets the rest to `main`.
