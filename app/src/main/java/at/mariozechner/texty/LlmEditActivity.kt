package at.mariozechner.texty

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.EditText
import android.widget.Button
import android.view.ViewGroup
import android.widget.LinearLayout
import android.content.Intent
import android.content.SharedPreferences
import android.view.Gravity
import android.view.View
import android.widget.ProgressBar
import androidx.lifecycle.lifecycleScope
import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
import com.aallam.openai.api.http.Timeout
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import com.aallam.openai.client.OpenAIConfig
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.text.isNullOrBlank
import kotlin.time.Duration.Companion.seconds

class LlmEditActivity : AppCompatActivity() {

    private lateinit var editText: EditText
    private lateinit var promptEditText: EditText
    private lateinit var llmButton: Button
    private lateinit var applyButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var openAI: OpenAI
    private lateinit var rootLayout: LinearLayout
    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sharedPreferences = getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE)

        rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setPadding(16, 16, 16, 16)
        }

        editText = EditText(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            ).apply {
                setMargins(0, 0, 0, 16)
            }
            setText(intent.getStringExtra("text_content") ?: "")
            hint = "Text to edit"
            gravity = Gravity.TOP or Gravity.START
            minLines = 6
        }
        rootLayout.addView(editText)

        promptEditText = EditText(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 16)
            }
            val prompt = sharedPreferences.getString(
                MainActivity.PROMPT_KEY,
                "Fix typos, spelling and grammar, only output the corrected text and nothing else."
            );
            setText(prompt)
            setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    sharedPreferences.edit().putString(MainActivity.PROMPT_KEY, text.toString())
                        .apply()
                }
            }
            hint = "Prompt"
            maxLines = 5
        }
        rootLayout.addView(promptEditText)

        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 16)
            }
            visibility = View.GONE
            isIndeterminate = true
        }
        rootLayout.addView(progressBar)

        val buttonsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        llmButton = Button(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                setMargins(0, 0, 8, 0)
            }
            text = "Fix"
            setOnClickListener {
                triggerLlmCompletion()
            }
        }
        buttonsLayout.addView(llmButton)

        applyButton = Button(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
            text = "Apply"
            setOnClickListener {
                applyChanges()
            }
        }
        buttonsLayout.addView(applyButton)

        rootLayout.addView(buttonsLayout)

        setContentView(rootLayout)

        initializeOpenAI()
    }

    private fun initializeOpenAI() {
        try {
            val apiKey = sharedPreferences.getString(MainActivity.API_KEY_KEY, null)

            if (apiKey.isNullOrBlank()) {
                showError("API Key not found. Please enter it in the main screen.")
                return
            }

            val config = OpenAIConfig(token = apiKey, timeout = Timeout(socket = 60.seconds))
            openAI = OpenAI(config)
        } catch (e: Exception) {
            e.printStackTrace()
            showError("Failed to initialize OpenAI")
        }
    }

    private fun triggerLlmCompletion() {
        val textToEdit = editText.text.toString()
        val prompt = promptEditText.text.toString()

        // Validate inputs
        when {
            textToEdit.isBlank() -> {
                showError("Please enter text to edit.")
                return
            }

            prompt.isBlank() -> {
                showError("Please enter a prompt.")
                return
            }
        }

        setUIState(isLoading = true)

        lifecycleScope.launch {
            try {
                val completion = performLlmCompletion(textToEdit, prompt)
                updateEditText(completion)
            } catch (e: Exception) {
                showError("LLM Completion failed: ${e.localizedMessage}")
            } finally {
                setUIState(isLoading = false)
            }
        }
    }

    private suspend fun performLlmCompletion(text: String, prompt: String): String =
        withContext(Dispatchers.IO) {
            val chatCompletionRequest = ChatCompletionRequest(
                model = ModelId("gpt-4o"),
                messages = listOf(
                    ChatMessage(
                        role = ChatRole.System,
                        content = prompt
                    ),
                    ChatMessage(
                        role = ChatRole.User,
                        content = text
                    )
                )
            )

            val completion = openAI.chatCompletion(chatCompletionRequest)
            completion.choices.firstOrNull()?.message?.content
                ?: throw Exception("No response from LLM")
        }

    private suspend fun updateEditText(newText: String) = withContext(Dispatchers.Main) {
        editText.setText(newText)
    }

    private fun applyChanges() {
        val updatedText = editText.text.toString()
        val intent = Intent(LlmEditService.ACTION_TEXT_UPDATED).apply {
            setPackage(packageName)
            putExtra(LlmEditService.EXTRA_UPDATED_TEXT, updatedText)
        }
        sendBroadcast(intent)
        finish()
    }

    private fun setUIState(isLoading: Boolean) {
        runOnUiThread {
            progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            llmButton.isEnabled = !isLoading
            applyButton.isEnabled = !isLoading
            editText.isEnabled = !isLoading
            promptEditText.isEnabled = !isLoading
        }
    }

    private fun showError(message: String) {
        runOnUiThread {
            Snackbar.make(rootLayout, message, Snackbar.LENGTH_LONG).show()
        }
    }
}