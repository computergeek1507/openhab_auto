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
import com.openhab.auto.CommandOption
import com.openhab.auto.OpenHabAutoApp
import com.openhab.auto.OpenHabItem
import com.openhab.auto.OpenHabService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.openhab.auto.R

class OpenHabScreen(carContext: CarContext) : Screen(carContext) {

    private val settings = OpenHabAutoApp.instance.settingsManager
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var items: List<OpenHabItem> = emptyList()
    private var error: String? = null
    private var busy = false

    init {
        refreshItems()
        startPeriodicRefresh()
    }

    private fun startPeriodicRefresh() {
        scope.launch {
            while (isActive) {
                delay(30_000)
                refreshItems()
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
                items = service.getGroupItems(settings.group)
                error = null
            } catch (e: Exception) {
                error = "Connection failed"
            } finally {
                busy = false
                invalidate()
            }
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
                .setTitle("openHAB Auto")
                .addAction(
                    Action.Builder()
                        .setTitle("Retry")
                        .setOnClickListener { refreshItems() }
                        .build()
                )
                .build()
        }

        if (items.isEmpty()) {
            return MessageTemplate.Builder("No items in group")
                .setTitle("openHAB Auto")
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
            .setTitle("openHAB Auto")
            .setHeaderAction(Action.APP_ICON)
            .setSingleList(listBuilder.build())
            .build()
    }
}
