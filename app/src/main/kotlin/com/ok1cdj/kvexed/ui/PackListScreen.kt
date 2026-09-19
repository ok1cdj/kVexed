package com.ok1cdj.kvexed.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ok1cdj.kvexed.core.PackGroup
import com.ok1cdj.kvexed.core.PackInfo
import com.mudita.mmd.components.text.TextMMD

/**
 * The pack list: 9 canonical packs on top in difficulty order, then a collapsible
 * "Variety 03–41" section. (No "extra" group — the bundled corpus has none.)
 */
@Composable
fun PackListScreen(vm: GameViewModel, onAbout: () -> Unit) {
    val canonical = vm.packs.filter { it.group == PackGroup.CANONICAL }
    val variety = vm.packs.filter { it.group == PackGroup.VARIETY }
    val extra = vm.packs.filter { it.group == PackGroup.EXTRA }
    var varietyOpen by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
        Row(
            modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextMMD(text = "Vexed", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            InfoButton(onClick = onAbout)
        }

        LazyColumn(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            if (vm.progress.lastPackId != null) {
                item {
                    PackRow(title = "Continue", subtitle = "Resume last game", solved = null,
                        onClick = vm::continueGame)
                    Spacer(Modifier.height(12.dp))
                }
            }

            items(canonical) { info -> PackItem(info, vm, onOpen = { vm.openPack(info.id) }) }

            item {
                SectionHeader(
                    text = if (varietyOpen) "Variety 03–41 ▾" else "Variety 03–41 ▸",
                    onClick = { varietyOpen = !varietyOpen },
                )
            }
            if (varietyOpen) {
                items(variety) { info -> PackItem(info, vm, onOpen = { vm.openPack(info.id) }) }
            }

            if (extra.isNotEmpty()) {
                item { SectionHeader(text = "Extra", onClick = {}) }
                items(extra) { info -> PackItem(info, vm, onOpen = { vm.openPack(info.id) }) }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun PackItem(info: PackInfo, vm: GameViewModel, onOpen: () -> Unit) {
    val solved = vm.packProgress(info.id).solvedCount
    PackRow(title = info.title, subtitle = null, solved = "$solved/${info.levelCount}", onClick = onOpen)
}

@Composable
private fun PackRow(title: String, subtitle: String?, solved: String?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .border(1.dp, Color.Black, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            TextMMD(text = title, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            if (subtitle != null) TextMMD(text = subtitle, fontSize = 13.sp)
        }
        if (solved != null) TextMMD(text = solved, fontSize = 15.sp)
    }
}

@Composable
private fun SectionHeader(text: String, onClick: () -> Unit) {
    TextMMD(
        text = text,
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
    )
}
