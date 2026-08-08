package com.openhab.auto

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.darkColorScheme
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.car.app.connection.CarConnection
import androidx.lifecycle.Observer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Trailing toggle for secret fields: open eye = tap to reveal, see-no-evil = tap to hide.
// Uses emoji glyphs to avoid pulling in the material-icons-extended dependency.
@Composable
private fun PasswordEye(visible: Boolean, onToggle: () -> Unit) {
    IconButton(onClick = onToggle) {
        Text(if (visible) "🙈" else "👁")
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settings = OpenHabAutoApp.instance.settingsManager
        setContent {
            // Follow the system light/dark setting. The brand orange is lightened
            // a touch in dark mode so it stays vivid against a dark background.
            val colors = if (isSystemInDarkTheme()) {
                darkColorScheme(
                    primary = Color(0xFFFF7043),
                    onPrimary = Color.Black,
                )
            } else {
                lightColorScheme(
                    primary = Color(0xFFE64A19),
                    onPrimary = Color.White,
                )
            }
            MaterialTheme(colorScheme = colors) {
                SettingsScreen(settings)
            }
        }
    }
}

// True while the phone is connected to a car (Android Auto projection, or a
// native Android Automotive host). Google Play's driver-distraction policy
// requires phone-side UI that duplicates in-car functionality to be disabled
// for the whole time the phone is projecting — there's no reliable "parked
// vs driving" signal available from the phone side, so we lock down the
// entire interactive screen rather than try to guess.
@Composable
private fun rememberCarConnected(): Boolean {
    val context = LocalContext.current
    var connected by remember { mutableStateOf(false) }
    DisposableEffect(context) {
        val carConnection = CarConnection(context)
        val observer = Observer<Int> { type ->
            connected = type != CarConnection.CONNECTION_TYPE_NOT_CONNECTED
        }
        carConnection.type.observeForever(observer)
        onDispose { carConnection.type.removeObserver(observer) }
    }
    return connected
}

