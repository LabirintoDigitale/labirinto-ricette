package it.labirintodigitale.ricette.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import it.labirintodigitale.ricette.R
import java.text.DateFormat
import java.util.Date

@Composable
fun StatusBanner(state: UiState, onRetry: () -> Unit) {
    when (state.mode) {
        Mode.CONNECTING -> Column(Modifier.fillMaxWidth()) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(
                stringResource(R.string.connecting),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        Mode.OFFLINE -> Surface(color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Lock, null, Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.offline_title), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        lastSyncText(state.lastSync) + (state.offlineReason?.let { " · $it" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (state.syncing) {
                    CircularProgressIndicator(Modifier.padding(12.dp).size(20.dp), strokeWidth = 2.dp)
                } else {
                    TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
                }
            }
        }
        else -> Unit
    }
}

@Composable
fun lastSyncText(lastSync: Long?): String =
    if (lastSync == null) {
        stringResource(R.string.never_synced)
    } else {
        stringResource(
            R.string.synced_at,
            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(lastSync)),
        )
    }

/** Show (and consume) a one-shot message from the view model. */
@Composable
fun MessageEffect(state: UiState, vm: MainViewModel, host: SnackbarHostState) {
    LaunchedEffect(state.message) {
        val msg = state.message ?: return@LaunchedEffect
        vm.consumeMessage()
        host.showSnackbar(msg)
    }
}

fun siteColor(site: String): Color {
    // Stable colour per site, so cards without a photo still look distinct.
    val palette = listOf(
        0xFFE65100, 0xFFBF360C, 0xFF2E7D32, 0xFF00695C, 0xFF1565C0,
        0xFF6A1B9A, 0xFFAD1457, 0xFF4E342E, 0xFF37474F,
    )
    val h = site.fold(0) { acc, c -> acc * 31 + c.code } and 0x7fffffff
    return Color(palette[h % palette.size])
}
