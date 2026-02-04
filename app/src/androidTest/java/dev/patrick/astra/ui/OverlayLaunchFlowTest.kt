package dev.patrick.astra.ui

import android.app.Activity
import android.app.Instrumentation
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.platform.LocalContext
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.anyIntent
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.matcher.IntentMatchers.hasData
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.patrick.astra.overlay.OverlayService
import org.hamcrest.Matchers.allOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OverlayLaunchFlowTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun setUp() {
        Intents.init()
        intending(anyIntent()).respondWith(
            Instrumentation.ActivityResult(Activity.RESULT_OK, Intent())
        )
    }

    @After
    fun tearDown() {
        Intents.release()
    }

    @Test
    fun overlayIcon_launchesPermissionIntentWhenDenied() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val coordinator = OverlayLaunchCoordinator(
            permissionChecker = OverlayPermissionChecker { false }
        )

        composeRule.setContent {
            AstraHomeScreen(overlayLaunchCoordinator = coordinator)
        }

        composeRule.onNodeWithText("🟣").performClick()

        intended(
            allOf(
                hasAction(Settings.ACTION_MANAGE_OVERLAY_PERMISSION),
                hasData(Uri.parse("package:${context.packageName}"))
            )
        )
    }

    @Test
    fun overlayIcon_startsServiceWhenPermissionGranted() {
        val baseContext = composeRule.activity
        val recordingContext = RecordingContext(baseContext)
        val coordinator = OverlayLaunchCoordinator(
            permissionChecker = OverlayPermissionChecker { true }
        )

        composeRule.setContent {
            CompositionLocalProvider(LocalContext provides recordingContext) {
                AstraHomeScreen(overlayLaunchCoordinator = coordinator)
            }
        }

        composeRule.onNodeWithText("🟣").performClick()

        composeRule.runOnIdle {
            val intent = recordingContext.startedServiceIntent
            val component = intent?.component
            assertEquals(OverlayService::class.java.name, component?.className)
        }
    }

    private class RecordingContext(base: Context) : ContextWrapper(base) {
        var startedServiceIntent: Intent? = null

        override fun startService(service: Intent): ComponentName? {
            startedServiceIntent = service
            return service.component
        }
    }
}
