package com.example.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.core.GlobalNavAction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

data class UiNodeSummary(
    val text: String,
    val className: String,
    val clickable: Boolean,
    val editable: Boolean
)

object AccessibilityAutomationManager {
    private var serviceInstance: NovaAccessibilityService? = null
    private val emergencyStopped = AtomicBoolean(false)

    private var pendingSearchPackage: String? = null
    private var pendingSearchQuery: String? = null

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _foregroundPackage = MutableStateFlow("")
    val foregroundPackage: StateFlow<String> = _foregroundPackage.asStateFlow()

    private val _visibleNodes = MutableStateFlow<List<UiNodeSummary>>(emptyList())
    val visibleNodes: StateFlow<List<UiNodeSummary>> = _visibleNodes.asStateFlow()

    fun attach(service: NovaAccessibilityService) {
        serviceInstance = service
        _isConnected.value = true
        emergencyStopped.set(false)
    }

    fun detach() {
        serviceInstance = null
        _isConnected.value = false
    }

    fun isServiceActive(): Boolean = serviceInstance != null && _isConnected.value

    fun updateForeground(pkg: String, nodes: List<UiNodeSummary>) {
        _foregroundPackage.value = pkg
        _visibleNodes.value = nodes
    }

    fun scheduleInAppSearch(packageName: String, query: String) {
        emergencyStopped.set(false)
        pendingSearchPackage = packageName
        pendingSearchQuery = query
    }

    fun consumePendingSearchIfMatching(currentPackage: String): String? {
        if (emergencyStopped.get()) {
            pendingSearchPackage = null
            pendingSearchQuery = null
            return null
        }
        if (pendingSearchPackage == currentPackage && !pendingSearchQuery.isNullOrBlank()) {
            val q = pendingSearchQuery
            pendingSearchPackage = null
            pendingSearchQuery = null
            return q
        }
        return null
    }

    fun performGlobal(action: GlobalNavAction): String {
        val svc = serviceInstance
            ?: return "Enable NOVA Accessibility Service in Automation tab to perform ${action.name.lowercase()}."
        val code = when (action) {
            GlobalNavAction.BACK -> AccessibilityService.GLOBAL_ACTION_BACK
            GlobalNavAction.HOME -> AccessibilityService.GLOBAL_ACTION_HOME
            GlobalNavAction.RECENTS -> AccessibilityService.GLOBAL_ACTION_RECENTS
            GlobalNavAction.NOTIFICATIONS -> AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS
            GlobalNavAction.QUICK_SETTINGS -> AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS
        }
        val ok = svc.performGlobalAction(code)
        return if (ok) "Performed ${action.name.lowercase().replace('_', ' ')}." else "Global action rejected."
    }

    fun scrollScreen(down: Boolean): String {
        val svc = serviceInstance
            ?: return "Enable NOVA Accessibility Service for voice scrolling."
        if (emergencyStopped.get()) return "Automation emergency stop is active."
        val dm = svc.resources.displayMetrics
        val cx = dm.widthPixels / 2f
        val startY = if (down) dm.heightPixels * 0.72f else dm.heightPixels * 0.28f
        val endY = if (down) dm.heightPixels * 0.28f else dm.heightPixels * 0.72f
        val path = Path().apply {
            moveTo(cx, startY)
            lineTo(cx, endY)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, 280L))
            .build()
        val dispatched = svc.dispatchGesture(gesture, null, null)
        return if (dispatched) {
            if (down) "Scrolled down." else "Scrolled up."
        } else {
            "Scroll gesture failed."
        }
    }

    fun tapElementByText(label: String): String {
        val svc = serviceInstance
            ?: return "Enable NOVA Accessibility Service to tap on-screen buttons."
        if (emergencyStopped.get()) return "Automation emergency stop is active."
        val root = svc.rootInActiveWindow ?: return "No active screen window."
        val matches = root.findAccessibilityNodeInfosByText(label)
        val target = matches.firstOrNull { it.isClickable } ?: matches.firstOrNull()?.parent
        return if (target != null && target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            "Tapped '$label'."
        } else {
            "No clickable element matching '$label' found."
        }
    }

    fun emergencyStop(): String {
        emergencyStopped.set(true)
        pendingSearchPackage = null
        pendingSearchQuery = null
        return "Automation Emergency Stop triggered."
    }

    fun resetEmergencyStop() {
        emergencyStopped.set(false)
    }
}

class NovaAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        AccessibilityAutomationManager.attach(this)
        Log.d(TAG, "NOVA Accessibility Service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val root = rootInActiveWindow ?: return
        val pkg = event?.packageName?.toString() ?: root.packageName?.toString().orEmpty()
        val nodes = mutableListOf<UiNodeSummary>()
        collectNodes(root, nodes, 0)
        AccessibilityAutomationManager.updateForeground(pkg, nodes.take(25))

        val pendingQuery = AccessibilityAutomationManager.consumePendingSearchIfMatching(pkg)
        if (!pendingQuery.isNullOrBlank()) {
            tryInjectSearch(root, pendingQuery)
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "NOVA Accessibility Service interrupted")
    }

    override fun onDestroy() {
        AccessibilityAutomationManager.detach()
        super.onDestroy()
    }

    private fun tryInjectSearch(root: AccessibilityNodeInfo, query: String) {
        val editNode = findFirstEditable(root)
        if (editNode != null) {
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, query)
            }
            editNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            editNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        } else {
            // Try tapping a Search icon/text first
            val searchNodes = root.findAccessibilityNodeInfosByText("Search")
            searchNodes.firstOrNull { it.isClickable }?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
    }

    private fun findFirstEditable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isEditable) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findFirstEditable(child)
            if (found != null) return found
        }
        return null
    }

    private fun collectNodes(node: AccessibilityNodeInfo, out: MutableList<UiNodeSummary>, depth: Int) {
        if (depth > 8 || out.size >= 25) return
        val text = node.text?.toString()?.trim().orEmpty()
        val desc = node.contentDescription?.toString()?.trim().orEmpty()
        val display = text.ifEmpty { desc }
        if (display.isNotEmpty() || node.isEditable) {
            out.add(
                UiNodeSummary(
                    text = display.ifEmpty { "[Input Field]" },
                    className = node.className?.toString()?.substringAfterLast('.') ?: "View",
                    clickable = node.isClickable,
                    editable = node.isEditable
                )
            )
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectNodes(child, out, depth + 1)
        }
    }

    private companion object {
        const val TAG = "NovaA11yService"
    }
}
