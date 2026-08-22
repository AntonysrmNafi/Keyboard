package com.privacykeyboard.app

import android.app.Activity
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.util.Patterns
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

// Full-screen Dictionary Security Lock management (Point 1 / 1.2) - a whole page for
// every step, never a popup. This activity is only ever reached either with no PIN
// set yet (first-time setup) or immediately after DictionaryUnlockActivity has
// already verified the current PIN, so nothing here re-checks the PIN itself; that
// check lives in exactly one place (DictionaryUnlockActivity).
class DictionaryLockManageActivity : Activity() {

    private lateinit var container: LinearLayout
    private var screen = SCREEN_MENU

    private val colorTextPrimary by lazy { resources.getColor(R.color.text_primary) }
    private val colorTextSecondary by lazy { resources.getColor(R.color.text_secondary) }
    private val colorAccent by lazy { resources.getColor(R.color.accent_mint) }
    private val colorCard by lazy { resources.getColor(R.color.keyboard_bg) }
    private val colorBg by lazy { resources.getColor(R.color.bg_dark) }
    private val colorDanger = 0xFFE0685A.toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings_section)

        findViewById<TextView>(R.id.section_title).text = "Dictionary Security Lock"
        findViewById<TextView>(R.id.back_button).setOnClickListener {
            if (!stepBackOrFinish()) finish()
        }

        container = findViewById(R.id.row_container)
        screen = if (DictionaryLockStore.isLocked(this)) SCREEN_MENU else SCREEN_SET_PIN
        render()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (!stepBackOrFinish()) super.onBackPressed()
    }

    // Only steps back to the menu when there is a menu to return to (i.e. the lock
    // already exists); first-time PIN setup has no menu behind it, so back exits
    // straight away in that case. Returns true if it stepped back, false otherwise.
    private fun stepBackOrFinish(): Boolean {
        if (screen != SCREEN_MENU && DictionaryLockStore.isLocked(this)) {
            screen = SCREEN_MENU
            render()
            return true
        }
        return false
    }

    private fun render() {
        container.removeAllViews()
        container.setPadding(dp(24), dp(20), dp(24), dp(24))
        when (screen) {
            SCREEN_SET_PIN -> renderSetPin(isChange = false)
            SCREEN_CHANGE_PIN -> renderSetPin(isChange = true)
            SCREEN_REMOVE -> renderRemove()
            SCREEN_EMAIL -> renderEmail()
            else -> renderMenu()
        }
    }

    private fun renderMenu() {
        val emailStatus = if (DictionaryLockStore.hasRecoveryEmail(this)) "Recovery key is set"
            else "No recovery key set yet"
        container.addView(subtext(emailStatus))

        container.addView(menuRow("\uD83D\uDD11", "Change PIN/Pass", "Set a new PIN for My Dictionary") {
            screen = SCREEN_CHANGE_PIN; render()
        })
        container.addView(menuRow("\uD83D\uDDD1", "Remove PIN/Pass", "Turn off the Dictionary Security Lock", danger = true) {
            screen = SCREEN_REMOVE; render()
        })
        container.addView(menuRow("\u2709\uFE0F", "Set/Change Recovery Key", "Used only to recover a forgotten PIN") {
            screen = SCREEN_EMAIL; render()
        })
        // Point 7: Allow Screenshots toggle
        val screenshotsAllowed = android.preference.PreferenceManager
            .getDefaultSharedPreferences(this)
            .getBoolean("allow_screenshots_my_dict", true)
        container.addView(toggleRow(
            "\uD83D\udccf",
            "Allow Screenshots",
            "Enable or disable screenshots in My Dictionary (English & à¦¬à¦¾à¦‚à¦²à¦¾ screens only)",
            screenshotsAllowed
        ) { enabled ->
            android.preference.PreferenceManager
                .getDefaultSharedPreferences(this)
                .edit()
                .putBoolean("allow_screenshots_my_dict", enabled)
                .apply()
        })
    }

    private fun toggleRow(icon: String, title: String, subtitle: String, initialState: Boolean, onChange: (Boolean) -> Unit): LinearLayout {
        var enabled = initialState
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = cardBg()
            setPadding(dp(16), dp(16), dp(16), dp(16))
            isClickable = true
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(12)
            }
            setOnClickListener {
                enabled = !enabled
                toggle.text = if (enabled) "ON" else "OFF"
                toggle.setTextColor(if (enabled) colorAccent else colorTextSecondary)
                onChange(enabled)
            }
        }
        row.addView(TextView(this).apply {
            text = icon
            textSize = 18f
            setPadding(0, 0, dp(14), 0)
        })
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        column.addView(TextView(this).apply {
            text = title
            setTextColor(colorTextPrimary)
            textSize = 15f
        })
        column.addView(TextView(this).apply {
            text = subtitle
            setTextColor(colorTextSecondary)
            textSize = 11f
        })
        row.addView(column)
        val toggle = TextView(this).apply {
            text = if (enabled) "ON" else "OFF"
            setTextColor(if (enabled) colorAccent else colorTextSecondary)
            textSize = 13f
        }
        row.addView(toggle)
        return row
    }

    private fun renderSetPin(isChange: Boolean) {
        container.addView(heading(if (isChange) "Change PIN/Pass" else "Set Dictionary PIN"))
        container.addView(subtext("One PIN protects both English and à¦¬à¦¾à¦‚à¦²à¦¾ My Dictionary."))

        val input1 = pinField("New PIN (4 to 8 digits)")
        val input2 = pinField("Confirm PIN")
        container.addView(input1)
        container.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(0, dp(10)) })
        container.addView(input2)

        container.addView(primaryButton(if (isChange) "Save New PIN" else "Create Lock") {
            val pin1 = input1.text.toString()
            val pin2 = input2.text.toString()
            when {
                pin1.length < 4 -> Toast.makeText(this, "PIN must be at least 4 digits", Toast.LENGTH_SHORT).show()
                pin1.length > 8 -> Toast.makeText(this, "PIN must be at most 8 digits", Toast.LENGTH_SHORT).show()
                pin1 != pin2 -> Toast.makeText(this, "PINs do not match", Toast.LENGTH_SHORT).show()
                else -> {
                    DictionaryLockStore.setPin(this, pin1)
                    Toast.makeText(
                        this,
                        if (isChange) "PIN updated" else "Dictionary Security Lock is on",
                        Toast.LENGTH_SHORT
                    ).show()
                    if (isChange) { screen = SCREEN_MENU; render() } else finish()
                }
            }
        })

        if (isChange) container.addView(linkText("Cancel") { screen = SCREEN_MENU; render() })
    }

    private fun renderRemove() {
        container.addView(heading("Remove PIN/Pass"))
        container.addView(subtext("This turns off the Dictionary Security Lock completely. Both English and à¦¬à¦¾à¦‚à¦²à¦¾ My Dictionary will open without a PIN afterwards."))
        container.addView(dangerButton("Remove Lock") {
            DictionaryLockStore.clearPin(this)
            Toast.makeText(this, "Lock removed", Toast.LENGTH_SHORT).show()
            finish()
        })
        container.addView(linkText("Cancel") { screen = SCREEN_MENU; render() })
    }

    private fun renderEmail() {
        val current = DictionaryLockStore.getRecoveryEmail(this)
        val isChange = current != null

        container.addView(heading(if (isChange) "Change Recovery Key" else "Set Recovery Key"))
        container.addView(subtext("This works as a recovery key, not an OTP - the app has no internet access, so nothing is ever actually emailed. If you forget your PIN, typing this exact address back on the unlock page is what lets you set a new one."))

        val input = EditText(this).apply {
            hint = "Recovery key (email format)"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            setTextColor(colorTextPrimary)
            setHintTextColor(colorTextSecondary)
            textSize = 16f
            background = cardBg()
            setPadding(dp(16), dp(14), dp(16), dp(14))
            if (current != null) {
                setText(current)
                setSelection(current.length)
            }
        }
        container.addView(input)

        container.addView(primaryButton(if (isChange) "Update Key" else "Set Key") {
            val email = input.text.toString().trim()
            if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(this, "Enter a valid email format", Toast.LENGTH_SHORT).show()
            } else {
                DictionaryLockStore.setRecoveryEmail(this, email)
                Toast.makeText(this, if (isChange) "Recovery key updated" else "Recovery key saved", Toast.LENGTH_SHORT).show()
                screen = SCREEN_MENU
                render()
            }
        })

        if (current != null) {
            container.addView(linkText("Remove recovery key") {
                DictionaryLockStore.clearRecoveryEmail(this)
                Toast.makeText(this, "Recovery key removed", Toast.LENGTH_SHORT).show()
                screen = SCREEN_MENU
                render()
            })
        }
        container.addView(linkText("Cancel") { screen = SCREEN_MENU; render() })
    }

    // --- view builders -------------------------------------------------------------------

    private fun heading(text: String) = TextView(this).apply {
        this.text = text
        setTextColor(colorTextPrimary)
        textSize = 18f
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        setPadding(0, 0, 0, dp(6))
    }

    private fun subtext(text: String) = TextView(this).apply {
        this.text = text
        setTextColor(colorTextSecondary)
        textSize = 13f
        setPadding(0, 0, 0, dp(18))
    }

    private fun pinField(hintText: String) = EditText(this).apply {
        hint = hintText
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        setTextColor(colorTextPrimary)
        setHintTextColor(colorTextSecondary)
        textSize = 18f
        background = cardBg()
        setPadding(dp(16), dp(14), dp(16), dp(14))
    }

    private fun cardBg() = GradientDrawable().apply {
        setColor(colorCard)
        cornerRadius = 14f * resources.displayMetrics.density
    }

    private fun primaryButton(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        setTextColor(colorBg)
        background = GradientDrawable().apply {
            setColor(colorAccent)
            cornerRadius = 24f * resources.displayMetrics.density
        }
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(16)
        }
        setOnClickListener { onClick() }
    }

    private fun dangerButton(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        setTextColor(resources.getColor(android.R.color.white))
        background = GradientDrawable().apply {
            setColor(colorDanger)
            cornerRadius = 24f * resources.displayMetrics.density
        }
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(16)
        }
        setOnClickListener { onClick() }
    }

    private fun linkText(text: String, onClick: () -> Unit) = TextView(this).apply {
        this.text = text
        setTextColor(colorTextSecondary)
        textSize = 13f
        gravity = Gravity.CENTER
        setPadding(0, dp(16), 0, dp(4))
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }
    }

    private fun menuRow(icon: String, title: String, subtitle: String, danger: Boolean = false, onClick: () -> Unit): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = cardBg()
            setPadding(dp(16), dp(16), dp(16), dp(16))
            isClickable = true
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(12)
            }
            setOnClickListener { onClick() }
        }
        row.addView(TextView(this).apply {
            text = icon
            textSize = 18f
            setPadding(0, 0, dp(14), 0)
        })
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        column.addView(TextView(this).apply {
            text = title
            setTextColor(if (danger) colorDanger else colorTextPrimary)
            textSize = 15f
        })
        column.addView(TextView(this).apply {
            text = subtitle
            setTextColor(colorTextSecondary)
            textSize = 11f
        })
        row.addView(column)
        row.addView(TextView(this).apply {
            text = "\u203A"
            setTextColor(colorTextSecondary)
            textSize = 18f
        })
        return row
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val SCREEN_MENU = "menu"
        private const val SCREEN_SET_PIN = "set_pin"
        private const val SCREEN_CHANGE_PIN = "change_pin"
        private const val SCREEN_REMOVE = "remove"
        private const val SCREEN_EMAIL = "email"
    }
}
