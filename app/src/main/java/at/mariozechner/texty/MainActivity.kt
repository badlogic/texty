package at.mariozechner.texty

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.EditText
import android.widget.Toast
import androidx.core.content.edit

class MainActivity : AppCompatActivity() {

    private lateinit var apiKeyEditText: EditText
    private lateinit var sharedPreferences: SharedPreferences

    companion object {
        const val PREFS_NAME = "TextyPrefs"
        const val API_KEY_KEY = "apiKey"
        const val PROMPT_KEY = "prompt"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }

        apiKeyEditText = EditText(this).apply {
            hint = "Enter your OpenAI API Key"
            setText(sharedPreferences.getString(API_KEY_KEY, ""))
        }
        layout.addView(apiKeyEditText)

        val saveApiKeyButton = Button(this).apply {
            text = "Save API Key"
            setOnClickListener {
                saveApiKey()
            }
        }
        layout.addView(saveApiKeyButton)

        val explanation = TextView(this).apply {
            text = "This app requires accessibility permissions to show a floating button when text fields are focused. Please enable the accessibility service in Settings."
            setPadding(0, 0, 0, 16)
        }
        layout.addView(explanation)

        val openSettingsButton = Button(this).apply {
            text = "Open Accessibility Settings"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }
        layout.addView(openSettingsButton)

        setContentView(layout)
    }

    private fun saveApiKey() {
        val apiKey = apiKeyEditText.text.toString()

        if (apiKey.isBlank()) {
            Toast.makeText(this, "API Key cannot be empty", Toast.LENGTH_SHORT).show()
            return
        }

        sharedPreferences.edit {
            putString(API_KEY_KEY, apiKey)
            apply()
        }

        Toast.makeText(this, "API Key saved", Toast.LENGTH_SHORT).show()
    }
}