package org.wishy.browser

/** Which edge of the screen the toolbar sits on. */
enum class BarSide(val id: String, val label: String) {
    TOP("top", "Top"), BOTTOM("bottom", "Bottom"), LEFT("left", "Left"), RIGHT("right", "Right");

    /** Left and right use a narrow column of icon buttons instead of a wide row. */
    val vertical: Boolean get() = this == LEFT || this == RIGHT

    companion object {
        fun byId(id: String?): BarSide = entries.firstOrNull { it.id == id } ?: TOP
    }
}
