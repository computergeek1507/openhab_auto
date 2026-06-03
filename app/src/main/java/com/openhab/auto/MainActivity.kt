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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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

    // Build an item source for the currently selected mode from the live form values.
    fun buildSource(): OpenHabSource =
        when {
            demoMode -> DemoItemSource
            useRemote -> OpenHabService(OpenHabService.REMOTE_URL, email.trim(), password)
            else -> OpenHabService(url.trim(), token.trim())
        }
    var statusMessage by remember { mutableStateOf("") }
    var statusColor by remember { mutableStateOf(Color.Unspecified) }
    var testing by remember { mutableStateOf(false) }
    var items by remember { mutableStateOf<List<OpenHabItem>>(emptyList()) }
    var loadingItems by remember { mutableStateOf(false) }
    var togglingItem by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("OpenHAB Auto") })
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
                            items = result
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
                            items = updated
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
                    items(items) { item ->
                        val isToggling = togglingItem == item.name
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !isToggling) {
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
                            if (isToggling) {
                                Text(
                                    text = "...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
