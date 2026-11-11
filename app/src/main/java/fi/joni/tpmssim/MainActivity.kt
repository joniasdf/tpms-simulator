package fi.joni.tpmssim

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.pm.PackageManager
import android.os.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private val s = UiState()
    private val handler = Handler(Looper.getMainLooper())
    private var adv: BluetoothLeAdvertiser? = null
    private var cb: AdvertiseCallback? = null
    private var scanner: BluetoothLeScanner? = null
    private var cur = 0

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val prefs = getSharedPreferences("tpms", MODE_PRIVATE)
        s.sensors.forEach { it.enabled = prefs.getBoolean("active_${it.index}", it.index < 4) }
        setContent {
            LaunchedEffect(Unit) { snapshotFlow { s.sensors.map { it.enabled } }.collect { l ->
                prefs.edit().apply { l.forEachIndexed { i, b -> putBoolean("active_$i", b) } }.apply() } }
            LaunchedEffect(Unit) { while (true) { delay(1000); s.now = System.currentTimeMillis(); s.sensors.forEach { it.tick(s.now) } } }
            TpmsTheme(isSystemInDarkTheme()) { App(s, { if (it) startAdv() else stopAdv() }, { if (it) startScan() else stopScan() }) }
        }
    }

    private fun need(scan: Boolean) = if (Build.VERSION.SDK_INT >= 31)
        (if (scan) arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT) else arrayOf(Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT))
        else if (scan) arrayOf(Manifest.permission.ACCESS_FINE_LOCATION) else arrayOf()
    private fun ensure(scan: Boolean): Boolean { val p = need(scan).filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (p.isNotEmpty()) { requestPermissions(p.toTypedArray(), 1); s.status = "Myönnä luvat ja yritä uudelleen"; return false }; return true }
    private fun adapter() = (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter?.takeIf { it.isEnabled }

    @SuppressLint("MissingPermission")
    private fun startAdv() {
        if (!ensure(false)) return
        val a = adapter() ?: run { s.status = "Bluetooth ei ole päällä"; return }
        adv = a.bluetoothLeAdvertiser ?: run { s.status = "Laite ei tue BLE-mainostusta"; return }
        s.advertising = true; cycle()
    }

    @SuppressLint("MissingPermission")
    private fun cycle() {
        if (!s.advertising) return
        cb?.let { try { adv?.stopAdvertising(it) } catch (_: Exception) {} }; cb = null
        val active = s.sensors.filter { it.enabled && !it.noSignal }
        if (active.isNotEmpty()) {
            val t = active[cur++ % active.size]
            val data = AdvertiseData.Builder().addManufacturerData(TpmsFormat.COMPANY, TpmsFormat.payload(t)).build()
            val settings = AdvertiseSettings.Builder().setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH).setConnectable(false).build()
            cb = object : AdvertiseCallback() {
                override fun onStartSuccess(e: AdvertiseSettings?) { s.current = "${t.code} (${t.id})" }
                override fun onStartFailure(code: Int) { s.current = null; s.status = "Mainostus epäonnistui, koodi $code" } }
            try { adv?.startAdvertising(settings, data, cb) } catch (e: Exception) { s.status = "Virhe: ${e.message}" }
        } else { s.current = null; s.status = "Ei aktiivisia antureita" }
        handler.postDelayed({ cycle() }, 500)
    }

    @SuppressLint("MissingPermission")
    private fun stopAdv() { s.advertising = false; s.current = null; cb?.let { try { adv?.stopAdvertising(it) } catch (_: Exception) {} }; cb = null; s.status = "Pysäytetty" }

    private val scanCb = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(type: Int, r: ScanResult) {
            val rec = r.scanRecord ?: return
            val m = rec.manufacturerSpecificData
            var full: ByteArray? = null
            for (i in 0 until m.size()) { val id = m.keyAt(i); val v = m.valueAt(i)
                full = byteArrayOf((id and 0xFF).toByte(), (id shr 8 and 0xFF).toByte()) + v
                if (TpmsFormat.decode(full) != null) break }
            val reading = full?.let { TpmsFormat.decode(it) }
            val name = rec.deviceName
            if (reading == null && name?.contains("TPMS", true) != true) return
            s.seen[r.device.address] = Seen(r.device.address, name, reading, full?.hex() ?: "", r.rssi, System.currentTimeMillis())
        }
        override fun onScanFailed(e: Int) { s.scanning = false; s.status = "Skannaus epäonnistui ($e)" }
    }

    @SuppressLint("MissingPermission")
    private fun startScan() {
        if (!ensure(true)) return
        scanner = adapter()?.bluetoothLeScanner ?: return
        scanner?.startScan(null, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), scanCb); s.scanning = true
    }
    @SuppressLint("MissingPermission")
    private fun stopScan() { try { scanner?.stopScan(scanCb) } catch (_: Exception) {}; s.scanning = false }
    override fun onDestroy() { stopAdv(); stopScan(); super.onDestroy() }
}
