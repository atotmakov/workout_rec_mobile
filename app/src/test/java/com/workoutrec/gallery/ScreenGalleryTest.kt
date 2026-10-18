package com.workoutrec.gallery

import android.app.Application
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.workoutrec.home.AccountAvatarTag
import com.workoutrec.home.SendDiagnosticsTag
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders every [ScreenGallery] screen in light, dark and dark Russian to
 * `app/build/outputs/gallery/<variant>/<screen>.png`.
 *
 * `./gradlew recordRoborazziDebug --tests '*ScreenGalleryTest*'` writes the images; a plain test
 * run only renders them, which still fails when a screen throws.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// A plain Application: the gallery needs no Tracer, WorkManager or database.
@Config(sdk = [35], qualifiers = RobolectricDeviceQualifiers.Pixel7, application = Application::class)
class ScreenGalleryTest(private val variant: Variant, private val screen: GalleryScreen) {

    enum class Variant(val qualifiers: String) {
        LIGHT("+en-notnight"),
        DARK("+en-night"),
        DARK_RU("+ru-night"),
    }

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun render() {
        RuntimeEnvironment.setQualifiers(variant.qualifiers)
        // Text fields in dialogs never let Compose go idle under Robolectric, and the rule's
        // setContent waits for idle; so the content is set on the activity and the clock moved by hand.
        compose.mainClock.autoAdvance = false
        compose.activity.setContent { GalleryWindow(screen) }
        settle()
        when (screen.open) {
            GalleryOpen.ACCOUNT_MENU -> compose.onNodeWithTag(AccountAvatarTag).performClick()
            GalleryOpen.DIAGNOSTICS_TEST_DIALOG -> {
                compose.onNodeWithTag(AccountAvatarTag).performClick()
                settle()
                compose.onNodeWithTag(SendDiagnosticsTag).performTouchInput { longClick() }
            }
            null -> {}
        }
        settle()
        captureScreenRoboImage("build/outputs/gallery/${variant.name.lowercase()}/${screen.name}.png")
    }

    /** Runs what a tap posted, then lets menus and dialogs finish opening. */
    private fun settle() = repeat(2) {
        shadowOf(Looper.getMainLooper()).idle()
        compose.mainClock.advanceTimeBy(SETTLE_MS)
    }

    companion object {
        private const val SETTLE_MS = 1_000L

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0} {1}")
        fun parameters(): List<Array<Any>> =
            Variant.entries.flatMap { variant -> ScreenGallery.screens.map { arrayOf<Any>(variant, it) } }
    }
}
