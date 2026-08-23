package com.privacykeyboard.app

import android.app.Activity
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

// Full-screen PIN entry for My Dictionary - a whole page, the same way a dedicated
// vault app does it, never an AlertDialog popup (Point 1). Also hosts the "Forgot
// PIN?" recovery flow entirely on this same page (still no popups): recovery email
// -> new PIN -> unlocked.
//
// Returns RESULT_OK to the caller once the PIN (or the email-recovery flow) has been
// successfully verified, RESULT_CANCELED otherwise.
class DictionaryUnlockActivity : Activity() {

    private lateinit var container: LinearLayout
    private var screen = SCREEN_UNLOCK
    private var isForLockManagement = false  // Point 1: forgot PIN only for lock management

    private val colorTextPrimary by lazy { resources.getColor(R.color.text_primary) }
    private val colorTextSecondary by lazy { resources.getColor(R.color.text_secondary) }
    private val colorAccent by lazy { resources.getColor(R.color.accent_mint) }
    private val colorCard by lazy { resources.getColor(R.color.keyboard_bg) }
    private val colorBg by lazy { resources.getColor(R.color.bg_dark) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Point 3: leave the soft keyboard's visibility exactly as it already was
        // instead of letting Android force a hide+reshow during this Activity
        // transition - that forced re-trigger is what caused the keyboard to
        // visibly flash/appear twice when unlocking My Dictionary.
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED)
        // Point 2: Allow Screenshots toggle now also covers the Dictionary
        // Security Lock unlock screen itself, not just the word list after unlocking.
        val screenshotsAllowed = android.preference.PreferenceManager
            .getDefaultSharedPreferences(this)
            .getBoolean("allow_screenshots_my_dict", true)
        if (!screenshotsAllowed) {
            window.setFlags(
                android.view.WindowManager.LayoutParams.FLAG_SECURE,
                android.view.WindowManager.LayoutParams.FLAG_SECURE
            )
        }
        setContentView(R.layout.activity_settings_section)

        // Point 1: check if coming from lock management or My Dictionary unlock
        isForLockManagement = intent.getBooleanExtra("isForLockManagement", false)

        findViewById<TextView>(R.id.section_title).text = "My Dictionary"
        findViewById<TextView>(R.id.back_button).setOnClickListener {
            if (screen != SCREEN_UNLOCK) {
                screen = SCREEN_UNLOCK
                render()
            } else {
                setResult(RESULT_CANCELED)
                finish()
            }
        }

        container = findViewById(R.id.row_container)

        if (!DictionaryLockStore.isLocked(this)) {
            // Nothing to unlock; treat the caller's request as already satisfied.
            setResult(RESULT_OK)
            finish()
            return
        }

