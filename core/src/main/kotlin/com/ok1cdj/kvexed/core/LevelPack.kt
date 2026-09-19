package com.ok1cdj.kvexed.core

/** Which section of the pack list a pack belongs to. */
enum class PackGroup { CANONICAL, VARIETY, EXTRA;
    companion object {
        fun from(s: String): PackGroup = when (s.lowercase()) {
            "canonical" -> CANONICAL
            "variety" -> VARIETY
            else -> EXTRA
        }
    }
}

/** Metadata for a pack, as listed in `index.json` (without the levels loaded). */
data class PackInfo(
    val id: String,
    val title: String,
    val author: String,
    val description: String,
    val levelCount: Int,
    val group: PackGroup,
    val order: Int,
)

/** A pack with its levels loaded from the corresponding `<id>.vxl`. */
data class LevelPack(
    val id: String,
    val title: String,
    val author: String,
    val description: String,
    val url: String,
    val group: PackGroup,
    val order: Int,
    val levels: List<Level>,
)
