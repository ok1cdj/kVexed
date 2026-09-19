package com.ok1cdj.kvexed.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.ok1cdj.kvexed.BuildConfig
import com.ok1cdj.kvexed.R
import com.mudita.mmd.components.buttons.ButtonMMD
import com.mudita.mmd.components.text.TextMMD

private const val COFFEE_URL = "https://www.buymeacoffee.com/ok1cdj"
private const val GITHUB_URL = "https://github.com/ok1cdj/kVexed"

@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color.Black, RoundedCornerShape(12.dp))
                .background(Color.White, RoundedCornerShape(12.dp))
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            TextMMD(text = stringResource(R.string.about_title), fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(2.dp))
            TextMMD(text = stringResource(R.string.about_version, BuildConfig.VERSION_NAME), fontSize = 13.sp)
            TextMMD(text = stringResource(R.string.about_author), fontSize = 13.sp)
            Spacer(Modifier.height(10.dp))
            TextMMD(text = stringResource(R.string.about_desc), fontSize = 13.sp)
            Spacer(Modifier.height(10.dp))
            TextMMD(text = stringResource(R.string.about_license), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            TextMMD(text = stringResource(R.string.about_credits), fontSize = 12.sp)
            Spacer(Modifier.height(14.dp))

            LinkButton(stringResource(R.string.about_coffee)) { openUrl(context, COFFEE_URL) }
            Spacer(Modifier.height(8.dp))
            LinkButton(stringResource(R.string.about_github)) { openUrl(context, GITHUB_URL) }
            Spacer(Modifier.height(8.dp))
            ButtonMMD(onClick = onDismiss, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
                TextMMD(text = stringResource(R.string.close), fontSize = 15.sp)
            }
        }
    }
}

@Composable
private fun LinkButton(text: String, onClick: () -> Unit) {
    ButtonMMD(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().border(1.dp, Color.Black, RoundedCornerShape(8.dp)),
        shape = RoundedCornerShape(8.dp),
    ) {
        TextMMD(text = text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

private fun openUrl(context: android.content.Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}
