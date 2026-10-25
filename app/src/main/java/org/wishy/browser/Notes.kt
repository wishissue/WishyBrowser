package org.wishy.browser

import androidx.compose.ui.graphics.vector.ImageVector

/** What a notification is about. Decides its colour and default icon (see ui/Notifications.kt). */
enum class NoteKind { INFO, SUCCESS, WARNING, ERROR, SHIELD }

/**
 * One notification card. [title] is the short headline, [message] an optional second line.
 * [icon] overrides the icon the kind would normally show.
 */
class Note(
    val id: Long,
    val kind: NoteKind,
    val title: String,
    val message: String?,
    val icon: ImageVector?,
    val durationMs: Long,
    val time: Long,
)
