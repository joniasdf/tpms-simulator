package fi.joni.tpmssim

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun TpmsTheme(dark: Boolean, content: @Composable () -> Unit) = MaterialTheme(
    colorScheme = if (dark) darkColorScheme(primary = Color(0xFF7FCFFF), secondary = Color(0xFF9ECAFF))
                  else lightColorScheme(primary = Color(0xFF00658E), secondary = Color(0xFF3A6188)), content = content)

@Composable fun healthColor(h: Health) = when (h) {
    Health.OK -> Color(0xFF2E9E5B); Health.WARN -> Color(0xFFE0A000); Health.ALARM -> Color(0xFFD93A3A); Health.OFF -> Color(0xFF8A8F98) }
fun healthText(t: Sensor) = if (!t.enabled) "Pois" else healthText(t.health)
fun healthText(h: Health) = when (h) { Health.OK -> "OK"; Health.WARN -> "Varoitus"; Health.ALARM -> "HÄLYTYS"; Health.OFF -> "Ei signaalia" }

class UiState {
    val sensors = List(5) { Sensor(it) }
    var unit by mutableStateOf(PUnit.BAR); var tab by mutableIntStateOf(0); var selected by mutableIntStateOf(0)
    var advertising by mutableStateOf(false); var current by mutableStateOf<String?>(null); var status by mutableStateOf("Valmis")
    val activeCount get() = sensors.count { it.enabled }
    fun setAll(on: Boolean) = sensors.forEach { it.enabled = on }
    fun solo(i: Int) { sensors.forEachIndexed { j, t -> t.enabled = j == i }; selected = i }
    var scanning by mutableStateOf(false); val seen = mutableStateMapOf<String, Seen>(); var now by mutableLongStateOf(System.currentTimeMillis())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(s: UiState, onAdvertise: (Boolean) -> Unit = {}, onScan: (Boolean) -> Unit = {}) {
    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text(if (s.tab == 0) "TPMS-simulaattori" else "TPMS-lukija", fontWeight = FontWeight.SemiBold) },
            actions = { UnitSelector(s) }) },
        bottomBar = { NavigationBar {
            NavigationBarItem(s.tab == 0, { s.tab = 0 }, { Icon(Icons.Default.Sensors, null) }, label = { Text("Simulaattori") })
            NavigationBarItem(s.tab == 1, { s.tab = 1 }, { Icon(Icons.Default.Radar, null) }, label = { Text("Lukija") }) } }
    ) { pad -> Box(Modifier.padding(pad).fillMaxSize()) { if (s.tab == 0) SimulatorScreen(s, onAdvertise) else ReaderScreen(s, onScan) } }
}

@Composable fun UnitSelector(s: UiState) {
    var open by remember { mutableStateOf(false) }
    TextButton({ open = true }) { Text(s.unit.label); Icon(Icons.Default.ArrowDropDown, null) }
    DropdownMenu(open, { open = false }) { PUnit.entries.forEach { u -> DropdownMenuItem({ Text(u.label) }, { s.unit = u; open = false }) } }
}

