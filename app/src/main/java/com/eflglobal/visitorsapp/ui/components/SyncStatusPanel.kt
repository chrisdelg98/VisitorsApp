package com.eflglobal.visitorsapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.work.WorkInfo
import com.eflglobal.visitorsapp.data.local.AppDatabase
import com.eflglobal.visitorsapp.data.local.dao.UnsyncedVisitDto
import com.eflglobal.visitorsapp.data.sync.SyncScheduler

private val AmberBg   = Color(0xFFFFF3E0)
private val Amber     = Color(0xFFEF6C00)
private val RedBg     = Color(0xFFFDECEA)
private val Red       = Color(0xFFC62828)
private val GreenBg   = Color(0xFFE8F5E9)
private val Green     = Color(0xFF2E7D32)

/**
 * Admin-panel card that gives reception full visibility into the upload queue:
 * how many rows are stuck, which ones, and — crucially — *why* each one failed
 * (the backend / network error per row). The "Sincronizar ahora" button forces
 * a real sync pass (see [SyncScheduler.enqueueNow]) and reports the live outcome
 * (running / done / failed) so a manual retry isn't a silent black box.
 *
 * Self-contained like [SyncStatusBadge]: reads straight from the DB flow and the
 * WorkManager status flow, so it stays in sync without any ViewModel plumbing.
 */
@Composable
fun SyncStatusPanel(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val dao = remember(context) { AppDatabase.getInstance(context).visitDao() }

    val rows by remember(context) { dao.getUnsyncedVisitDetailsFlow() }
        .collectAsState(initial = emptyList())
    val workInfos by remember(context) { SyncScheduler.statusFlow(context) }
        .collectAsState(initial = emptyList())

    // Immediate spinner on tap; cleared once WorkManager reports a terminal state.
    var justTapped by remember { mutableStateOf(false) }
    val workState = workInfos.firstOrNull()?.state
    val running = justTapped ||
        workState == WorkInfo.State.RUNNING ||
        workState == WorkInfo.State.ENQUEUED
    if (workState == WorkInfo.State.SUCCEEDED || workState == WorkInfo.State.FAILED) {
        justTapped = false
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            // ── Header: title + count ──────────────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (rows.isEmpty()) Icons.Filled.CloudDone else Icons.Filled.CloudUpload,
                    contentDescription = null,
                    tint = if (rows.isEmpty()) Green else Amber,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "Sincronización",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.weight(1f))
                if (rows.isNotEmpty()) {
                    Surface(color = AmberBg, shape = RoundedCornerShape(20.dp)) {
                        Text(
                            text = "${rows.size} sin subir",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = Amber,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // ── Live outcome line ──────────────────────────────────────────
            StatusLine(running = running, workState = workState, pending = rows.size)

            // ── Per-row detail (name · status · attempts · error) ──────────
            if (rows.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                rows.forEach { row ->
                    UnsyncedRow(row)
                    Spacer(Modifier.height(8.dp))
                }

                Button(
                    onClick = {
                        SyncScheduler.enqueueNow(context)
                        justTapped = true
                    },
                    enabled = !running,
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Color.White),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    if (running) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Sincronizando…", fontWeight = FontWeight.SemiBold)
                    } else {
                        Icon(Icons.Filled.CloudUpload, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Sincronizar ahora", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusLine(running: Boolean, workState: WorkInfo.State?, pending: Int) {
    when {
        running -> InfoBanner(
            bg = AmberBg, fg = Amber, icon = Icons.Filled.CloudUpload,
            text = "Sincronizando… espera unos segundos."
        )
        pending == 0 -> InfoBanner(
            bg = GreenBg, fg = Green, icon = Icons.Filled.CheckCircle,
            text = "Todo al día. No hay nada pendiente de subir."
        )
        workState == WorkInfo.State.FAILED -> InfoBanner(
            bg = RedBg, fg = Red, icon = Icons.Filled.ErrorOutline,
            text = "La última sincronización falló (sesión/credencial). " +
                "Puede que debas reactivar la estación."
        )
        workState == WorkInfo.State.SUCCEEDED -> InfoBanner(
            bg = AmberBg, fg = Amber, icon = Icons.Filled.ErrorOutline,
            text = "La sincronización terminó pero $pending quedaron sin subir. " +
                "Revisa el detalle de cada una abajo."
        )
        else -> InfoBanner(
            bg = AmberBg, fg = Amber, icon = Icons.Filled.CloudUpload,
            text = "$pending registro(s) esperan subir al servidor."
        )
    }
}

@Composable
private fun InfoBanner(
    bg: Color,
    fg: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text = text, color = fg, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun UnsyncedRow(row: UnsyncedVisitDto) {
    val failed = row.syncStatus == "failed"
    val name = listOfNotNull(row.firstName, row.lastName)
        .joinToString(" ").ifBlank { "Visita ${row.visitId.take(8)}" }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (failed) RedBg else AmberBg,
                RoundedCornerShape(10.dp)
            )
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Surface(
                color = if (failed) Red.copy(alpha = 0.12f) else Amber.copy(alpha = 0.15f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = if (failed) "FALLÓ" else "EN COLA",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (failed) Red else Amber
                )
            }
        }

        Text(
            text = "Intentos: ${row.syncAttempts}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.padding(top = 2.dp)
        )

        // The "why" — the exact error the backend / network returned.
        row.lastSyncError?.takeIf { it.isNotBlank() }?.let { err ->
            Spacer(Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.04f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Text(
                    text = err,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp
                    ),
                    color = if (failed) Red else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }
    }
}
