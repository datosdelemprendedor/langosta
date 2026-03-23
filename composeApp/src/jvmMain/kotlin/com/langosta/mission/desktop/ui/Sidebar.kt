package com.langosta.mission.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.langosta.mission.desktop.AppDestination

@Composable
fun Sidebar(
    selected: AppDestination,
    onSelect: (AppDestination) -> Unit,
    isConnected: Boolean = false,
    modifier: Modifier = Modifier
) {
    val expandedSections = remember {
        mutableStateMapOf<AppDestination, Boolean>().apply {
            AppDestination.entries.filter { it.isParent }.forEach { put(it, true) }
        }
    }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(220.dp)
            .background(MaterialTheme.colorScheme.surface)
            .padding(vertical = 16.dp, horizontal = 8.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Logo + indicador de conexión
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Column {
                Text(
                    text = "🦞 Langosta",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Mission Control",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (isConnected) Color(0xFF4CAF50) else Color(0xFFF44336))
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Spacer(modifier = Modifier.height(16.dp))

        // Secciones
        AppDestination.entries.filter { it.isParent }.forEach { section ->
            val isExpanded = expandedSections[section] ?: true
            val hasActiveChild = section.children.any { it == selected }
            val isSelected = selected == section

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        if (section.children.isEmpty()) onSelect(section)
                        else expandedSections[section] = !isExpanded
                    }
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primaryContainer
                        else Color.Transparent
                    )
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(section.icon, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = section.label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isSelected || hasActiveChild) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isSelected || hasActiveChild)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onBackground
                    )
                }
                if (section.children.isNotEmpty()) {
                    Text(
                        text = if (isExpanded) "▾" else "▸",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            if (isExpanded && section.children.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                section.children.forEach { child ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onSelect(child) }
                            .background(
                                if (selected == child) MaterialTheme.colorScheme.primaryContainer
                                else Color.Transparent
                            )
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(RoundedCornerShape(50))
                                .background(
                                    if (selected == child) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outline
                                )
                        )
                        Text(
                            text = child.label,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (selected == child)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}
