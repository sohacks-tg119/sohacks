package com.hackerman.sohacksrev2

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class QuickBubbleEditorActivity : AppCompatActivity() {
    private lateinit var store: QuickBubbleStore
    private lateinit var scope: QuickBubbleScope
    private lateinit var model: ScooterModel
    private lateinit var nameInput: EditText
    private lateinit var colorsContainer: LinearLayout
    private lateinit var actionsContainer: LinearLayout
    private val actions = mutableListOf<QuickBubbleAction>()
    private val colorViews = linkedMapOf<Int, View>()
    private var selectedColor = QuickBubble.DEFAULT_COLOR
    private var existingBubble: QuickBubble? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_quick_bubble_editor)

        store = QuickBubbleStore(this)
        scope = QuickBubbleScope.current(this)
        model = ScooterCommandCatalog.findModel(scope.modelId)
        val bubbleId = intent.getStringExtra(QuickBubbleSettingsActivity.EXTRA_BUBBLE_ID)
        existingBubble = store.load(scope).firstOrNull { it.id == bubbleId }

        nameInput = findViewById(R.id.inputQuickBubbleName)
        colorsContainer = findViewById(R.id.quickBubbleColorContainer)
        actionsContainer = findViewById(R.id.quickBubbleActionsContainer)

        findViewById<TextView>(R.id.tvQuickBubbleEditorTitle).text =
            if (existingBubble == null) "Neue Bubble" else "Bubble bearbeiten"
        findViewById<TextView>(R.id.tvQuickBubbleEditorDevice).text = model.displayName

        existingBubble?.let {
            nameInput.setText(it.name)
            selectedColor = it.color
            actions += it.actions
        }

        renderPalette()
        renderActions()
        findViewById<MaterialButton>(R.id.btnAddQuickBubbleAction).setOnClickListener { showActionPicker() }
        findViewById<MaterialButton>(R.id.btnSaveQuickBubble).setOnClickListener { saveBubble() }
        findViewById<MaterialButton>(R.id.btnCancelQuickBubble).setOnClickListener { finish() }
    }

    override fun onResume() {
        super.onResume()
        AppPreferences.applyKeepScreenOn(this)
    }

    private fun renderPalette() {
        colorsContainer.removeAllViews()
        colorViews.clear()
        PALETTE.forEach { color ->
            val swatch = View(this).apply {
                contentDescription = "Bubble-Farbe"
                isClickable = true
                layoutParams = LinearLayout.LayoutParams(48.dp, 48.dp).also { it.marginEnd = 10.dp }
                setOnClickListener {
                    selectedColor = color
                    updatePaletteSelection()
                }
            }
            colorViews[color] = swatch
            colorsContainer.addView(swatch)
        }
        updatePaletteSelection()
    }

    private fun updatePaletteSelection() {
        colorViews.forEach { (color, view) ->
            view.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
                setStroke(
                    if (color == selectedColor) 3.dp else 1.dp,
                    if (color == selectedColor) Color.WHITE else Color.TRANSPARENT
                )
            }
        }
    }

    private fun renderActions() {
        actionsContainer.removeAllViews()
        if (actions.isEmpty()) {
            actionsContainer.addView(TextView(this).apply {
                text = "Noch keine Aktion"
                setTextColor(ContextCompat.getColor(this@QuickBubbleEditorActivity, R.color.textColorSecondary))
                textSize = 13f
                setPadding(2.dp, 8.dp, 2.dp, 8.dp)
            })
            return
        }

        actions.forEachIndexed { index, action ->
            actionsContainer.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = GradientDrawable().apply {
                    cornerRadius = 12.dp.toFloat()
                    setColor(ContextCompat.getColor(this@QuickBubbleEditorActivity, R.color.colorSurface))
                    setStroke(1.dp, ContextCompat.getColor(this@QuickBubbleEditorActivity, R.color.colorOutline))
                }
                setPadding(12.dp, 6.dp, 4.dp, 6.dp)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.bottomMargin = 7.dp }

                addView(TextView(this@QuickBubbleEditorActivity).apply {
                    text = "${index + 1}   ${action.label(model)}"
                    setTextColor(ContextCompat.getColor(this@QuickBubbleEditorActivity, R.color.colorOnSurface))
                    textSize = 14f
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                })
                addView(rowButton("↑", index > 0) {
                    val moved = actions.removeAt(index)
                    actions.add(index - 1, moved)
                    renderActions()
                })
                addView(rowButton("↓", index < actions.lastIndex) {
                    val moved = actions.removeAt(index)
                    actions.add(index + 1, moved)
                    renderActions()
                })
                addView(rowButton("×", true, danger = true) {
                    actions.removeAt(index)
                    renderActions()
                })
            })
        }
    }

    private fun rowButton(
        label: String,
        enabled: Boolean,
        danger: Boolean = false,
        action: () -> Unit
    ): MaterialButton = MaterialButton(this).apply {
        text = label
        isEnabled = enabled
        alpha = if (enabled) 1f else 0.25f
        minWidth = 0
        minimumWidth = 0
        insetTop = 0
        insetBottom = 0
        setPadding(8.dp, 0, 8.dp, 0)
        textSize = 18f
        backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
        setTextColor(
            ContextCompat.getColor(
                this@QuickBubbleEditorActivity,
                if (danger) R.color.colorDanger else R.color.colorSecondary
            )
        )
        layoutParams = LinearLayout.LayoutParams(38.dp, 42.dp)
        setOnClickListener { action() }
    }

    private fun showActionPicker() {
        val choices = buildList {
            if (model.commands.unlock != null) add(ActionChoice("Entsperren", QuickBubbleAction(QuickBubbleActionType.UNLOCK)))
            if (model.commands.lock != null) add(ActionChoice("Sperren", QuickBubbleAction(QuickBubbleActionType.LOCK)))
            if (model.commands.eco != null) add(ActionChoice("Eco", QuickBubbleAction(QuickBubbleActionType.ECO)))
            if (model.commands.normal != null) add(ActionChoice("Normal", QuickBubbleAction(QuickBubbleActionType.NORMAL)))
            if (model.commands.sport != null) add(ActionChoice("Sport", QuickBubbleAction(QuickBubbleActionType.SPORT)))
            if (model.commands.dev != null) add(ActionChoice("Dev", QuickBubbleAction(QuickBubbleActionType.DEV)))
            if (model.supportedSpeeds.isNotEmpty()) add(ActionChoice("Geschwindigkeit …", picker = Picker.SPEED))
            if (model.maxAdvancedMode > 0) add(ActionChoice("Advanced-Mode …", picker = Picker.ADVANCED))
            model.extraCommands.forEach {
                add(ActionChoice(it.label, QuickBubbleAction(QuickBubbleActionType.EXTRA, it.id)))
            }
            add(ActionChoice("App beenden", QuickBubbleAction(QuickBubbleActionType.EXIT_APP)))
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("Aktion")
            .setItems(choices.map { it.label }.toTypedArray()) { _, index ->
                val choice = choices[index]
                when (choice.picker) {
                    Picker.SPEED -> showSpeedPicker()
                    Picker.ADVANCED -> showAdvancedModePicker()
                    null -> choice.action?.let(::addAction)
                }
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    private fun showSpeedPicker() {
        val speeds = model.supportedSpeeds
        MaterialAlertDialogBuilder(this)
            .setTitle("Geschwindigkeit")
            .setItems(speeds.map { "$it km/h" }.toTypedArray()) { _, index ->
                addAction(QuickBubbleAction(QuickBubbleActionType.SPEED, speeds[index].toString()))
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    private fun showAdvancedModePicker() {
        val modes = (1..model.maxAdvancedMode).toList()
        MaterialAlertDialogBuilder(this)
            .setTitle("Advanced-Mode")
            .setItems(modes.map { "Mode $it" }.toTypedArray()) { _, index ->
                addAction(QuickBubbleAction(QuickBubbleActionType.ADVANCED_MODE, modes[index].toString()))
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    private fun addAction(action: QuickBubbleAction) {
        actions += action
        renderActions()
    }

    private fun saveBubble() {
        val name = nameInput.text?.toString()?.trim().orEmpty()
        if (name.isEmpty()) {
            nameInput.error = "Name fehlt"
            return
        }
        if (actions.isEmpty()) {
            Toast.makeText(this, "Mindestens eine Aktion hinzufügen", Toast.LENGTH_SHORT).show()
            return
        }
        val exitIndex = actions.indexOfFirst { it.type == QuickBubbleActionType.EXIT_APP }
        if (exitIndex >= 0 && exitIndex != actions.lastIndex) {
            Toast.makeText(this, "App beenden muss am Ende stehen", Toast.LENGTH_SHORT).show()
            return
        }
        if (actions.any { !it.isAvailableFor(model) }) {
            Toast.makeText(this, "Eine Aktion ist für dieses Gerät nicht verfügbar", Toast.LENGTH_SHORT).show()
            return
        }

        val previous = existingBubble
        val existingCount = store.load(scope).size
        val bubble = QuickBubble(
            id = previous?.id ?: java.util.UUID.randomUUID().toString(),
            name = name,
            color = selectedColor,
            actions = actions.toList(),
            positionX = previous?.positionX ?: (0.08f + (existingCount % 4) * 0.2f).coerceAtMost(0.8f),
            positionY = previous?.positionY ?: (0.2f + (existingCount / 4) * 0.13f).coerceAtMost(0.72f),
            sizeDp = previous?.sizeDp ?: QuickBubble.DEFAULT_SIZE_DP
        ).normalized()
        store.upsert(scope, bubble)
        finish()
    }

    private data class ActionChoice(
        val label: String,
        val action: QuickBubbleAction? = null,
        val picker: Picker? = null
    )

    private enum class Picker { SPEED, ADVANCED }

    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()

    companion object {
        private val PALETTE = listOf(
            Color.rgb(53, 214, 154),
            Color.rgb(72, 163, 255),
            Color.rgb(154, 104, 255),
            Color.rgb(255, 99, 132),
            Color.rgb(242, 169, 59),
            Color.rgb(255, 224, 92),
            Color.rgb(238, 238, 238),
            Color.rgb(48, 56, 52)
        )
    }
}
