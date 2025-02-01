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

    private var currentEditText: String? = null
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
        val isEditText = isTextField(sourceNode)

        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                if (isEditText && sourceNode != null) {
                    currentEditText = event.text.joinToString("");
                }
            }
            else -> {
                val focusedView = findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
                if (focusedView == null) {
                    if (floatingButton != null) {
                        hideFloatingButton()
                    };
                } else {
                    if (focusedView.packageName != packageName) {
                        handleEditTextFocus(focusedView)
                    }
                }
            }
        }

        sourceNode?.recycle()
    }

    private fun isTextField(node: AccessibilityNodeInfo?): Boolean {
        return node != null && (node.className?.contains("EditText") == true || node.className?.contains("MultiAutoCompleteTextView") == true)
    }

    private fun handleEditTextFocus(node: AccessibilityNodeInfo) {
        val text = node.text?.toString() ?: ""

        if (pendingUpdate != null) {
            updateEditTextContent(pendingUpdate!!)
            pendingUpdate = null
        }

        currentEditText = text

        if (text.isNotEmpty()) {
            showFloatingButton()
        }
    }

    private fun updateEditTextContent(newText: String) {
        val freshNode = findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (freshNode?.isEditable == true) {
            val arguments = Bundle().apply {
                putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    newText
                )
            }
            if (!freshNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)) {
                android.util.Log.d("TextDebug", "Failed to update EditText content");
            }
            freshNode.recycle()  // Don't forget to recycle the fresh node
        }
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
                    currentEditText?.let { text ->
                        val intent = Intent(context, LlmEditActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            putExtra("text_content", text)
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