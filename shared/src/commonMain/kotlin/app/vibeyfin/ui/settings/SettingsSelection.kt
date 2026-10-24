package app.vibeyfin.ui.settings

/**
 * The page a two-pane Settings opens on: the first page after Root, in [SettingsPageId] order, with a
 * visible toggle or choice to set. Profile only has actions and info, so it is never picked; null when
 * no page has anything to set (nobody is signed in).
 */
fun firstSettablePage(pages: Map<SettingsPageId, SettingsPage>): SettingsPageId? =
    SettingsPageId.entries.firstOrNull { id ->
        id != SettingsPageId.Root && pages[id]?.visibleOnly()?.sections.orEmpty()
            .any { section -> section.items.any { it is ToggleItem || it is ChoiceItem } }
    }
