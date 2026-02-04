package dev.patrick.astra.overlay

import android.content.Intent
import android.provider.Settings
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiScrollable
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.Until
import dev.patrick.astra.ui.MainActivity
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OverlayServiceE2eTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun overlayPermissionAndService_showOverlayBubble() {
        val context = composeRule.activity
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val packageName = context.packageName
        val appLabel = context.applicationInfo.loadLabel(context.packageManager).toString()

        val wasAllowed = Settings.canDrawOverlays(context)
        if (!wasAllowed) {
            composeRule.onNodeWithText("🟣").performClick()
            waitForSettings(device)
            ensureOverlayPermissionEnabled(device, appLabel, packageName, context)
            returnToApp(device, packageName)
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithText("🟣").performClick()

        val hasOverlay = device.wait(
            Until.hasObject(By.desc(OverlayService.OVERLAY_CONTENT_DESC)),
            TIMEOUT_MS
        )
        assertTrue("Overlay bubble was not found on screen", hasOverlay)

        context.stopService(Intent(context, OverlayService::class.java))
        device.wait(Until.gone(By.desc(OverlayService.OVERLAY_CONTENT_DESC)), TIMEOUT_MS)
    }

    private fun waitForSettings(device: UiDevice) {
        device.wait(Until.hasObject(By.pkg(SETTINGS_PACKAGE).depth(0)), TIMEOUT_MS)
    }

    private fun ensureOverlayPermissionEnabled(
        device: UiDevice,
        appName: String,
        packageName: String,
        context: android.content.Context
    ) {
        val initialSwitch = findOverlaySwitch(device)
        if (initialSwitch == null) {
            openAppOverlayDetails(device, appName)
        }

        val switchWidget = findOverlaySwitch(device)

        if (switchWidget != null) {
            if (!switchWidget.isChecked) {
                switchWidget.click()
            }
        }

        if (!Settings.canDrawOverlays(context)) {
            device.executeShellCommand("appops set $packageName SYSTEM_ALERT_WINDOW allow")
        }

        val nowAllowed = Settings.canDrawOverlays(context)
        assertTrue("Overlay permission was not enabled", nowAllowed)
    }

    private fun openAppOverlayDetails(device: UiDevice, appName: String) {
        val scrollable = UiScrollable(UiSelector().scrollable(true))
        scrollable.setAsVerticalList()
        scrollable.scrollToBeginning(5)
        if (!device.hasObject(By.textContains(appName))) {
            val found = scrollable.scrollIntoView(UiSelector().textContains(appName))
            if (!found) {
                val fallbackToken = appName.split(" ").firstOrNull() ?: appName
                scrollable.scrollIntoView(UiSelector().textContains(fallbackToken))
            }
        }
        val appEntry = device.findObject(By.textContains(appName))
        requireNotNull(appEntry) { "App entry '$appName' not found in overlay list" }
        appEntry.click()
        device.wait(Until.hasObject(By.textContains("Display over other apps")), TIMEOUT_MS)
    }

    private fun findOverlaySwitch(device: UiDevice) = device.findObject(
        By.res(SETTINGS_PACKAGE, "switch_widget")
    ) ?: device.findObject(By.clazz("android.widget.Switch"))

    private fun returnToApp(device: UiDevice, packageName: String) {
        repeat(3) {
            if (device.wait(Until.hasObject(By.pkg(packageName).depth(0)), 500)) {
                return
            }
            device.pressBack()
        }
        device.wait(Until.hasObject(By.pkg(packageName).depth(0)), TIMEOUT_MS)
    }

    companion object {
        private const val SETTINGS_PACKAGE = "com.android.settings"
        private const val TIMEOUT_MS = 6_000L
    }
}
