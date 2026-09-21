package com.ok1cdj.kvexed.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ok1cdj.kvexed.R
import com.ok1cdj.kvexed.core.LevelParser
import com.mudita.mmd.components.text.TextMMD

/**
 * Grid of level numbers for a pack. Solved levels are filled (inverted); a level
 * solved with a hint carries a small dot. A "Continue" chip jumps to the last
 * level played in this pack.
 */
@Composable
fun LevelListScreen(vm: GameViewModel, packId: String) {
    val info = vm.packs.first { it.id == packId }
    val count = info.levelCount
    val prog = vm.packProgress(packId)

    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
        Row(
            modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BackButton(onClick = vm::backToPacks)
            Spacer(Modifier.width(4.dp))
            TextMMD(text = "${info.title} · ${prog.solvedCount}/$count",
                fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }

        val resume = prog.resumeLevel
        if (resume != null) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                Box(
                    modifier = Modifier
                        .border(1.dp, Color.Black, RoundedCornerShape(8.dp))
                        .clickable { vm.openLevel(packId, resume) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    TextMMD(text = stringResource(R.string.continue_level, resume + 1),
                        fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(5),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items((0 until count).toList()) { index ->
                val stat = prog.levels[index]
                LevelCell(
                    number = index + 1,
                    solved = stat?.solved == true,
                    hinted = stat?.hintUsed == true,
                    onClick = { vm.openLevel(packId, index) },
                )
            }
        }
    }
}

@Composable
private fun LevelCell(number: Int, solved: Boolean, hinted: Boolean, onClick: () -> Unit) {
    val bg = if (solved) Color.Black else Color.White
    val fg = if (solved) Color.White else Color.Black
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .border(1.dp, Color.Black, RoundedCornerShape(6.dp))
            .background(bg, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = number.toString(), color = fg, fontSize = 16.sp,
            fontWeight = if (solved) FontWeight.Bold else FontWeight.Normal,
        )
        if (hinted) {
            Text(text = "•", color = fg, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.TopEnd).padding(horizontal = 4.dp))
        }
    }
}
