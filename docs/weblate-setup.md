# Weblate setup

For the Weblate admin. The workspace is at https://translate.lore-le.ch/workspaces/0e11631b-4809-477f-ab13-52e3dff0e7b6/. Translators and contributors want [`translating.md`](translating.md).

The repo side is done: `shared/strings/en.json` is the source, `shared/languages.json` lists shipped languages and `scripts/check-languages.sh` runs in CI. This file is what has to be set up on the Weblate server.

Verified against docs.weblate.org unless marked **unverified**.

**Access.** Create a GitHub token (or bot user) with Contents and Pull requests read/write on the repo and put it in `GITHUB_CREDENTIALS` in `settings.py` ([docs](https://docs.weblate.org/en/latest/admin/code-hosting.html)). Push over HTTPS with the token, or add Weblate's SSH key as a deploy key with write access. Set `DEFAULT_COMMITER_NAME` / `DEFAULT_COMMITER_EMAIL`.

**Project.** Create "VibeyFin", source language English, translation instructions URL pointing at `docs/translating.md`. Turn **Enable hooks** on. Start with public access and **Direct translation** (suggestions on, reviews off); move to **Dedicated reviewers** once a language has several contributors. Use and contribute to the shared translation memory. Access-mode labels are **unverified**.

**Component "App strings".**

| Setting | Value |
|---|---|
| Version control system | GitHub pull requests |
| Source code repository | `https://github.com/VibeyFin/VibeyFin.git` (must match the webhook payload URL) |
| Repository branch / Push branch | `main` / `weblate` (with the push branch empty Weblate forks instead) |
| File mask | `shared/strings/*.json` |
| Monolingual base file | `shared/strings/en.json` |
| Template for new translations | `shared/strings/en.json` |
| Edit base file | off (English changes through a git PR) |
| File format | i18next JSON file v4 |
| Language code style | BCP style (`pt-BR`); dropdown label **unverified** |
| Adding new translation | Create new language file |
| Merge style / Push on commit | Rebase / on |
| Age of changes to commit | 24 hours |
| Commit, add, delete, merge, add-on, merge-request messages | one line, no body, starting `task:` (e.g. `task: translate {{ language_name }}`), because the repo's format is `task\|fix\|feat\|chore: <message>` and Weblate's defaults add a body ([variables](https://docs.weblate.org/en/latest/admin/projects.html#component)) |

The format adds the "i18next interpolation" check by itself. Enable the plural checks as enforced; labels **unverified**. Weblate PRs are squash-merged like all others in this repo. Lock the component while a PR is under review ([docs](https://docs.weblate.org/en/latest/admin/continuous.html)).

**Strings.** The format stores no notes, context, location or flags ([format page](https://docs.weblate.org/en/latest/formats/i18next.html)). So: write an **Explanation** on ambiguous source strings ("Back", "Sign in" as button or title, anything with a placeholder), set `max-length:N` on tab, button and chip labels with the Bulk edit add-on, and give `priority:200` to Who's watching, Home, Player and Settings strings. Bulk-loading Explanations is **unverified**.

**Webhook.** In the GitHub repo add `https://<weblate-host>/hooks/github/` (push events only). Fallback: `AUTO_UPDATE = "remote"` fetches hourly. Test with one English edit on `main`; it should show on Weblate within a minute.

**Add-ons.** Bulk edit (once). Do not enable Squash Git commits: GitHub squashes the PR already, and Weblate's docs warn the add-on conflicts with squash merging. Cleanup translation files and Customize JSON output are useful but their behaviour on this format is **unverified**, so test them once. Automatic translation off, or only from translation memory marked "Needs editing".

**Languages and glossary.** Only admins start new languages: set the component's "Adding new translation" so users cannot create their own (the exact option label is **unverified**; check the dropdown), otherwise any signed-in user can open a PR for an unplanned language. Add languages from the Weblate UI; Weblate opens a PR with the new `<tag>.json`, and a maintainer adds the plural rule and the `languages.json`, `locales_config.xml` and `CFBundleLocalizations` entries on that PR (`check-languages.sh` fails until they do). Seed the glossary with terms that stay as is (VibeyFin, Jellyfin, Seerr, Quick Connect) and terms to keep consistent (My List, Who's watching?, Continue watching, Home, Downloads).

**Notifications and badge.** Lorenzo and the maintainer watch the project with failure events on (labels **unverified**). Copy the badge URL and the "Help translate" link from the project's Share tab into the README.

**Flow.** Translator saves, Weblate commits lazily, pushes `weblate` and opens a PR, `pr.yml` runs, a maintainer squash-merges, the webhook tells Weblate `main` moved. An English edit on `main` reaches Weblate through the same webhook.
