package com.eflglobal.visitorsapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.DocumentScanner
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.eflglobal.visitorsapp.data.local.AppDatabase
import com.eflglobal.visitorsapp.data.local.TemplateCatalogPrefs
import com.eflglobal.visitorsapp.data.local.entity.DocumentTemplateEntity
import com.eflglobal.visitorsapp.data.sync.TemplateSyncScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Slate  = Color(0xFF334155)
private val Orange = Color(0xFFEF6C00)
private val Green  = Color(0xFF2E7D32)
private val GreenBg = Color(0xFFE8F5E9)

/**
 * Admin-panel view listing the OCR templates currently cached on this tablet
 * (the catalog synced from `GET /v1/ocr/templates`). Gives reception visibility
 * into *what* the document scanner can recognise, the catalog version in use and
 * when it last refreshed — plus a manual "sync now" that forces a fresh pull.
 *
 * Self-contained like [SyncStatusPanel]: reads Room + [TemplateCatalogPrefs]
 * directly, no ViewModel plumbing.
 */
@Composable
fun OcrTemplatesSection(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    var templates by remember { mutableStateOf<List<DocumentTemplateEntity>>(emptyList()) }
    var loading   by remember { mutableStateOf(true) }
    var syncing   by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableIntStateOf(0) }

    val catalogVersion = remember(refreshKey) { TemplateCatalogPrefs.catalogVersion(context) }
    val lastSyncAt     = remember(refreshKey) { TemplateCatalogPrefs.lastSyncAt(context) }

    // (Re)load the cached catalog whenever refreshKey changes.
    LaunchedEffect(refreshKey) {
        loading = true
        templates = withContext(Dispatchers.IO) {
            AppDatabase.getInstance(context).documentTemplateDao().getAll()
        }
        loading = false
    }

    // After a manual sync, WorkManager runs the pull off-thread; give it a few
    // seconds then reload so the new catalog shows without a manual refresh.
    LaunchedEffect(syncing) {
        if (syncing) {
            delay(4000)
            refreshKey++
            syncing = false
        }
    }

    val dateFmt = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ── Summary card ───────────────────────────────────────────────────
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.DocumentScanner, null, tint = Orange, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("Plantillas OCR", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        Surface(color = GreenBg, shape = RoundedCornerShape(20.dp)) {
                            Text(
                                text = "${templates.size} sincronizadas",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = Green,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    InfoLine("Versión del catálogo", catalogVersion ?: "—")
                    InfoLine(
                        "Última sincronización",
                        if (lastSyncAt > 0L) dateFmt.format(Date(lastSyncAt)) else "Nunca"
                    )

                    Spacer(Modifier.height(12.dp))

                    Button(
                        onClick = {
                            TemplateSyncScheduler.enqueueNow(context)
                            syncing = true
                        },
                        enabled = !syncing,
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Orange, contentColor = Color.White),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        if (syncing) {
                            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Sincronizando…", fontWeight = FontWeight.SemiBold)
                        } else {
                            Icon(Icons.Filled.CloudSync, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Sincronizar ahora", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        // ── Empty / loading / list ─────────────────────────────────────────
        if (loading) {
            item {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Orange, strokeWidth = 3.dp, modifier = Modifier.size(28.dp))
                }
            }
        } else if (templates.isEmpty()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(GreenBg, RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.CheckCircle, null, tint = Green, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "No hay plantillas en caché todavía. Pulsa \"Sincronizar ahora\" con conexión.",
                        color = Green,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        } else {
            items(templates, key = { it.id }) { t -> TemplateRow(t) }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            "$label:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun TemplateRow(t: DocumentTemplateEntity) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = t.name?.takeIf { it.isNotBlank() } ?: t.code ?: t.id.take(8),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Slate,
                    modifier = Modifier.weight(1f)
                )
                Surface(color = Orange.copy(alpha = 0.12f), shape = RoundedCornerShape(8.dp)) {
                    Text(
                        text = "v${t.version}",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Orange
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            val meta = listOfNotNull(
                t.countryId?.takeIf { it.isNotBlank() },
                t.documentKind?.takeIf { it.isNotBlank() },
                t.side.takeIf { it.isNotBlank() },
                t.extractionMethod.takeIf { it.isNotBlank() }
            ).joinToString(" · ")
            Text(
                text = meta.ifBlank { "—" },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}
