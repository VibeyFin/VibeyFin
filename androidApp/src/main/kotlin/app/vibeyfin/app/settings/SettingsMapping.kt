package app.vibeyfin.app.settings

import app.vibeyfin.app.player.SubtitleViewStyle
import app.vibeyfin.app.player.subtitleStyle
import app.vibeyfin.ui.settings.ChoiceItem
import app.vibeyfin.ui.settings.LinkItem
import app.vibeyfin.ui.settings.SettingItem
import app.vibeyfin.ui.settings.SettingKey
import app.vibeyfin.ui.settings.SettingOption
import app.vibeyfin.ui.settings.SettingsPage
import app.vibeyfin.ui.settings.SettingsPageId
import app.vibeyfin.ui.settings.ToggleItem
import app.vibeyfin.ui.settings.firstSettablePage

/**
 * The page as the screen draws it: hidden items are gone, and so is a link to a page the tree does not
 * have (yet), so a tap can never lead nowhere. Sections left empty by either are gone too.
 */
internal fun SettingsPage.drawn(hasPage: (SettingsPageId) -> Boolean): SettingsPage {
    val shown = visibleOnly()
    return shown.copy(
        sections = shown.sections.mapNotNull { section ->
            section.copy(items = section.items.filterNot { it is LinkItem && !hasPage(it.target) }).takeIf { it.items.isNotEmpty() }
        },
    )
}

/** A row's identity, used as its composition key and in tests: the page id and the key, since a key can appear on two pages. */
internal fun rowId(page: SettingsPageId, item: SettingItem): String = "${page.name}/${item.key.name}"

/** The page a route id names; an id this build does not know (a newer link, a stale back stack) opens the root. */
internal fun pageIdOrRoot(id: String): SettingsPageId = SettingsPageId.entries.firstOrNull { it.name == id } ?: SettingsPageId.Root

/** The rows of an [ChoiceItem.asPage] choice, drawn inline in its own section: every option, the one in use marked. */
internal fun ChoiceItem.checklist(): List<Pair<SettingOption, Boolean>> = options.map { it to (it.value == value) }

/** The label of the option in use, or null when the value is off the list. */
internal fun ChoiceItem.valueLabel(): String? = options.firstOrNull { it.value == value }?.label

/** What the preview draws, from the page's own rows (the unfiltered page: they are hidden while the system style is on). */
internal fun SettingsPage.subtitlePreviewStyle(): SubtitleViewStyle {
    val items = sections.flatMap { it.items }
    fun choice(key: SettingKey) = (items.firstOrNull { it.key == key } as? ChoiceItem)?.value ?: 0
    val follow = (items.firstOrNull { it.key == SettingKey.CaptionsFollowSystem } as? ToggleItem)?.checked ?: true
    return subtitleStyle(follow, choice(SettingKey.SubtitleSize), choice(SettingKey.SubtitleColor), choice(SettingKey.SubtitleBackground))
}

/** The root's visible links, in order, to pages the tree has. */
private fun rootLinks(pages: Map<SettingsPageId, SettingsPage>): List<LinkItem> =
    pages[SettingsPageId.Root]?.drawn { it in pages }?.sections.orEmpty().flatMap { it.items }.filterIsInstance<LinkItem>()

/**
 * The root row that is open on a tablet: the saved one while it is still a drawn link, otherwise the first
 * link to the first settable page. It is a row, not a page, because two rows can lead to one page; only
 * the tapped one is selected. Null when there is nothing to open.
 */
internal fun selectedLink(saved: SettingKey?, pages: Map<SettingsPageId, SettingsPage>): LinkItem? {
    val links = rootLinks(pages)
    return links.firstOrNull { it.key == saved }
        ?: firstSettablePage(pages)?.let { first -> links.firstOrNull { it.target == first } }
}
