package fi.joni.tpmssim

import androidx.compose.runtime.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

enum class PUnit(val label: String, val perKpa: Double, val dec: Int) { KPA("kPa", 1.0, 0), BAR("bar", 0.01, 2), PSI("psi", 0.145038, 1);
    fun fromKpa(k: Double) = k * perKpa; fun toKpa(v: Double) = v / perKpa
    fun fmt(k: Double) = "%.${dec}f".format(fromKpa(k)) }

enum class Health { OK, WARN, ALARM, OFF }
val SCENARIOS = listOf("Normaali", "Hidas vuoto", "Nopea vuoto", "Ylikuumeneminen", "Heikko paristo", "Anturi katoaa")
val POSITIONS = listOf("FL" to "Vasen etu", "FR" to "Oikea etu", "RL" to "Vasen taka", "RR" to "Oikea taka", "SP" to "Vararengas")

class Sensor(val index: Int) {
    val code get() = POSITIONS[index].first
    val name get() = POSITIONS[index].second
    var id by mutableStateOf("80EACA%06X".format((0x100000..0xFFFFFF).random()).let { "%02X".format(0x80 + index) + it.drop(2) })
    var kpa by mutableDoubleStateOf(240.0); var tempC by mutableDoubleStateOf(25.0); var batt by mutableIntStateOf(90)
    var fastLeak by mutableStateOf(false); var lowBatt by mutableStateOf(false); var noSignal by mutableStateOf(false)
    var enabled by mutableStateOf(index < 4); var scenario by mutableIntStateOf(0)
    fun idBytes(): ByteArray { val h = id.filter { it.isLetterOrDigit() }.padEnd(12, '0').take(12)
        return ByteArray(6) { h.substring(it * 2, it * 2 + 2).toIntOrNull(16)?.toByte() ?: 0 } }
    fun applyScenario(i: Int) { scenario = i; fastLeak = false; lowBatt = false; noSignal = false
        when (i) { 0 -> { kpa = 240.0; tempC = 25.0; batt = 90 }; 3 -> tempC = 95.0; 4 -> { batt = 5; lowBatt = true } } }
    fun tick(nowMs: Long) { when (scenario) {
        1 -> kpa = maxOf(0.0, kpa - 0.5); 2 -> { kpa = maxOf(0.0, kpa - 10.0); fastLeak = true }
        3 -> tempC = minOf(120.0, tempC + 0.2); 5 -> noSignal = (nowMs / 10000) % 2 == 1L } }
    val health: Health get() = when {
        !enabled || noSignal -> Health.OFF
        fastLeak || kpa < 160 || tempC >= 90 -> Health.ALARM
        lowBatt || kpa < 210 || tempC >= 70 || batt < 20 -> Health.WARN
        else -> Health.OK }
}

/** Theengs Decoder "TPMS" (src/devices/TPMS_json.h): manufacturerdata 18 tavua
 *  00 01 | osoite 6 (1. tavu 0x80+paikka) | paine Pa int32LE | lämpö 0.01°C int32LE | akku % | hälytys (bitti 0) */
object TpmsFormat {
    const val COMPANY = 0x0100 // Android kirjoittaa LE -> tavut 00 01
    fun payload(s: Sensor): ByteArray {
        val b = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
        val id = s.idBytes(); id[0] = (0x80 or s.index).toByte(); b.put(id)
        b.putInt((s.kpa * 1000).toInt()); b.putInt((s.tempC * 100).toInt())
        b.put((if (s.lowBatt) minOf(s.batt, 10) else s.batt).toByte())
        b.put(((if (s.fastLeak || s.kpa < 50) 1 else 0) or (if (s.lowBatt) 2 else 0)).toByte()); return b.array()
    }
    /** data = koko manufacturer data mukaan lukien 2 tavun company id. */
    fun decode(data: ByteArray): Reading? {
        if (data.size != 18 || data[0].toInt() != 0 || data[1].toInt() != 1) return null
        val b = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        val id = data.copyOfRange(2, 8).joinToString(":") { "%02X".format(it) }
        val pos = (data[2].toInt() and 0x0F)
        return Reading(id, pos, b.getInt(8) / 1000.0, b.getInt(12) / 100.0, data[16].toInt() and 0xFF, data[17].toInt() and 1 == 1)
    }
}
data class Reading(val id: String, val pos: Int, val kpa: Double, val tempC: Double, val batt: Int, val alarm: Boolean)
data class Seen(val mac: String, val name: String?, val reading: Reading?, val rawHex: String, val rssi: Int, val lastSeen: Long)
fun ByteArray.hex() = joinToString(" ") { "%02X".format(it) }
