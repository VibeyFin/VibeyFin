package app.vibeyfin.i18n

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

fun <T, R> Flow<T>.localized(transform: (T) -> R): Flow<R> = combine(this, Texts.language) { value, _ -> transform(value) }
