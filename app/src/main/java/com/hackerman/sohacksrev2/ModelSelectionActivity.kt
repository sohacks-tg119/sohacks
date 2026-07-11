package com.hackerman.sohacksrev2

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.card.MaterialCardView

class ModelSelectionActivity : AppCompatActivity() {

    private lateinit var modelListContainer: LinearLayout
    private val required: Boolean by lazy { intent.getBooleanExtra(EXTRA_MODEL_REQUIRED, false) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_model_selection)

        modelListContainer = findViewById(R.id.modelListContainer)
        findViewById<TextView>(R.id.tvOpenSettings).apply {
            paintFlags = paintFlags or Paint.UNDERLINE_TEXT_FLAG
            setOnClickListener {
                startActivity(Intent(this@ModelSelectionActivity, SettingsActivity::class.java))
            }
        }

        renderModels()
    }

    override fun onResume() {
        super.onResume()
        AppPreferences.applyKeepScreenOn(this)
    }

    override fun onBackPressed() {
        if (required) {
            setResult(Activity.RESULT_CANCELED)
            finish()
        } else {
            setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_MODEL_ID, savedModelId()))
            finish()
        }
    }

    private fun renderModels() {
        val selectedModelId = savedModelId()
        ScooterCommandCatalog.models.forEach { model ->
            modelListContainer.addView(createModelCard(model, selectedModelId == model.id))
        }
    }

    private fun createModelCard(model: ScooterModel, selected: Boolean): MaterialCardView {
        val card = MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = resources.getDimensionPixelSize(R.dimen.gap_s) }
            radius = resources.getDimension(R.dimen.card_radius)
            strokeWidth = resources.getDimensionPixelSize(R.dimen.card_stroke)
            setCardBackgroundColor(ContextCompat.getColor(this@ModelSelectionActivity, R.color.colorSurface))
            setStrokeColor(
                ContextCompat.getColor(
                    this@ModelSelectionActivity,
                    if (selected) R.color.colorSecondary else R.color.colorOutline
                )
            )
            setOnClickListener { selectModel(model) }
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16.dp, 16.dp, 16.dp, 16.dp)
        }

        content.addView(
            TextView(this).apply {
                text = model.displayName
                setTextColor(ContextCompat.getColor(this@ModelSelectionActivity, R.color.colorOnBackground))
                textSize = 18f
                setTypeface(typeface, Typeface.BOLD)
            }
        )
        content.addView(
            TextView(this).apply {
                text = model.description
                setTextColor(ContextCompat.getColor(this@ModelSelectionActivity, R.color.textColorSecondary))
                textSize = 14f
                setPadding(0, 6.dp, 0, 0)
            }
        )

        card.addView(content)
        return card
    }

    private fun selectModel(model: ScooterModel) {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MODEL_ID, model.id)
            .apply()

        setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_MODEL_ID, model.id))
        finish()
    }

    private fun savedModelId(): String? {
        return getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_MODEL_ID, null)
    }

    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()

    companion object {
        private const val PREFS_NAME = "BLE_Prefs"
        private const val KEY_MODEL_ID = "model_id"
        private const val EXTRA_MODEL_ID = "MODEL_ID"
        private const val EXTRA_MODEL_REQUIRED = "MODEL_REQUIRED"
    }
}
