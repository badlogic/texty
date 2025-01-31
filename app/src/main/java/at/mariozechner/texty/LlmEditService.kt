package at.mariozechner.texty

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.content.Intent
import android.view.accessibility.AccessibilityNodeInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.os.Bundle

class LlmEditService : AccessibilityService() {
    private var windowManager: WindowManager? = null
    private var floatingButton: View? = null
    private var isReceiverRegistered = false

    // State management
    private data class EditTextState(
        val id: String,
        val content: String
    )

    private var currentEditText: EditTextState? = null
    private var pendingUpdate: String? = null

    companion object {
        const val ACTION_TEXT_UPDATED = "at.mariozechner.texty.TEXT_UPDATED"
        const val EXTRA_UPDATED_TEXT = "updated_text"
    }

    private val textUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_TEXT_UPDATED) {
                val updatedText = intent.getStringExtra(EXTRA_UPDATED_TEXT) ?: return
                android.util.Log.d("TextDebug", "Received updated text: '$updatedText'")
                // Store the update until we get focus on the right EditText
                pendingUpdate = updatedText
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        registerReceiver()
    }

    private fun registerReceiver() {
        if (!isReceiverRegistered) {
            val filter = IntentFilter(ACTION_TEXT_UPDATED)
            registerReceiver(
                textUpdateReceiver,
                filter,
                Context.RECEIVER_NOT_EXPORTED
            )
            isReceiverRegistered = true
            android.util.Log.d("TextDebug", "Receiver registered")
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // Don't process events from our own app
        if (event.packageName == packageName && event.className?.contains("android.widget.EditText") == true) {
            hideFloatingButton()
            return
        }

        val sourceNode = event.source
        val isEditText = event.className?.contains("EditText") == true

        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_FOCUSED -> {
                if (isEditText && sourceNode != null) {
                    val nodeId = "${sourceNode.windowId}${sourceNode.viewIdResourceName}"
                    handleEditTextFocus(nodeId, sourceNode)
                } else if (!isEditText) {
                    // Only hide if we're focusing something that's definitely not an EditText
                    hideFloatingButton()
                }
            }
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                if (isEditText && sourceNode != null) {
                    val nodeId = "${sourceNode.windowId}${sourceNode.viewIdResourceName}"
                    handleTextChange(nodeId, event.text?.joinToString("") ?: "")
                }
            }
            else -> {
                if (floatingButton != null) {
                    val focusedView = findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
                    if (focusedView == null || focusedView.className?.contains("EditText") != true) {
                        hideFloatingButton();
                    }
                }
            }
        }

        sourceNode?.recycle()
    }

    private fun isEditText(node: AccessibilityNodeInfo): Boolean {
        return node.className?.contains("EditText") == true
    }

    private fun handleEditTextFocus(nodeId: String, node: AccessibilityNodeInfo) {
        val text = node.text?.toString() ?: ""

        // If we have a pending update for this EditText
        if (pendingUpdate != null && currentEditText?.id == nodeId) {
            updateEditTextContent(node, pendingUpdate!!)
            pendingUpdate = null
        }

        // Update current EditText state
        currentEditText = EditTextState(nodeId, text)

        // Show floating button if we have text
        if (text.isNotEmpty()) {
            showFloatingButton()
        }
    }

    private fun handleTextChange(nodeId: String, newText: String) {
        if (currentEditText?.id == nodeId) {
            currentEditText = currentEditText?.copy(content = newText)
        }
    }

    private fun updateEditTextContent(node: AccessibilityNodeInfo, newText: String) {
        val arguments = Bundle().apply {
            putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                newText
            )
        }
        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
    }

    private fun showFloatingButton() {
        if (floatingButton == null) {
            val button = Button(this).apply {
                text = "TY"
                setPadding(10, 10, 10, 10)
                setMinWidth(50)
                setMinHeight(50)
                textSize = 10f

                setOnClickListener {
                    currentEditText?.let { state ->
                        val intent = Intent(context, LlmEditActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            putExtra("text_content", state.content)
                        }
                        startActivity(intent)
                        hideFloatingButton()
                    }
                }
            }

            val params = WindowManager.LayoutParams(
                80,
                80,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.END
                x = 50 // Adjust horizontal offset if needed
                y = 50 // Adjust vertical offset from bottom
            }

            windowManager?.addView(button, params)
            floatingButton = button
        }
    }

    private fun hideFloatingButton() {
        floatingButton?.let {
            windowManager?.removeView(it)
            floatingButton = null
        }
    }

    override fun onInterrupt() {
        hideFloatingButton()
    }

    override fun onDestroy() {
        super.onDestroy()
        hideFloatingButton()
        if (isReceiverRegistered) {
            unregisterReceiver(textUpdateReceiver)
            isReceiverRegistered = false
        }
    }
}