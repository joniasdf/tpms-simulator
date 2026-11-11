package fi.joni.tpmssim

import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
import androidx.compose.ui.test.onRoot
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h915dp-xxhdpi")
class ScreenshotTest {
    @get:Rule val rule = createComposeRule()
    private val out = "/workspace/screenshots"

    private fun state() = UiState().apply { sensors.forEachIndexed { i, t -> t.id = "%02XEACA1A2B%02X".format(0x80 + i, 0x10 + i) } }

    private fun shot(name: String, dark: Boolean, st: UiState) {
        rule.setContent { TpmsTheme(dark) { App(st) } }
        rule.onRoot().captureRoboImage("$out/$name.png")
    }

    @Test fun simulatorNormal() = shot("01_simulaattori_normaali", false, state())

    @Test fun simulatorAlarmDark() { val st = state()
        st.sensors[0].applyScenario(2); repeat(9) { st.sensors[0].tick(0) }           // nopea vuoto: 240 -> 150 kPa
        st.sensors[3].applyScenario(1); repeat(60) { st.sensors[3].tick(0) }         // hidas vuoto: 240 -> 210 kPa
        st.sensors[1].applyScenario(4)
        st.sensors[2].noSignal = true
        st.advertising = true; st.current = "FL (80EACA1A2B10)"; st.selected = 0
        shot("02_simulaattori_halytys_tumma", true, st) }

    @Test fun simulatorFlRrOnly() { val st = state()
        st.sensors.forEachIndexed { i, t -> t.enabled = i == 0 || i == 3 }
        st.sensors[3].applyScenario(1); repeat(40) { st.sensors[3].tick(0) }
        st.advertising = true; st.current = "RR (83EACA1A2B13)"; st.selected = 3
        shot("04_simulaattori_vain_FL_RR", false, st) }

    @Test fun simulatorNoneActive() { val st = state(); st.setAll(false); st.advertising = true
        shot("05_simulaattori_ei_aktiivisia_tumma", true, st) }

    @Test fun reader() { val st = state(); st.tab = 1; st.scanning = true; val now = 1_000_000L; st.now = now
        // Syötetään oikeita simulaattorin payloadeja dekooderin läpi
        st.sensors[3].kpa = 150.0; st.sensors[3].fastLeak = true
        st.sensors.take(4).forEachIndexed { i, t ->
            val r = TpmsFormat.decode(byteArrayOf(0, 1) + TpmsFormat.payload(t))!!
            st.seen["5A:3C:11:22:33:4$i"] = Seen("5A:3C:11:22:33:4$i", null, r, "", -55 - i * 6, now - i * 1500) }
        st.seen["C4:7D:00:11:22:33"] = Seen("C4:7D:00:11:22:33", "TPMS_A1B2C3", null, "AC 00 1F 42 9A 00 10 27", -81, now - 4000)
        shot("03_lukija", false, st) }

    @Test fun roundTrip() { val t = Sensor(2).apply { kpa = 231.5; tempC = 21.37; batt = 77; fastLeak = true }
        val r = TpmsFormat.decode(byteArrayOf(0, 1) + TpmsFormat.payload(t))!!
        assertEquals(231.5, r.kpa, 0.01); assertEquals(21.37, r.tempC, 0.01); assertEquals(77, r.batt); assertEquals(2, r.pos); assertTrue(r.alarm) }
}