@Composable
private fun DrivingRestrictedMessage() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Connected to your car",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "To avoid distracted driving, controls aren't available on your " +
                "phone while connected to Android Auto. Use your car's display instead.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray,
            textAlign = TextAlign.Center,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(settings: SettingsManager) {
    if (rememberCarConnected()) {
        Scaffold(
            topBar = { TopAppBar(title = { Text("OH Auto") }) }
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                DrivingRestrictedMessage()
            }
        }
        return
    }

    var demoMode by remember { mutableStateOf(settings.demoMode) }
    var useRemote by remember { mutableStateOf(settings.useRemote) }
    var url by remember { mutableStateOf(settings.url) }
    var token by remember { mutableStateOf(settings.token) }
    var email by remember { mutableStateOf(settings.email) }
    var password by remember { mutableStateOf(settings.password) }
    var sitemap by remember { mutableStateOf(settings.sitemap) }
    var allowSelfSigned by remember { mutableStateOf(settings.allowSelfSigned) }
    var localBasicAuth by remember { mutableStateOf(settings.localBasicAuth) }
    var localUsername by remember { mutableStateOf(settings.localUsername) }
    var localPassword by remember { mutableStateOf(settings.localPassword) }

    // Build an item source for the currently selected mode from the live form values.
    // Local mode sends the API token as a Bearer token, or — when "Username &
    // password" is chosen — the username/password as Basic auth.
    fun buildSource(): OpenHabSource =
        when {
            demoMode -> DemoItemSource
            useRemote -> OpenHabService(OpenHabService.REMOTE_URL, email.trim(), password.trim())
            localBasicAuth -> OpenHabService(
                url.trim(), localUsername.trim(), localPassword.trim(),
                allowSelfSigned = allowSelfSigned,
            )
            else -> OpenHabService(url.trim(), token.trim(), allowSelfSigned = allowSelfSigned)
        }
    var passwordVisible by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("") }
    var statusColor by remember { mutableStateOf(Color.Unspecified) }
    var testing by remember { mutableStateOf(false) }
    var items by remember { mutableStateOf<List<OpenHabItem>>(emptyList()) }
    var togglingItem by remember { mutableStateOf<String?>(null) }
    var expandedItem by remember { mutableStateOf<String?>(null) }
    var sitemapOptions by remember { mutableStateOf<List<SitemapInfo>>(emptyList()) }
    var sitemapDropdownExpanded by remember { mutableStateOf(false) }
    var loadingSitemaps by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Load the list of sitemaps from the server to populate the dropdown.
    fun loadSitemaps() {
        if (loadingSitemaps) return
        loadingSitemaps = true
        scope.launch {
            try {
                sitemapOptions = withContext(Dispatchers.IO) { buildSource().getSitemaps() }
            } catch (e: Exception) {
                statusMessage = "Failed to load sitemaps: ${e.message}"
                statusColor = Color(0xFFF44336)
            } finally {
                loadingSitemaps = false
            }
        }
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
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Top,
        ) {
            val segmentColors = SegmentedButtonDefaults.colors(
                activeContainerColor = MaterialTheme.colorScheme.primary,
                activeContentColor = MaterialTheme.colorScheme.onPrimary,
                activeBorderColor = MaterialTheme.colorScheme.primary,
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
                    // Password keyboard: disables autocorrect/suggestions so
                    // punctuation (e.g. "!") can't trigger a word substitution.
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = if (passwordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        PasswordEye(passwordVisible) { passwordVisible = !passwordVisible }
                    },
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

                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = !localBasicAuth,
                        onClick = { localBasicAuth = false },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        colors = segmentColors,
                    ) { Text("API token") }
                    SegmentedButton(
                        selected = localBasicAuth,
                        onClick = { localBasicAuth = true },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        colors = segmentColors,
                    ) { Text("Username & password") }
                }

                Spacer(Modifier.height(16.dp))

                if (localBasicAuth) {
                    Text(
                        text = "Requires \"Allow Basic Authentication\" enabled in openHAB " +
                            "Settings → API Security.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = localUsername,
                        onValueChange = { localUsername = it },
                        label = { Text("Username") },
                        placeholder = { Text("openhab") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(Modifier.height(16.dp))

                    OutlinedTextField(
                        value = localPassword,
                        onValueChange = { localPassword = it },
                        label = { Text("Password") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        visualTransformation = if (passwordVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        trailingIcon = {
                            PasswordEye(passwordVisible) { passwordVisible = !passwordVisible }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    OutlinedTextField(
                        value = token,
                        onValueChange = { token = it },
                        label = { Text("API Token") },
                        placeholder = { Text("oh.carplay.xxxxxx") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        visualTransformation = if (passwordVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        trailingIcon = {
                            PasswordEye(passwordVisible) { passwordVisible = !passwordVisible }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

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

                ExposedDropdownMenuBox(
                    expanded = sitemapDropdownExpanded,
                    onExpandedChange = { expanded ->
                        sitemapDropdownExpanded = expanded
                        if (expanded && sitemapOptions.isEmpty()) loadSitemaps()
                    },
                ) {
                    OutlinedTextField(
                        value = sitemapOptions.find { it.name == sitemap }?.label ?: sitemap,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Sitemap") },
                        placeholder = {
                            Text(if (loadingSitemaps) "Loading..." else "Select a sitemap")
                        },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = sitemapDropdownExpanded)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    )
                    DropdownMenu(
                        expanded = sitemapDropdownExpanded,
                        onDismissRequest = { sitemapDropdownExpanded = false },
                        modifier = Modifier.exposedDropdownSize(),
                    ) {
                        if (sitemapOptions.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text(if (loadingSitemaps) "Loading..." else "No sitemaps found") },
                                onClick = {},
                            )
                        }
                        for (option in sitemapOptions) {
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                onClick = {
                                    sitemap = option.name
                                    sitemapDropdownExpanded = false
                                },
                            )
                        }
                    }
                }
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
                            password = password.trim(),
                            sitemap = sitemap.trim(),
                            allowSelfSigned = allowSelfSigned,
                            localBasicAuth = localBasicAuth,
                            localUsername = localUsername.trim(),
                            localPassword = localPassword.trim(),
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

            if (demoMode || sitemap.isNotBlank()) {
                // Fetch items whenever the sitemap or connection settings change, then
                // keep refreshing every 5s so tile states stay current.
                LaunchedEffect(
                    demoMode, useRemote, url, token, email, password, sitemap,
                    localBasicAuth, localUsername, localPassword,
                ) {
                    statusMessage = "Fetching items..."
                    statusColor = Color.Unspecified
                    try {
                        val result = withContext(Dispatchers.IO) {
                            buildSource().getSitemapItems(sitemap.trim())
                        }
                        items = result
                        statusMessage = "Found ${result.size} item(s)"
                        statusColor = Color(0xFF4CAF50)
                    } catch (e: Exception) {
                        items = emptyList()
                        statusMessage = "Failed: ${e.message}"
                        statusColor = Color(0xFFF44336)
                    }

                    while (isActive) {
                        delay(5000)
                        if (togglingItem != null) continue
                        try {
                            items = withContext(Dispatchers.IO) {
                                buildSource().getSitemapItems(sitemap.trim())
                            }
                        } catch (_: Exception) { }
                    }
                }
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
                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (demoMode) "Demo devices" else "Items in ${sitemap.trim()}",
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.height(8.dp))
                Column {
                    items.forEach { item ->
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
