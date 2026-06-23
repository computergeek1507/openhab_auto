package com.openhab.auto

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settings = OpenHabAutoApp.instance.settingsManager
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Color(0xFFE64A19),
                    onPrimary = Color.White,
                )
            ) {
                SettingsScreen(settings)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(settings: SettingsManager) {
    var demoMode by remember { mutableStateOf(settings.demoMode) }
    var useRemote by remember { mutableStateOf(settings.useRemote) }
    var url by remember { mutableStateOf(settings.url) }
    var token by remember { mutableStateOf(settings.token) }
    var email by remember { mutableStateOf(settings.email) }
    var password by remember { mutableStateOf(settings.password) }
    var group by remember { mutableStateOf(settings.group) }
    var allowSelfSigned by remember { mutableStateOf(settings.allowSelfSigned) }

    // Build an item source for the currently selected mode from the live form values.
    fun buildSource(): OpenHabSource =
        when {
            demoMode -> DemoItemSource
            useRemote -> OpenHabService(OpenHabService.REMOTE_URL, email.trim(), password)
            else -> OpenHabService(url.trim(), token.trim(), allowSelfSigned = allowSelfSigned)
        }
    var statusMessage by remember { mutableStateOf("") }
    var statusColor by remember { mutableStateOf(Color.Unspecified) }
    var testing by remember { mutableStateOf(false) }
    var items by remember { mutableStateOf<List<OpenHabItem>>(emptyList()) }
    var loadingItems by remember { mutableStateOf(false) }
    var togglingItem by remember { mutableStateOf<String?>(null) }
    var expandedItem by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // Move an item up (-1) or down (+1) in the list and persist the new order.
    fun moveItem(from: Int, direction: Int) {
        val to = from + direction
        if (to < 0 || to >= items.size) return
        val reordered = items.toMutableList()
        reordered.add(to, reordered.removeAt(from))
        items = reordered
        settings.itemOrder = reordered.map { it.name }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("OH Auto") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Top,
        ) {
            val segmentColors = SegmentedButtonDefaults.colors(
                activeContainerColor = Color(0xFFE64A19),
                activeContentColor = Color.White,
                activeBorderColor = Color(0xFFE64A19),
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = !demoMode && !useRemote,
                    onClick = { demoMode = false; useRemote = false },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                    colors = segmentColors,
                ) { Text("Local") }
                SegmentedButton(
                    selected = !demoMode && useRemote,
                    onClick = { demoMode = false; useRemote = true },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                    colors = segmentColors,
                ) { Text("myopenHAB") }
                SegmentedButton(
                    selected = demoMode,
                    onClick = { demoMode = true },
                    shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                    colors = segmentColors,
                ) { Text("Demo") }
            }

            Spacer(Modifier.height(16.dp))

            if (demoMode) {
                Text(
                    text = "Demo mode — showing sample devices. No server or sign-in needed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                )
            } else if (useRemote) {
                Text(
                    text = "Connects through home.myopenhab.org",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("myopenHAB Email") },
                    placeholder = { Text("you@example.com") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("myopenHAB Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("OpenHAB URL") },
                    placeholder = { Text("https://openhab.local:8443") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text("API Token") },
                    placeholder = { Text("oh.carplay.xxxxxx") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { allowSelfSigned = !allowSelfSigned },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = allowSelfSigned,
                        onCheckedChange = { allowSelfSigned = it },
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Allow self-signed certificates")
                        Text(
                            text = "Trust the local server's certificate even if it's " +
                                "self-signed. Use only on a network you trust.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray,
                        )
                    }
                }
            }

            if (!demoMode) {
                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = group,
                    onValueChange = { group = it },
                    label = { Text("Item Group") },
                    placeholder = { Text("gCarPlay") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(24.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
                        settings.save(
                            demoMode = demoMode,
                            useRemote = useRemote,
                            url = url.trim(),
                            token = token.trim(),
                            email = email.trim(),
                            password = password,
                            group = group.trim(),
                            allowSelfSigned = allowSelfSigned,
                        )
                        statusMessage = "Settings saved!"
                        statusColor = Color(0xFF4CAF50)
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Save")
                }

                Spacer(Modifier.width(12.dp))

                OutlinedButton(
                    onClick = {
                        testing = true
                        statusMessage = "Testing..."
                        statusColor = Color.Unspecified
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                buildSource().testConnection()
                            }
                            testing = false
                            if (result == "OK") {
                                statusMessage = "Connection successful!"
                                statusColor = Color(0xFF4CAF50)
                            } else {
                                statusMessage = "Connection failed: $result"
                                statusColor = Color(0xFFF44336)
                            }
                        }
                    },
                    enabled = !testing,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Test Connection")
                }
            }

            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                onClick = {
                    loadingItems = true
                    statusMessage = "Fetching items..."
                    statusColor = Color.Unspecified
                    items = emptyList()
                    scope.launch {
                        try {
                            val result = withContext(Dispatchers.IO) {
                                buildSource().getGroupItems(group.trim())
                            }
                            items = settings.applyItemOrder(result)
                            statusMessage = "Found ${result.size} item(s)"
                            statusColor = Color(0xFF4CAF50)
                        } catch (e: Exception) {
                            statusMessage = "Failed: ${e.message}"
                            statusColor = Color(0xFFF44336)
                        } finally {
                            loadingItems = false
                        }
                    }
                },
                enabled = !loadingItems && (demoMode || group.isNotBlank()),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Test Group")
            }

            if (statusMessage.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = statusMessage,
                    color = statusColor,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (items.isNotEmpty()) {
                LaunchedEffect(demoMode, useRemote, url, token, email, password, group) {
                    while (isActive) {
                        delay(5000)
                        if (togglingItem != null) continue
                        try {
                            val updated = withContext(Dispatchers.IO) {
                                buildSource().getGroupItems(group.trim())
                            }
                            items = settings.applyItemOrder(updated)
                        } catch (_: Exception) { }
                    }
                }

                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (demoMode) "Demo devices" else "Items in ${group.trim()}",
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn {
                    itemsIndexed(items) { index, item ->
                        val isToggling = togglingItem == item.name
                        Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(
                                    enabled = !isToggling && !item.isDimmer &&
                                        (item.isSelectable || !item.isReadOnly)
                                ) {
                                    if (item.isSelectable) {
                                        expandedItem = item.name
                                        return@clickable
                                    }
                                    togglingItem = item.name
                                    scope.launch {
                                        try {
                                            val newState = withContext(Dispatchers.IO) {
                                                buildSource().toggleItem(item.name)
                                            }
                                            item.state = newState
                                            items = items.toList()
                                        } catch (e: Exception) {
                                            statusMessage = "Toggle failed: ${e.message}"
                                            statusColor = Color(0xFFF44336)
                                        } finally {
                                            togglingItem = null
                                        }
                                    }
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (item.isOn) Color(0xFF4CAF50) else Color(0xFF9E9E9E)
                                    )
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.label,
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                Text(
                                    text = "${item.name} — ${item.state}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray,
                                )
                            }

                            // Reorder controls; the saved order is applied on every refresh.
                            IconButton(
                                onClick = { moveItem(index, -1) },
                                enabled = index > 0,
                                modifier = Modifier.size(32.dp),
                            ) { Text("▲") }
                            IconButton(
                                onClick = { moveItem(index, 1) },
                                enabled = index < items.size - 1,
                                modifier = Modifier.size(32.dp),
                            ) { Text("▼") }

                            if (isToggling) {
                                Text(
                                    text = "...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray,
                                )
                            }

                            if (item.isSelectable) {
                                DropdownMenu(
                                    expanded = expandedItem == item.name,
                                    onDismissRequest = { expandedItem = null },
                                ) {
                                    for (option in item.commandOptions) {
                                        DropdownMenuItem(
                                            text = { Text(option.label) },
                                            onClick = {
                                                expandedItem = null
                                                togglingItem = item.name
                                                scope.launch {
                                                    try {
                                                        withContext(Dispatchers.IO) {
                                                            buildSource().sendCommand(
                                                                item.name, option.command
                                                            )
                                                        }
                                                        item.state = option.command
                                                        items = items.toList()
                                                    } catch (e: Exception) {
                                                        statusMessage =
                                                            "Command failed: ${e.message}"
                                                        statusColor = Color(0xFFF44336)
                                                    } finally {
                                                        togglingItem = null
                                                    }
                                                }
                                            },
                                        )
                                    }
                                }
                            }
                        }

                        if (item.isDimmer) {
                            var level by remember(item.name, item.state) {
                                mutableStateOf(item.level.toFloat())
                            }
                            Slider(
                                value = level,
                                onValueChange = { level = it },
                                valueRange = 0f..100f,
                                enabled = !isToggling,
                                onValueChangeFinished = {
                                    val pct = level.toInt()
                                    togglingItem = item.name
                                    scope.launch {
                                        try {
                                            withContext(Dispatchers.IO) {
                                                buildSource().sendCommand(
                                                    item.name, pct.toString()
                                                )
                                            }
                                            item.state = pct.toString()
                                            items = items.toList()
                                        } catch (e: Exception) {
                                            statusMessage = "Set level failed: ${e.message}"
                                            statusColor = Color(0xFFF44336)
                                        } finally {
                                            togglingItem = null
                                        }
                                    }
                                },
                            )
                        }
                        }
                    }
                }
            }
        }
    }
}
