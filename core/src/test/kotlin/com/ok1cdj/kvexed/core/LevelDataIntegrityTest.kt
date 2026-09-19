package com.ok1cdj.kvexed.core

import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Structural checks on the bundled level data — independent of whether a level
 * is *solvable* (that's [SolutionVerificationTest]). One dynamic test per pack;
 * failures are collected so a bad pack reports every offending level at once.
 */
class LevelDataIntegrityTest {

    private val packs = LevelParser.loadAllPacks()
    private val index = LevelParser.loadIndex()

    @TestFactory
    fun everyLevelIsWellFormed(): List<DynamicTest> = packs.map { pack ->
        DynamicTest.dynamicTest("${pack.id} (${pack.levels.size})") {
            val failures = mutableListOf<String>()
            pack.levels.forEachIndexed { n, level ->
                try {
                    val board = level.toBoard() // enforces 10×8, legal chars only

                    // Starting board must already be settled — gravity is a no-op.
                    if (Engine.settled(board) != board) failures += "$n/${level.title}: board not settled"

                    // ...and must contain no pre-existing match.
                    if (Engine.hasMatch(board)) failures += "$n/${level.title}: board has a pre-existing match"

                    // Par is exactly half the (even-length) solution.
                    if (level.solution.length % 2 != 0) failures += "$n/${level.title}: odd-length solution"
                    if (level.solution.length / 2 != level.par) failures += "$n/${level.title}: par != solution.length/2"

                    // Every move pair parses, is in range, and has exactly one uppercase letter.
                    level.solutionMoves()
                } catch (e: Exception) {
                    failures += "$n/${level.title}: ${e.message}"
                }
            }
            assertTrue(failures.isEmpty()) {
                "${failures.size}/${pack.levels.size} malformed in ${pack.id}:\n" + failures.joinToString("\n")
            }
        }
    }

    @Test
    fun indexAgreesWithVxlContents() {
        val failures = mutableListOf<String>()
        val byId = packs.associateBy { it.id }
        for (info in index) {
            val pack = byId[info.id]
            if (pack == null) { failures += "${info.id}: in index but no .vxl loaded"; continue }
            if (pack.levels.size != info.levelCount) {
                failures += "${info.id}: index says ${info.levelCount} levels, .vxl has ${pack.levels.size}"
            }
        }
        // No orphan .vxl packs missing from the index.
        val indexIds = index.map { it.id }.toSet()
        for (pack in packs) if (pack.id !in indexIds) failures += "${pack.id}: .vxl not listed in index.json"

        assertTrue(failures.isEmpty()) { failures.joinToString("\n") }
    }

    @Test
    fun packGroupsAndOrderAreSane() {
        // 9 canonical packs, ordered 1..9; every variety pack ordered above them.
        val canonical = index.filter { it.group == PackGroup.CANONICAL }
        assertTrue(canonical.size == 9) { "expected 9 canonical packs, got ${canonical.size}" }
        assertTrue(canonical.map { it.order }.sorted() == (1..9).toList()) {
            "canonical order must be 1..9, was ${canonical.map { it.order }.sorted()}"
        }
    }

    // index.json is the single source of truth for counts — these tests check
    // CONSISTENCY, never a hard-coded expected number of levels.
    @Test
    fun everyPackIsNonEmptyWithContiguousLevelNumbers() {
        val failures = mutableListOf<String>()
        for (info in index) {
            val body = readResource("/levels/${info.id}.vxl")
            val numbers = body.lineSequence()
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .map { it.substringBefore(';').toInt() }
                .toList()
            if (numbers.isEmpty()) failures += "${info.id}: pack has no levels"
            // level numbers must be 0,1,2,…,N-1 with no holes and no reordering
            if (numbers != numbers.indices.toList()) {
                failures += "${info.id}: level numbers not contiguous from 0: $numbers"
            }
        }
        assertTrue(failures.isEmpty()) { failures.joinToString("\n") }
    }

    @Test
    fun packIdsAreUnique() {
        val dupes = index.groupingBy { it.id }.eachCount().filterValues { it > 1 }.keys
        assertTrue(dupes.isEmpty()) { "duplicate pack ids in index.json: $dupes" }
    }

    private fun readResource(path: String): String =
        (javaClass.getResourceAsStream(path) ?: error("missing resource $path"))
            .bufferedReader(Charsets.UTF_8).use { it.readText() }
}
