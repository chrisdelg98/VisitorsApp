package com.eflglobal.visitorsapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eflglobal.visitorsapp.R
import com.eflglobal.visitorsapp.data.local.AppDatabase
import com.eflglobal.visitorsapp.data.sync.SyncScheduler
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow

/**
 * Self-contained badge that surfaces local rows still waiting to reach the
 * backend. Reads the count from [com.eflglobal.visitorsapp.data.local.dao.VisitDao.getPendingVisitsCountFlow]
 * so it updates automatically when the [SyncScheduler] worker finishes a pass.
 *
 * Renders nothing while the count is zero, so it disappears silently on a
 * healthy connection. While there are pending rows the user can tap
 * "Sync now" to nudge the worker (useful right after recovering connectivity).
 */
@Composable
fun SyncStatusBadge(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val flow: Flow<Int> = remember(context) {
        AppDatabase.getInstance(context).visitDao().getPendingVisitsCountFlow()
    }
    val pending by flow.collectAsState(initial = 0)

    if (pending <= 0) return

    // Brief in-progress feedback after a manual "Sync now" tap.
    var isSyncing by remember { mutableStateOf(false) }
    LaunchedEffect(isSyncing) {
        if (isSyncing) {
            delay(2000)
            isSyncing = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFFFF3E0))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Filled.CloudUpload,
                contentDescription = null,
                tint = Color(0xFFEF6C00),
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.sync_pending_title),
                    color = Color(0xFFEF6C00),
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = stringResource(R.string.sync_pending_count, pending),
                    color = Color(0xFFEF6C00),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Spacer(Modifier.width(12.dp))
            Button(
                onClick = {
                    SyncScheduler.enqueueNow(context)
                    isSyncing = true
                },
                enabled = !isSyncing,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFEF6C00),
                    contentColor   = Color.White
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 16.dp, vertical = 8.dp
                )
            ) {
                if (isSyncing) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp)
                    )
                } else {
                    Text(
                        text = stringResource(R.string.sync_now),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