@Composable
fun SimulatorScreen(s: UiState, onAdvertise: (Boolean) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ElevatedCard(Modifier.fillMaxWidth()) { Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (s.advertising) "Mainostetaan" else "Pysäytetty", style = MaterialTheme.typography.titleMedium)
                Text("Aktiivisia antureita ${s.activeCount}/${s.sensors.size}" + (s.current?.takeIf { s.activeCount > 0 }?.let { " · nyt: $it" } ?: ""),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (s.advertising && s.sensors.none { it.enabled && !it.noSignal })
                    Text("Yksikään anturi ei lähetä – kytke rengas päälle", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                else if (!s.advertising && s.status != "Valmis" && s.status != "Pysäytetty") Text(s.status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            Button({ onAdvertise(!s.advertising) }, colors = if (s.advertising) ButtonDefaults.buttonColors(MaterialTheme.colorScheme.error) else ButtonDefaults.buttonColors()) {
                Icon(if (s.advertising) Icons.Default.Stop else Icons.Default.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text(if (s.advertising) "Pysäytä" else "Käynnistä") }
        } }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            FilterChip(s.activeCount == s.sensors.size, { s.setAll(true) }, { Text("Kaikki") })
            FilterChip(s.activeCount == 0, { s.setAll(false) }, { Text("Ei mitään") })
            Text("Pitkä painallus = vain tämä", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        CarView(s)
        SensorEditor(s, s.sensors[s.selected])
    }
}

@Composable
fun CarView(s: UiState) {
    @Composable fun tyre(i: Int, m: Modifier) = TyreCard(s.sensors[i], s.unit, s.selected == i, m, { s.solo(i) }) { s.selected = i }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Row(Modifier.fillMaxWidth().height(360.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) { tyre(0, Modifier); tyre(2, Modifier) }
        Box(Modifier.width(92.dp).fillMaxHeight().padding(vertical = 4.dp)) {
            Box(Modifier.fillMaxSize().clip(RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp, bottomStart = 24.dp, bottomEnd = 24.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant).border(2.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp, bottomStart = 24.dp, bottomEnd = 24.dp)))
            Box(Modifier.align(Alignment.TopCenter).padding(top = 60.dp).size(70.dp, 50.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.outlineVariant))
            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp).size(70.dp, 36.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.outlineVariant))
            Text("ETU", Modifier.align(Alignment.TopCenter).padding(top = 14.dp), style = MaterialTheme.typography.labelSmall)
        }
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) { tyre(1, Modifier); tyre(3, Modifier) }
    }
    tyre(4, Modifier) }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TyreCard(t: Sensor, unit: PUnit, sel: Boolean, m: Modifier, onLong: () -> Unit, onClick: () -> Unit) {
    val c = if (t.enabled) healthColor(t.health) else MaterialTheme.colorScheme.outline
    OutlinedCard(m.fillMaxWidth().alpha(if (t.enabled) 1f else 0.55f).clip(CardDefaults.outlinedShape).combinedClickable(onLongClick = onLong, onClick = onClick),
        border = BorderStroke(if (sel) 3.dp else 1.5.dp, c),
        colors = CardDefaults.outlinedCardColors(containerColor = if (t.enabled) c.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (t.index == 4) "Vara" else t.code, fontWeight = FontWeight.Bold); Spacer(Modifier.width(6.dp))
                Text(healthText(t), color = c, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1)
                Switch(t.enabled, { t.enabled = it }, Modifier.scale(0.75f)) }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(if (!t.enabled || t.noSignal) "––" else unit.fmt(t.kpa), fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
                Text(" ${unit.label}", Modifier.padding(bottom = 5.dp), style = MaterialTheme.typography.bodySmall) }
            Text("%.1f °C · %d %%".format(t.tempC, t.batt), style = MaterialTheme.typography.bodySmall)
            if (t.scenario != 0) Text(SCENARIOS[t.scenario], style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun NumField(label: String, value: Double, dec: Int, suffix: String, m: Modifier, onSet: (Double) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    val shown = "%.${dec}f".format(value)
    LaunchedEffect(shown, focused) { if (!focused) text = shown }
    OutlinedTextField(text, { text = it; it.replace(',', '.').toDoubleOrNull()?.let(onSet) }, m.onFocusChanged { focused = it.isFocused },
        label = { Text(label) }, suffix = { Text(suffix) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SensorEditor(s: UiState, t: Sensor) {
    ElevatedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${t.name} (${t.code})", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text("Käytössä", style = MaterialTheme.typography.bodySmall); Spacer(Modifier.width(8.dp)); Switch(t.enabled, { t.enabled = it }) }
        OutlinedTextField(t.id, { t.id = it.uppercase().filter { c -> c.isLetterOrDigit() }.take(12) }, Modifier.fillMaxWidth(),
            label = { Text("Anturin ID (12 heksaa)") }, singleLine = true, textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumField("Paine", s.unit.fromKpa(t.kpa), s.unit.dec, s.unit.label, Modifier.weight(1.2f)) { t.kpa = s.unit.toKpa(it) }
            NumField("Lämpö", t.tempC, 1, "°C", Modifier.weight(1f)) { t.tempC = it }
            NumField("Akku", t.batt.toDouble(), 0, "%", Modifier.weight(0.9f)) { t.batt = it.toInt().coerceIn(0, 100) } }
        Text("Hälytykset", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(t.fastLeak, { t.fastLeak = !t.fastLeak }, { Text("Nopea vuoto") })
            FilterChip(t.lowBatt, { t.lowBatt = !t.lowBatt }, { Text("Heikko paristo") })
            FilterChip(t.noSignal, { t.noSignal = !t.noSignal }, { Text("Ei signaalia") }) }
        Text("Skenaario", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SCENARIOS.forEachIndexed { i, n -> FilterChip(t.scenario == i, { t.applyScenario(i) }, { Text(n) }) } }
    } }
}

@Composable
fun ReaderScreen(s: UiState, onScan: (Boolean) -> Unit) {
    val list = s.seen.values.sortedWith(compareBy({ it.reading == null }, { it.reading?.pos ?: 99 }, { it.mac }))
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ElevatedCard(Modifier.fillMaxWidth()) { Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (s.scanning) "Skannataan…" else "Skannaus pysäytetty", style = MaterialTheme.typography.titleMedium)
                Text("Löydetty ${list.count { it.reading != null }} anturia, ${list.count { it.reading == null }} tuntematonta", style = MaterialTheme.typography.bodySmall) }
            Button({ onScan(!s.scanning) }) { Icon(if (s.scanning) Icons.Default.Stop else Icons.Default.Search, null); Spacer(Modifier.width(6.dp)); Text(if (s.scanning) "Pysäytä" else "Skannaa") }
        } }
        if (list.isEmpty()) Text("Ei havaittuja TPMS-antureita. Käynnistä skannaus.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) { items(list, key = { it.mac }) { SeenCard(it, s.unit, s.now) } }
    }
}

