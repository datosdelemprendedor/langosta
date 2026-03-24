package com.langosta.mission.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.langosta.mission.data.repository.ChannelRepository
import com.langosta.mission.domain.model.CHANNEL_CATALOG
import com.langosta.mission.domain.model.Channel
import kotlinx.coroutines.launch

@Composable
fun ChannelsScreen(modifier: Modifier = Modifier) {
    val repo = remember { ChannelRepository() }
    val scope = rememberCoroutineScope()
    var channels by remember { mutableStateOf(CHANNEL_CATALOG) }
    var isLoading by remember { mutableStateOf(true) }
    var snackMessage by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Cargar channels al entrar
    LaunchedEffect(Unit) {
        isLoading = true
        channels = repo.getChannels()
        isLoading = false
    }

    // Mostrar snackbar cuando hay mensaje
    LaunchedEffect(snackMessage) {
        snackMessage?.let {
            snackbarHostState.showSnackbar(it)
            snackMessage = null
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("📡 Channels",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold)
                    Text("Conecta Telegram, Discord y otros canales al gateway",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedButton(onClick = {
                    scope.launch {
                        isLoading = true
                        channels = repo.getChannels()
                        isLoading = false
                    }
                }) {
                    Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Actualizar")
                }
            }

            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(channels, key = { it.id }) { channel ->
                        ChannelCard(
                            channel = channel,
                            onConnect = { token, dmPolicy, groupPolicy ->
                                scope.launch {
                                    val result = repo.connectChannel(channel.id, token, dmPolicy, groupPolicy)
                                    result.fold(
                                        onSuccess = { msg ->
                                            snackMessage = "✅ $msg"
                                            channels = repo.getChannels()
                                        },
                                        onFailure = { snackMessage = "❌ ${it.message}" }
                                    )
                                }
                            },
                            onDisconnect = {
                                scope.launch {
                                    val result = repo.disconnectChannel(channel.id)
                                    result.fold(
                                        onSuccess = { msg ->
                                            snackMessage = "✅ $msg"
                                            channels = repo.getChannels()
                                        },
                                        onFailure = { snackMessage = "❌ ${it.message}" }
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelCard(
    channel: Channel,
    onConnect: (token: String, dmPolicy: String, groupPolicy: String) -> Unit,
    onDisconnect: () -> Unit
) {
    var expanded by remember { mutableStateOf(!channel.configured) }
    var token by remember { mutableStateOf("") }
    var tokenVisible by remember { mutableStateOf(false) }
    var dmPolicy by remember { mutableStateOf(channel.dmPolicy) }
    var groupPolicy by remember { mutableStateOf(channel.groupPolicy) }

    val statusColor = when {
        channel.connected  -> Color(0xFF4CAF50)
        channel.configured -> Color(0xFFFFC107)
        else               -> Color(0xFF78909C)
    }
    val statusLabel = when {
        channel.connected  -> "Conectado"
        channel.configured -> "Configurado"
        else               -> "Sin configurar"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

            // Fila superior: icon + nombre + status + botón
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(channel.icon, style = MaterialTheme.typography.titleLarge)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(channel.label,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold)
                        channel.botUsername?.let {
                            Text("@$it",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary)
                        }
                        channel.error?.let {
                            Text(it,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error)
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusChip(statusLabel)
                    Box(
                        Modifier.size(10.dp)
                            .clip(RoundedCornerShape(50))
                            .background(statusColor)
                    )
                    IconButton(onClick = { expanded = !expanded }) {
                        Icon(
                            if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = if (expanded) "Colapsar" else "Expandir"
                        )
                    }
                }
            }

            // Formulario de configuración (expandible)
            if (expanded) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

                // Token field
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text(channel.tokenLabel) },
                    placeholder = { Text(channel.tokenPlaceholder,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = if (tokenVisible) VisualTransformation.None
                                          else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { tokenVisible = !tokenVisible }) {
                            Icon(
                                if (tokenVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = null
                            )
                        }
                    }
                )

                // DM Policy
                PolicyDropdown(
                    label = "DM Policy",
                    selected = dmPolicy,
                    options = listOf("pairing", "open", "disabled"),
                    onSelect = { dmPolicy = it }
                )

                // Group Policy
                PolicyDropdown(
                    label = "Group Policy",
                    selected = groupPolicy,
                    options = listOf("disabled", "enabled"),
                    onSelect = { groupPolicy = it }
                )

                // Hint
                if (channel.hint.isNotBlank()) {
                    Text(
                        channel.hint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Botones acción
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            if (token.isNotBlank()) onConnect(token, dmPolicy, groupPolicy)
                        },
                        enabled = token.isNotBlank(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Link, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (channel.configured) "Reconectar" else "Conectar")
                    }

                    if (channel.configured) {
                        OutlinedButton(
                            onClick = onDisconnect,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.LinkOff, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Desconectar")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PolicyDropdown(label: String, selected: String, options: List<String>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                IconButton(onClick = { expanded = true }) {
                    Icon(Icons.Filled.ArrowDropDown, null)
                }
            }
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = { onSelect(option); expanded = false }
                )
            }
        }
    }
}
