package com.openhab.auto.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarIcon
import androidx.car.app.model.GridItem
import androidx.car.app.model.GridTemplate
import androidx.car.app.model.ItemList
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.openhab.auto.CommandOption
import com.openhab.auto.OpenHabAutoApp
import com.openhab.auto.OpenHabItem
import com.openhab.auto.StateChangeListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.openhab.auto.R

class OpenHabScreen(carContext: CarContext) : Screen(carContext) {

    private val settings = OpenHabAutoApp.instance.settingsManager
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    @Volatile private var items: List<OpenHabItem> = emptyList()
    private var error: String? = null
    @Volatile private var busy = false

    // Live state-change stream (server-sent events). While it's connected, tiles
    // update as changes arrive and polling drops to an occasional resync; while
    // it's down (or the source can't stream, e.g. demo mode) we poll as before.
    @Volatile private var stream: AutoCloseable? = null
    @Volatile private var streamConnected = false
    private var reconnectJob: Job? = null
    private var reconnectDelayMs = RECONNECT_MIN_MS
    private var pendingInvalidate: Job? = null
    @Volatile private var lastRefreshAt = 0L

    init {
        refreshItems()
        startPeriodicRefresh()
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) = openStream()
            override fun onStop(owner: LifecycleOwner) = closeStream()
            override fun onDestroy(owner: LifecycleOwner) {
                closeStream()
                scope.cancel()
            }
        })
    }

    private fun startPeriodicRefresh() {
        scope.launch {
            while (isActive) {
                delay(POLL_INTERVAL_MS)
                val sinceLast = System.currentTimeMillis() - lastRefreshAt
                if (!streamConnected || sinceLast >= RESYNC_INTERVAL_MS) {
                    refreshItems()
                }
            }
        }
    }

    private fun refreshItems() {
        if (busy) return
        if (!settings.isConfigured) {
            error = "Configure settings on phone"
            invalidate()
            return
        }

        busy = true
        scope.launch {
            try {
                val service = settings.buildSource()
                items = service.getSitemapItems(settings.sitemap)
                lastRefreshAt = System.currentTimeMillis()
                error = null
            } catch (e: Exception) {
                error = "Connection failed"
            } finally {
                busy = false
                invalidate()
            }
        }
    }

    @Synchronized
    private fun openStream() {
        if (stream != null || !settings.isConfigured) return
        stream = try {
            settings.buildSource().subscribeStateChanges(streamListener)
        } catch (_: Exception) {
            scheduleReconnect()
            null
        }
    }

    @Synchronized
    private fun closeStream() {
        reconnectJob?.cancel()
        reconnectJob = null
        stream?.close()
        stream = null
        streamConnected = false
    }

    @Synchronized
    private fun scheduleReconnect() {
        if (reconnectJob?.isActive == true) return
        val wait = reconnectDelayMs
        reconnectDelayMs = (reconnectDelayMs * 2).coerceAtMost(RECONNECT_MAX_MS)
        reconnectJob = scope.launch {
            delay(wait)
            openStream()
        }
    }

    private val streamListener = object : StateChangeListener {
        override fun onConnected() {
            streamConnected = true
            synchronized(this@OpenHabScreen) { reconnectDelayMs = RECONNECT_MIN_MS }
            // Catch up on anything that changed while we were disconnected.
            refreshItems()
        }

        override fun onStateChanged(itemName: String, state: String) {
            var changed = false
            for (item in items) {
                if (item.name == itemName && item.state != state) {
                    item.state = state
                    changed = true
                }
            }
            if (changed) requestInvalidate()
        }

        override fun onDisconnected(error: Throwable?) {
            synchronized(this@OpenHabScreen) {
                streamConnected = false
                stream = null
            }
            scheduleReconnect()
        }
    }

    // Coalesce bursts of events (a scene switching several lights) into one
    // template refresh, so the head unit isn't asked to redraw per event.
    @Synchronized
    private fun requestInvalidate() {
        if (pendingInvalidate?.isActive == true) return
        pendingInvalidate = scope.launch {
            delay(INVALIDATE_DEBOUNCE_MS)
            invalidate()
        }
    }

    private fun toggleItem(item: OpenHabItem) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                val service = settings.buildSource()
                val newState = service.toggleItem(item.name)
                item.state = newState
            } catch (_: Exception) {
                refreshItems()
                return@launch
            } finally {
                busy = false
                invalidate()
            }
        }
    }

    private fun openCommandList(item: OpenHabItem, options: List<CommandOption>) {
        screenManager.push(ItemCommandScreen(carContext, item, options) { invalidate() })
    }

    companion object {
        private const val POLL_INTERVAL_MS = 30_000L
        // Full refresh while streaming, as a safety net for missed events.
        private const val RESYNC_INTERVAL_MS = 5 * 60_000L
        private const val RECONNECT_MIN_MS = 2_000L
        private const val RECONNECT_MAX_MS = 60_000L
        private const val INVALIDATE_DEBOUNCE_MS = 250L

        // Sliders aren't allowed in car templates, so dimmers pick from fixed levels.
        private val DIMMER_LEVELS = listOf(
            CommandOption("0", "Off (0%)"),
            CommandOption("25", "25%"),
            CommandOption("50", "50%"),
            CommandOption("75", "75%"),
            CommandOption("100", "100%"),
        )
    }

    override fun onGetTemplate(): Template {
        if (error != null || (!settings.isConfigured && items.isEmpty())) {
            return MessageTemplate.Builder(error ?: "Configure settings on phone")
                .setTitle("OH Auto")
                .addAction(
                    Action.Builder()
                        .setTitle("Retry")
                        .setOnClickListener { refreshItems() }
                        .build()
                )
                .build()
        }

        if (items.isEmpty()) {
            return MessageTemplate.Builder("No items in sitemap")
                .setTitle("OH Auto")
                .addAction(
                    Action.Builder()
                        .setTitle("Refresh")
                        .setOnClickListener { refreshItems() }
                        .build()
                )
                .build()
        }

        val listBuilder = ItemList.Builder()
        for (item in items) {
            val gridItem = GridItem.Builder().setTitle(item.label)

            if (item.isSelectable) {
                // Has command options: tap to open a list and pick a command.
                val icon = CarIcon.Builder(
                    IconCompat.createWithResource(carContext, R.drawable.ic_value)
                ).build()
                gridItem.setText(item.displayValue).setImage(icon)
                    .setOnClickListener { openCommandList(item, item.commandOptions) }
            } else if (item.isDimmer) {
                // Sliders aren't allowed in the car, so tap to pick a preset level.
                val icon = CarIcon.Builder(
                    IconCompat.createWithResource(
                        carContext,
                        if (item.isOn) R.drawable.ic_on else R.drawable.ic_off
                    )
                ).build()
                gridItem.setText("${item.level}%").setImage(icon)
                    .setOnClickListener { openCommandList(item, DIMMER_LEVELS) }
            } else if (item.isReadOnly) {
                // Read-only: show the value with no toggle action.
                val icon = CarIcon.Builder(
                    IconCompat.createWithResource(carContext, R.drawable.ic_value)
                ).build()
                gridItem.setText(item.displayValue).setImage(icon)
            } else {
                val icon = CarIcon.Builder(
                    IconCompat.createWithResource(
                        carContext,
                        if (item.isOn) R.drawable.ic_on else R.drawable.ic_off
                    )
                ).build()
                val stateLabel = when {
                    item.isRollershutter -> if (item.isOn) "CLOSED" else "OPEN"
                    else -> if (item.isOn) "ON" else "OFF"
                }
                gridItem.setText(stateLabel).setImage(icon)
                    .setOnClickListener { toggleItem(item) }
            }

            listBuilder.addItem(gridItem.build())
        }

        return GridTemplate.Builder()
            .setTitle("OH Auto")
            .setHeaderAction(Action.APP_ICON)
            .setSingleList(listBuilder.build())
            .build()
    }
}
