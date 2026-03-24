package com.langosta.mission.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.langosta.mission.desktop.DashboardViewModel
import com.langosta.mission.domain.model.Agent
import com.langosta.mission.domain.model.AgentSkill

@Composable
fun SkillsScreen(
    agent: Agent,
    viewModel: DashboardViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Estado local optimista mientras no hay endpoint real
    var skills by remember(agent.id) { mutableStateOf(agent.skills) }

    Column(modifier = modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {

        // Header con back
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Volver")
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(agent.emoji, style = MaterialTheme.typography.headlineSmall)
                    Text(agent.name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold)
                    StatusChip(agent.status.label)
                }
                Text("Modelo: ${agent.model}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        HorizontalDivider()

        // Resumen de skills
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SkillPill("Total",     "${skills.size}",                          MaterialTheme.colorScheme.primary)
            SkillPill("Activos",   "${skills.count { it.enabled }}",          Color(0xFF4CAF50))
            SkillPill("Inactivos", "${skills.count { !it.enabled }}",         Color(0xFF78909C))
        }

        if (skills.isEmpty()) {
            // Estado vacío
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Filled.Build, null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Este agente no tiene skills configurados",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Los skills estarán disponibles cuando el gateway los exponga via /api/agents/{id}/skills",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(skills, key = { it.id }) { skill ->
                    SkillCard(
                        skill = skill,
                        onToggle = { enabled ->
                            // Actualización optimista local
                            skills = skills.map {
                                if (it.id == skill.id) it.copy(enabled = enabled) else it
                            }
                            // Persistir en el backend (cuando exista el endpoint)
                            viewModel.toggleSkill(agent.id, skill.id, enabled)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SkillCard(skill: AgentSkill, onToggle: (Boolean) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    if (skill.enabled) Icons.Filled.CheckCircle else Icons.Filled.Info,
                    contentDescription = null,
                    tint = if (skill.enabled) Color(0xFF4CAF50) else Color(0xFF78909C),
                    modifier = Modifier.size(20.dp)
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(skill.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium)
                    if (skill.description.isNotBlank()) {
                        Text(skill.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    skill.binding?.let {
                        Text("binding: $it",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Switch(
                checked = skill.enabled,
                onCheckedChange = onToggle
            )
        }
    }
}

@Composable
private fun SkillPill(label: String, value: String, color: Color) {
    Row(
        modifier = Modifier.clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
