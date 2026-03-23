package com.langosta.mission.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.langosta.mission.data.TaskHistoryEntry
import java.text.SimpleDateFormat
import java.util.Date

@Composable
fun TaskHistoryPanel(
    entries: List<TaskHistoryEntry>,
    onClear: () -> Unit,
    onExportCsv: () -> Unit
) {
    val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Historial de Tareas",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onExportCsv) {
                    Text("Exportar CSV")
                }
                OutlinedButton(
                    onClick = onClear,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Limpiar")
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (entries.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No hay tareas completadas aún",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            // Cabecera de la tabla
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                TableCell("Tarea", weight = 3f, bold = true)
                TableCell("Agente", weight = 2f, bold = true)
                TableCell("Duración", weight = 1f, bold = true)
                TableCell("Resultado", weight = 1f, bold = true)
                TableCell("Fecha", weight = 2f, bold = true)
            }

            HorizontalDivider()

            // Filas
            LazyColumn {
                items(entries) { entry ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TableCell(entry.title, weight = 3f)
                        TableCell(entry.agentName, weight = 2f)
                        TableCell(formatDuration(entry.durationSeconds), weight = 1f)
                        ResultBadge(entry.result, weight = 1f)
                        TableCell(
                            dateFormat.format(Date(entry.completedAt)),
                            weight = 2f
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun RowScope.TableCell(
    text: String,
    weight: Float,
    bold: Boolean = false
) {
    Text(
        text = text,
        modifier = Modifier.weight(weight),
        fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
        fontSize = 13.sp,
        maxLines = 1
    )
}

@Composable
private fun RowScope.ResultBadge(result: String, weight: Float) {
    val (bgColor, textColor) = when (result) {
        "COMPLETED" -> Color(0xFF2E7D32) to Color.White
        "FAILED"    -> Color(0xFFC62828) to Color.White
        else        -> Color.Gray to Color.White
    }
    Box(modifier = Modifier.weight(weight)) {
        Surface(
            color = bgColor,
            shape = MaterialTheme.shapes.small
        ) {
            Text(
                text = result,
                color = textColor,
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

private fun formatDuration(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return when {
        h > 0  -> "${h}h ${m}m"
        m > 0  -> "${m}m ${s}s"
        else   -> "${s}s"
    }
}