@Composable
fun SeenCard(d: Seen, unit: PUnit, now: Long) {
    val r = d.reading
    val age = ((now - d.lastSeen) / 1000).coerceAtLeast(0)
    val h = when { r == null -> Health.OFF; r.alarm || r.kpa < 160 || r.tempC >= 90 -> Health.ALARM; r.kpa < 210 || r.batt < 20 || r.tempC >= 70 -> Health.WARN; else -> Health.OK }
    OutlinedCard(Modifier.fillMaxWidth(), border = BorderStroke(1.5.dp, healthColor(h))) { Column(Modifier.padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(r?.let { POSITIONS.getOrNull(it.pos)?.let { p -> "${p.first} · ${p.second}" } ?: "Paikka ${it.pos + 1}" } ?: (d.name ?: "Tuntematon"),
                fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("${d.rssi} dBm · ${age} s sitten", style = MaterialTheme.typography.labelSmall) }
        if (r != null) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(unit.fmt(r.kpa), fontSize = 28.sp, fontWeight = FontWeight.SemiBold); Text(" ${unit.label}", Modifier.padding(bottom = 4.dp))
                Spacer(Modifier.weight(1f)); Text(if (r.alarm) "HÄLYTYS" else healthText(h), color = healthColor(h), fontWeight = FontWeight.Bold) }
            Text("%.2f °C · akku %d %% · ID %s".format(r.tempC, r.batt, r.id), style = MaterialTheme.typography.bodySmall)
        } else Text("Raakadata: ${d.rawHex.ifEmpty { "–" }}", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
        Text("BLE ${d.mac}${d.name?.let { " · $it" } ?: ""}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } }
}