        render()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (screen != SCREEN_UNLOCK) {
            screen = SCREEN_UNLOCK
            render()
        } else {
            setResult(RESULT_CANCELED)
            super.onBackPressed()
        }
    }

    private fun render() {
        container.removeAllViews()
        container.setPadding(dp(24), dp(20), dp(24), dp(24))
        when (screen) {
            SCREEN_RECOVER_EMAIL -> renderRecoverEmail()
            SCREEN_RECOVER_NEW_PIN -> renderRecoverNewPin()
            else -> renderUnlock()
        }
    }

    private fun renderUnlock() {
        container.addView(iconBadge("\uD83D\uDD12"))
        container.addView(heading("My Dictionary is Locked"))
        container.addView(subtext("Enter your Dictionary Security Lock PIN to continue"))

        val cooldown = DictionaryLockStore.cooldownSecondsRemaining(this)
        val input = pinField("PIN")
        input.isEnabled = cooldown <= 0
        container.addView(input)

        if (cooldown > 0) {
            container.addView(subtext("Too many attempts. Try again in ${cooldown}s."))
        }

        container.addView(primaryButton("Unlock", enabled = cooldown <= 0) {
            if (DictionaryLockStore.verifyPin(this, input.text.toString())) {
                setResult(RESULT_OK)
                finish()
            } else {
                val now = DictionaryLockStore.cooldownSecondsRemaining(this)
                Toast.makeText(
                    this,
                    if (now > 0) "Too many attempts. Try again in ${now}s." else "Incorrect PIN",
                    Toast.LENGTH_SHORT
                ).show()
                render()
            }
        })

        // Point 1: Forgot PIN option only when unlocking Dictionary Security Lock management
        // (not when unlocking My Dictionary to view/edit words)
        if (isForLockManagement && DictionaryLockStore.hasRecoveryEmail(this)) {
            container.addView(linkText("Forgot PIN?") {
                screen = SCREEN_RECOVER_EMAIL
                render()
            })
        }
    }

    private fun renderRecoverEmail() {
        container.addView(iconBadge("\u2709\uFE0F"))
        container.addView(heading("Recover Access"))
        container.addView(subtext("Enter your recovery key (the email you set earlier). This app has no internet access, so nothing is emailed as an OTP - it just has to match exactly."))

        val cooldown = DictionaryLockStore.recoveryCooldownSecondsRemaining(this)
        val input = EditText(this).apply {
            hint = "Recovery email"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            setTextColor(colorTextPrimary)
            setHintTextColor(colorTextSecondary)
            textSize = 16f
            background = cardBg()
            setPadding(dp(16), dp(14), dp(16), dp(14))
            isEnabled = cooldown <= 0
        }
        container.addView(input)
        if (cooldown > 0) {
            container.addView(subtext("Too many attempts. Try again in ${cooldown}s."))
        }

        container.addView(primaryButton("Verify Email", enabled = cooldown <= 0) {
            if (DictionaryLockStore.verifyRecoveryEmail(this, input.text.toString())) {
                screen = SCREEN_RECOVER_NEW_PIN
                render()
            } else {
                val now = DictionaryLockStore.recoveryCooldownSecondsRemaining(this)
                Toast.makeText(
                    this,
                    if (now > 0) "Too many attempts. Try again in ${now}s." else "Email doesn't match",
                    Toast.LENGTH_SHORT
                ).show()
            }
        })

        container.addView(linkText("Back to PIN") {
            screen = SCREEN_UNLOCK
            render()
        })
    }

    private fun renderRecoverNewPin() {
        container.addView(iconBadge("\uD83D\uDD11"))
        container.addView(heading("Set a New PIN"))
        container.addView(subtext("Choose a new Dictionary Security Lock PIN"))

        val input1 = pinField("New PIN (4 to 8 digits)")
        val input2 = pinField("Confirm PIN")
        container.addView(input1)
        container.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(0, dp(10)) })
        container.addView(input2)

        container.addView(primaryButton("Save & Unlock", enabled = true) {
            val pin1 = input1.text.toString()
            val pin2 = input2.text.toString()
            when {
                pin1.length < 4 -> Toast.makeText(this, "PIN must be at least 4 digits", Toast.LENGTH_SHORT).show()
                pin1.length > 8 -> Toast.makeText(this, "PIN must be at most 8 digits", Toast.LENGTH_SHORT).show()
                pin1 != pin2 -> Toast.makeText(this, "PINs do not match", Toast.LENGTH_SHORT).show()
                else -> {
                    DictionaryLockStore.setPin(this, pin1)
                    Toast.makeText(this, "PIN updated", Toast.LENGTH_SHORT).show()
                    setResult(RESULT_OK)
                    finish()
                }
            }
        })
    }

    // --- small view builders ------------------------------------------------------------

    private fun iconBadge(icon: String) = TextView(this).apply {
        text = icon
        textSize = 40f
        gravity = Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(20); bottomMargin = dp(8)
        }
    }

    private fun heading(text: String) = TextView(this).apply {
        this.text = text
        setTextColor(colorTextPrimary)
        textSize = 19f
        gravity = Gravity.CENTER
        setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

    private fun subtext(text: String) = TextView(this).apply {
        this.text = text
        setTextColor(colorTextSecondary)
        textSize = 13f
        gravity = Gravity.CENTER
        setPadding(dp(12), dp(6), dp(12), dp(20))
    }

    private fun pinField(hintText: String) = EditText(this).apply {
        hint = hintText
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        setTextColor(colorTextPrimary)
        setHintTextColor(colorTextSecondary)
        textSize = 20f
        gravity = Gravity.CENTER
        background = cardBg()
        setPadding(dp(16), dp(16), dp(16), dp(16))
    }

    private fun cardBg() = GradientDrawable().apply {
        setColor(colorCard)
        cornerRadius = 14f * resources.displayMetrics.density
    }

    private fun primaryButton(label: String, enabled: Boolean, onClick: () -> Unit) = Button(this).apply {
        text = label
        isEnabled = enabled
        setTextColor(if (enabled) colorBg else colorTextSecondary)
        background = GradientDrawable().apply {
            setColor(if (enabled) colorAccent else colorCard)
            cornerRadius = 24f * resources.displayMetrics.density
        }
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(18)
        }
        setOnClickListener { onClick() }
    }

    private fun linkText(text: String, onClick: () -> Unit) = TextView(this).apply {
        this.text = text
        setTextColor(colorAccent)
        textSize = 13f
        gravity = Gravity.CENTER
        setPadding(0, dp(18), 0, dp(4))
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val SCREEN_UNLOCK = "unlock"
        private const val SCREEN_RECOVER_EMAIL = "recover_email"
        private const val SCREEN_RECOVER_NEW_PIN = "recover_new_pin"
    }
}
