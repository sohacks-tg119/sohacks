package com.hackerman.sohacksrev2

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class QuickBubbleSettingsActivity : AppCompatActivity() {
    private lateinit var store: QuickBubbleStore
    private lateinit var listContainer: LinearLayout
    private lateinit var emptyView: TextView
    private lateinit var deviceView: TextView
    private lateinit var scope: QuickBubbleScope
    private lateinit var model: ScooterModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_quick_bubbles)

        store = QuickBubbleStore(this)
        listContainer = findViewById(R.id.quickBubbleListContainer)
        emptyView = findViewById(R.id.tvQuickBubbleEmpty)
        deviceView = findViewById(R.id.tvQuickBubbleDevice)

        findViewById<MaterialButton>(R.id.btnAddQuickBubble).setOnClickListener {
            startActivity(Intent(this, QuickBubbleEditorActivity::class.java))
        }
        findViewById<MaterialButton>(R.id.btnQuickBubblesBack).setOnClickListener { finish() }
    }

    override fun onResume() {
        super.onResume()
        AppPreferences.applyKeepScreenOn(this)
        scope = QuickBubbleScope.current(this)
        model = ScooterCommandCatalog.findModel(scope.modelId)
        deviceView.text = buildScopeLabel()
        renderBubbles()
    }

    private fun buildScopeLabel(): String {
        val device = scope.deviceAddress?.let { " • …${it.takeLast(8)}" }.orEmpty()
        return model.displayName + device
    }

    private fun renderBubbles() {
        val bubbles = store.load(scope)
        listContainer.removeAllViews()
        emptyView.visibility = if (bubbles.isEmpty()) View.VISIBLE else View.GONE

        bubbles.forEach { bubble -> listContainer.addView(createBubbleCard(bubble)) }
    }

    private fun createBubbleCard(bubble: QuickBubble): MaterialCardView {
        val card = MaterialCardView(this).apply {
            setCardBackgroundColor(ContextCompat.getColor(this@QuickBubbleSettingsActivity, R.color.colorSurface))
            radius = 14.dp.toFloat()
            strokeWidth = 1.dp
            strokeColor = ContextCompat.getColor(this@QuickBubbleSettingsActivity, R.color.colorOutline)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = 10.dp }
            setOnClickListener { openEditor(bubble.id) }
        }

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(14.dp, 12.dp, 8.dp, 12.dp)
        }
        row.addView(TextView(this).apply {
            text = bubble.name.take(2).uppercase()
            gravity = Gravity.CENTER
            setTextColor(contrastTextColor(bubble.color))
            textSize = 12f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(bubble.color)
            }
            layoutParams = LinearLayout.LayoutParams(46.dp, 46.dp).also { it.marginEnd = 13.dp }
        })

        val labels = bubble.actions.map { it.label(model) }
        row.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            addView(TextView(this@QuickBubbleSettingsActivity).apply {
                text = bubble.name
                setTextColor(ContextCompat.getColor(this@QuickBubbleSettingsActivity, R.color.colorOnSurface))
                textSize = 16f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
            addView(TextView(this@QuickBubbleSettingsActivity).apply {
                text = labels.take(3).joinToString("  ›  ") +
                    if (labels.size > 3) "  +${labels.size - 3}" else ""
                setTextColor(ContextCompat.getColor(this@QuickBubbleSettingsActivity, R.color.textColorSecondary))
                textSize = 12f
                maxLines = 2
            })
        })

        row.addView(MaterialButton(this).apply {
            text = "×"
            contentDescription = "${bubble.name} löschen"
            textSize = 22f
            minWidth = 0
            insetTop = 0
            insetBottom = 0
            setPadding(12.dp, 0, 12.dp, 0)
            backgroundTintList = ColorStateList.valueOf(android.graphics.Color.TRANSPARENT)
            setTextColor(ContextCompat.getColor(this@QuickBubbleSettingsActivity, R.color.colorDanger))
            layoutParams = LinearLayout.LayoutParams(48.dp, 48.dp)
            setOnClickListener {
                MaterialAlertDialogBuilder(this@QuickBubbleSettingsActivity)
                    .setTitle("${bubble.name} löschen?")
                    .setNegativeButton("Abbrechen", null)
                    .setPositiveButton("Löschen") { _, _ ->
                        store.delete(scope, bubble.id)
                        renderBubbles()
                    }
                    .show()
            }
        })
        card.addView(row)
        return card
    }

    private fun openEditor(id: String) {
        startActivity(Intent(this, QuickBubbleEditorActivity::class.java).putExtra(EXTRA_BUBBLE_ID, id))
    }

    private fun contrastTextColor(background: Int): Int {
        val luminance = androidx.core.graphics.ColorUtils.calculateLuminance(background)
        return if (luminance > 0.48) android.graphics.Color.BLACK else android.graphics.Color.WHITE
    }

    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_BUBBLE_ID = "quick_bubble_id"
    }
}
