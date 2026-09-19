package com.ok1cdj.kvexed.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ok1cdj.kvexed.R
import com.mudita.mmd.components.text.TextMMD

// Header icon sizing — bumped up so the ⓘ / back targets are comfortable on the
// Kompakt's e-ink panel (the small glyph version was hard to hit).
private val TOUCH_TARGET = 48.dp
private val INFO_ICON = 34.dp

/** The About (ⓘ) button: the crisp ic_info vector in a 48dp touch target. */
@Composable
fun InfoButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(TOUCH_TARGET).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_info),
            contentDescription = "About",
            modifier = Modifier.size(INFO_ICON),
        )
    }
}

/** The Settings (gear/sliders) button in a 48dp touch target. */
@Composable
fun SettingsButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(TOUCH_TARGET).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_settings),
            contentDescription = "Settings",
            modifier = Modifier.size(INFO_ICON),
        )
    }
}

/** The back (‹) button: a large chevron in a 48dp touch target. */
@Composable
fun BackButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(TOUCH_TARGET).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        TextMMD(text = "‹", fontSize = 30.sp, fontWeight = FontWeight.Bold)
    }
}
