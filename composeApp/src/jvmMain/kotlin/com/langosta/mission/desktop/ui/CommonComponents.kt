package com.langosta.mission.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Chip de status reutilizable en AgentsScreen, SkillsScreen, DashboardScreen.
 * Colores: active/busy=verde, idle=gris, error=rojo, otros=ambar
 */
@Composable
fun StatusChip(status: String, modifier: Modifier = Modifier) {
    val (bg, fg) = when (status.lowercase()) {
        "active", "running", "online"  -> Color(0xFF4CAF50) to Color.White
        "busy", "processing"           -> Color(0xFFFFC107) to Color(0xFF1A1A1A)
        "error", "failed", "offline"   -> Color(0xFFF44336) to Color.White
        "idle"                         -> Color(0xFF78909C) to Color.White
        else                           -> Color(0xFF90A4AE) to Color.White
    }
    Text(
        text = status,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Medium,
        color = fg,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}

/** Formatea tokens a K/M para mostrar en cards */
fun formatTokens(n: Long): String = when {
    n >= 1_000_000 -> "%.1fM".format(n / 1_000_000.0)
    n >= 1_000     -> "%.1fK".format(n / 1_000.0)
    else           -> n.toString()
}
