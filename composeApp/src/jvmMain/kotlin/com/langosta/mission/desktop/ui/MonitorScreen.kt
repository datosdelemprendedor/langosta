package com.langosta.mission.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SignalWifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.langosta.mission.desktop.DashboardViewModel

@Composable
fun MonitorScreen(viewModel: DashboardViewModel, modifier: Modifier = Modifier) {
    val events    by viewModel.incidentEvents.collectAsState()
    val wsState   by viewModel.wsConnectionState.collectAsState()
    val listState = rememberLazyListState()
    var autoScroll  by remember { mutableStateOf(true) }
    var filterText  by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { viewModel.startIncidentStream() }
    LaunchedEffect(events.size) {
        if (autoScroll && events.isNotEmpty()) listState.animateScrollToItem(0)
    }

    Column(modifier = modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {

        // Header
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Monitor de Eventos", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val wsColor = if (wsState.contains("OK")) Color(0xFF4CAF50) else Color(0xFF78909C)
                    Box(Modifier.size(8.dp).clip(RoundedCornerShape(50)).background(wsColor))
                    Text(wsState, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = filterText,
                    onValueChange = { filterText = it },
                    placeholder = { Text("Filtrar eventos...", style = MaterialTheme.typography.bodySmall) },
                    modifier = Modifier.width(220.dp).height(48.dp),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall,
                    leadingIcon = { Icon(Icons.Filled.Search, null, modifier = Modifier.size(16.dp)) },
                    shape = RoundedCornerShape(8.dp)
                )
                FilterChip(
                    selected = autoScroll,
                    onClick = { autoScroll = !autoScroll },
                    label = { Text("Auto-scroll", style = MaterialTheme.typography.labelSmall) },
                    leadingIcon = if (autoScroll) ({
                        Icon(Icons.Filled.KeyboardArrowDown, null, modifier = Modifier.size(14.dp))
                    }) else null
                )
                if (events.isNotEmpty()) {
                    OutlinedButton(onClick = { /* viewModel.clearEvents() */ }) {
                        Icon(Icons.Filled.Delete, null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Limpiar", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        // Contador
        Box(
            modifier = Modifier.clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text("${events.size} eventos en buffer", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        val filtered = if (filterText.isBlank()) events
        else events.filter { it.contains(filterText, ignoreCase = true) }

        if (filtered.isEmpty()) {
            Box(Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.SignalWifiOff, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        if (filterText.isBlank()) "Esperando eventos del WebSocket..."
                        else "Sin resultados para \"$filterText\"",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            Card(modifier = Modifier.fillMaxSize(), shape = RoundedCornerShape(12.dp), elevation = CardDefaults.cardElevation(1.dp)) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().background(Color(0xFF0D1117)).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(filtered) { event -> EventLine(event) }
                }
            }
        }
    }
}

@Composable
private fun EventLine(raw: String) {
    val isError   = raw.contains("error",      ignoreCase = true) || raw.contains("fail",       ignoreCase = true)
    val isWarning = raw.contains("warn",       ignoreCase = true) || raw.contains("disconnect", ignoreCase = true)
    val isSuccess = raw.contains("connect",    ignoreCase = true) || raw.contains("ok",         ignoreCase = true)
    val color = when {
        isError   -> Color(0xFFEF9A9A)
        isWarning -> Color(0xFFFFCC80)
        isSuccess -> Color(0xFF81C784)
        else      -> Color(0xFFB0BEC5)
    }
    val prefix = when { isError -> "ERR "; isWarning -> "WRN "; isSuccess -> "OK  "; else -> "INF " }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(prefix, style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp), color = color, fontWeight = FontWeight.Bold)
        Text(raw,    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp), color = color.copy(alpha = 0.9f))
    }
}
