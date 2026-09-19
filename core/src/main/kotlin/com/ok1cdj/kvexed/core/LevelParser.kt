package com.ok1cdj.kvexed.core

/**
 * Loads bundled level data from module resources under `/levels/`.
 *
 * Everything is read via [Class.getResourceAsStream], which works identically in
 * a plain JVM test and inside the APK — no `AssetManager`, no `Context`.
 */
object LevelParser {

    private const val DIR = "/levels"

    /** Read `index.json` into pack metadata, in display order. */
    fun loadIndex(): List<PackInfo> {
        val text = readResource("$DIR/index.json")
        return Json.parseArray(text).map { entry ->
            @Suppress("UNCHECKED_CAST")
            val m = entry as Map<String, Any?>
            PackInfo(
                id = m["id"] as String,
                title = m["title"] as String,
                author = m["author"] as String,
                description = m["description"] as String,
                levelCount = (m["levels"] as Double).toInt(),
                group = PackGroup.from(m["group"] as String),
                order = (m["order"] as Double).toInt(),
            )
        }.sortedWith(compareBy({ it.order }, { it.title }))
    }

    /** Load one pack (its `<id>.vxl`) with all levels parsed. */
    fun loadPack(info: PackInfo): LevelPack {
        val text = readResource("$DIR/${info.id}.vxl")
        var author = info.author
        var url = ""
        var description = info.description
        val levels = ArrayList<Level>()

        for (raw in text.lineSequence()) {
            val line = raw.ifEmpty { continue }
            if (line.startsWith("#")) {
                val body = line.removePrefix("#").trim()
                val sep = body.indexOf(':')
                if (sep > 0) {
                    val key = body.substring(0, sep).trim()
                    val value = body.substring(sep + 1).trim()
                    when (key) {
                        "Author" -> author = value
                        "URL" -> url = value
                        "Description" -> description = value
                    }
                }
                continue
            }
            // number;title;board;solution[;bestKnown]
            val parts = line.split(';')
            require(parts.size == 4 || parts.size == 5) { "${info.id}: malformed level line '$line'" }
            val solution = parts[3]
            val bestKnown = parts.getOrNull(4)?.takeIf { it.isNotEmpty() }
            levels.add(
                Level(
                    title = parts[1],
                    board = parts[2],
                    solution = solution,
                    par = solution.length / 2,
                    bestKnown = bestKnown,
                )
            )
        }

        return LevelPack(
            id = info.id, title = info.title, author = author, description = description,
            url = url, group = info.group, order = info.order, levels = levels,
        )
    }

    /** Load every pack in index order. */
    fun loadAllPacks(): List<LevelPack> = loadIndex().map(::loadPack)

    private fun readResource(path: String): String =
        (LevelParser::class.java.getResourceAsStream(path)
            ?: error("missing resource $path"))
            .bufferedReader(Charsets.UTF_8).use { it.readText() }
}
