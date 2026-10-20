package de.mm20.launcher2.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Records the hot paths of a fresh install: cold start, the search sheet with the app list,
 * typing a query and the widget screen. Run with:
 * ANDROID_SERIAL=<device> ./gradlew :baselineprofile:connectedDefaultNonMinifiedReleaseAndroidTest \
 *     -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.enabledRules=BaselineProfile
 * then copy the produced *-startup-prof.txt from baselineprofile/build/outputs/connected_android_test_additional_output
 * to app/app/src/main/generated/baselineProfiles/ as both baseline-prof.txt and startup-prof.txt.
 * The target runs as de.mm20.launcher2.profiling. Prefer adb over Wi-Fi: USB resets on the Pixel kill the run.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() {
        // Matches the applicationId app/app gives its nonMinifiedRelease/benchmarkRelease variants.
        val packageName = InstrumentationRegistry.getArguments().getString("targetAppId")
            ?: "de.mm20.launcher2.profiling"

        rule.collect(packageName = packageName, includeInStartupProfile = true) {
            pressHome()
            startActivityAndWait()

            val x = device.displayWidth / 2
            val top = device.displayHeight / 4
            val bottom = device.displayHeight * 3 / 4

            // Swipe down: search with the full app list, then scroll it.
            device.swipe(x, top, x, bottom, 20)
            device.waitForIdle()
            device.swipe(x, bottom, x, top, 20)
            device.waitForIdle()
            device.executeShellCommand("input text ca")
            device.waitForIdle()
            device.pressBack()
            device.pressBack()
            device.waitForIdle()

            // Swipe up: widget screen.
            device.swipe(x, bottom, x, top, 20)
            device.waitForIdle()
            device.pressBack()
            device.waitForIdle()
        }
    }
}
