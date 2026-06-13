package com.openhab.auto.car

import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import com.openhab.auto.CommandOption
import com.openhab.auto.OpenHabAutoApp
import com.openhab.auto.OpenHabItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Presents a list of commands for an item as a selectable list. Used both for an
 * item's openHAB commandDescription options and for synthesized dimmer presets.
 * Picking one sends that command, then pops back to the grid.
 */
class ItemCommandScreen(
    carContext: CarContext,
    private val item: OpenHabItem,
    private val options: List<CommandOption>,
    private val onResult: () -> Unit,
) : Screen(carContext) {

    private val settings = OpenHabAutoApp.instance.settingsManager
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var busy = false

    private fun select(command: String) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                settings.buildSource().sendCommand(item.name, command)
                item.state = command
            } catch (_: Exception) {
                CarToast.makeText(carContext, "Command failed", CarToast.LENGTH_SHORT).show()
                busy = false
                return@launch
            }
            onResult()
            screenManager.pop()
        }
    }

    override fun onGetTemplate(): Template {
        val listBuilder = ItemList.Builder()
        for (option in options) {
            val row = Row.Builder().setTitle(option.label)
            if (option.command == item.state) {
                row.addText("Current")
            }
            row.setOnClickListener { select(option.command) }
            listBuilder.addItem(row.build())
        }

        return ListTemplate.Builder()
            .setTitle(item.label)
            .setHeaderAction(androidx.car.app.model.Action.BACK)
            .setSingleList(listBuilder.build())
            .build()
    }
}
