# Translating VibeyFin

VibeyFin's text lives in JSON files in `shared/strings/`, in sets called layers, one file per language in each layer. Both apps read the same files. English (`en.json`) is the source and the fallback. Translations are done on Weblate; nobody edits a translation file by hand.

## How the strings are organised

A client loads the `common` layer plus the layers for its own device. The layers load in order and a later layer wins a duplicate key, so the order is `common`, then `mobile`, then `tv`.

| Layer | Folder | Holds | Loaded by |
| --- | --- | --- | --- |
| Common | `shared/strings/common/` | Text every client shows, Seerr included | Every client |
| Mobile | `shared/strings/mobile/` | Downloads and offline text for phones and tablets | iPhone, iPad, Android phone and tablet |
| TV | `shared/strings/tv/` | Not created yet | A future TV client |

Each layer has a file for every language in `shared/languages.json` (`common/en.json`, `common/de.json`, `mobile/en.json`, ...). On Weblate each layer is a component of the one VibeyFin project (Common: `shared/strings/common/*.json`, Mobile: `shared/strings/mobile/*.json`), so shared text is translated once and device text sits in its own list.

A client names its layers at startup (`initKoin(stringLayers = ...)`; phones and tablets use `StringLayers.mobileClient`). A key found in two layers is an override: the later layer's text replaces the earlier one on that client, and its English text must differ. An identical duplicate is a copy, and `scripts/check-languages.sh` fails on it.

Example: a TV client would pass `listOf("common", "tv")`. If `common/en.json` has `empty_list_help` ("Tap Clear to start over") and `tv/en.json` has the same key ("Press Back to start over"), the TV client shows its own text and phones keep the common one. A key's layer is never visible to a caller: `Strings` stays one object.

## Translating

1. Open the [VibeyFin workspace on Weblate](https://translate.lore-le.ch/workspaces/0e11631b-4809-477f-ab13-52e3dff0e7b6/) and pick your language.
2. Translate. Weblate saves your work, commits it to the `weblate` branch and opens a pull request.
3. A maintainer reviews and merges it. The next build ships it.

Rules for translators:

- Keep `{{placeholders}}` exactly as they are (`{{name}}`, `{{count}}`). Move them to where your language needs them, never rename or drop one.
- A key ending in `_one` or `_other` is a plural pair. `{{count}}` is the number. Add the forms your language uses (`_few`, `_many` and so on); English only has `_one` and `_other`.
- Names stay as they are: VibeyFin, Jellyfin, Seerr, Quick Connect.
- Words in the glossary (My List, Who's watching?, Continue watching, Home, Downloads) stay consistent.
- Tab, button and chip labels have a length limit set in Weblate per component; stay under it.
- The layer a text lives in does not matter to you; pick the component (Common or Mobile) and translate what it lists.
- System dialogs (permission prompts, the share sheet) use the device language and are not part of these files.

## Adding or changing English text (developers)

1. Pick the layer and add the key to its `en.json`: flat, `snake_case`, named after what the text is (`home_continue_watching`). One key per whole sentence; never glue fragments in Kotlin. Where it goes:
   - shown by every client: `shared/strings/common/en.json`,
   - only on phones and tablets: `shared/strings/mobile/en.json`,
   - only on TV: `shared/strings/tv/en.json`.
2. Read it from `Strings` (or `LoadMessages`, `SignInMessages`, `SeerrMessages`) with `text("key", "name" to value)`, or `plural("key", count)` for counts. Each call stays on one line with a literal key.
3. For a count, add both `key_one` and `key_other`. `plural()` fills `{{count}}`.
4. Numbers, decimals, month names and clock times go through `Formats.current` (`LocaleFormatter`), never hand-built strings.
5. Run `./gradlew :shared:testAndroidHostTest`: `EnglishFileTest` fails on a key that is missing, unused, missing a placeholder, or copied identically into two layers.

Once the English change reaches `main`, the GitHub webhook tells Weblate and the new text shows up for translators on its own.

## Adding a language

1. A Weblate admin starts the language in every component (Common, Mobile) in the Weblate UI. Weblate opens a pull request with the new `shared/strings/<layer>/<tag>.json` for each layer (BCP 47 style: `de`, `pt-BR`).
2. A maintainer adds the rest on that pull request:
   - a branch for the language in `PluralRules.kt` (CLDR rules; `de` and `en` are one/other),
   - an entry in `shared/languages.json` with the language's own name (`{ "tag": "de", "name": "Deutsch" }`),
   - the tag in `androidApp/src/main/res/xml/locales_config.xml`,
   - the tag in `CFBundleLocalizations` in `iosApp/iosApp/Info.plist`.
3. `scripts/check-languages.sh` fails CI until the three lists agree, every layer folder has a file for the language and the language has a plural rule. A language file that is not listed yet is still being translated: it may be empty, `{}` or partial and CI does not mind. Run the script locally to check.

## Adding a layer

1. Create `shared/strings/<layer>/en.json` and a file for every language in `shared/languages.json` (`{}` is fine for a language with nothing yet).
2. In Weblate, add a component to the VibeyFin project with the file mask `shared/strings/<layer>/*.json`. Copy the file format and settings of an existing component, share the project's glossary and translation memory, and check the GitHub webhook reaches it.
3. Pass the layer in the client's list, after the layers it overrides (`initKoin(stringLayers = listOf("common", "<layer>"))`).
4. `scripts/check-languages.sh` and the file tests pick the folder up on their own: they check the language files, duplicate keys and placeholders for every folder under `shared/strings/`.

## Choosing the language in the app

Android has a Language row in Settings (Device language or any shipped language). On iOS there is no in-app row: the app follows the per-app language in the system Settings app (Settings > VibeyFin > Language), which iOS lists once `CFBundleLocalizations` names more than one language.

## Merging Weblate pull requests

Weblate pull requests are squash-merged like every other pull request in this repo. Lock the components on Weblate while a pull request is under review, so no new changes land in them.

Weblate's own docs caution against squash merging, because its local commits then have no match upstream. If a push to the `weblate` branch ever starts failing after a merge, an admin runs Repository maintenance, "Reset and reapply translations", on the component. That keeps the pending translations and resets the rest to `main`.

## Moving keys between layers

A key that moves from one layer to another carries its translations with it. Merge or close every open Weblate pull request first, lock the components, merge the move to `main`, then in Weblate change the file masks (and add a component for a new layer), check that translation propagation keeps the moved keys' translations, and unlock once the webhook reaches every component.
