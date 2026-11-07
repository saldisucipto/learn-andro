package com.example.nativestarter.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.nativestarter.scan.ScanBeep

private val TerminalBg = Color(0xFF05080A)
private val PanelBg = Color(0xFF0D141A)
private val RowBg = Color(0xFF121B23)
private val AccentGreen = Color(0xFF00E5A0)
private val AccentDim = Color(0xFF0A6E4E)
private val MutedText = Color(0xFF6F8494)
private val DividerColor = Color(0xFF1C262F)

@Composable
fun ScannerScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val history = remember { mutableStateListOf<ScanRecord>() }
    var scanningEnabled by remember { mutableStateOf(true) }
    var lastSeen by remember { mutableStateOf<ScanRecord?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    DisposableEffect(Unit) {
        onDispose { ScanBeep.release() }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(TerminalBg)
            .volumeDownTrigger {
                scanningEnabled = true
                ScanBeep.play()
            }
    ) {
        // Headless capture engine: no preview surface, camera runs silently.
        if (hasPermission) {
            CameraCapture(
                showPreview = false,
                scanningEnabled = scanningEnabled,
                onBarcode = { result ->
                    if (scanningEnabled) {
                        val record = ScanRecord(value = result.value, format = result.format)
                        history.add(0, record)
                        lastSeen = record
                        ScanBeep.play()
                    }
                }
            )
        }

        Column(modifier = Modifier.fillMaxSize()) {
            StatusBar(
                scanningEnabled = scanningEnabled && hasPermission,
                hasPermission = hasPermission
            )

            ImeBanner()

            if (!hasPermission) {
                PermissionRationale(
                    modifier = Modifier.weight(1f),
                    onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) }
                )
            } else {
                RadarPanel(
                    scanningEnabled = scanningEnabled,
                    lastSeen = lastSeen,
                    modifier = Modifier.fillMaxWidth()
                )

                ControlsBar(
                    scanCount = history.size,
                    scanningEnabled = scanningEnabled,
                    onToggle = { scanningEnabled = !scanningEnabled },
                    onClear = { history.clear(); lastSeen = null }
                )

                HistoryList(
                    history = history,
                    clipboard = clipboard,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun StatusBar(scanningEnabled: Boolean, hasPermission: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(PanelBg)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(
                    when {
                        !hasPermission -> Color(0xFFB00020)
                        scanningEnabled -> AccentGreen
                        else -> Color(0xFFFFB300)
                    }
                )
        )
        Spacer(modifier = Modifier.size(10.dp))
        Text(
            text = "SOLVE SCAN",
            color = AccentGreen,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 3.sp,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = when {
                !hasPermission -> "NO CAM"
                scanningEnabled -> "ACTIVE"
                else -> "PAUSED"
            },
            color = when {
                !hasPermission -> Color(0xFFB00020)
                scanningEnabled -> AccentGreen
                else -> Color(0xFFFFB300)
            },
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun ImeBanner() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var enabled by remember { mutableStateOf(ImeHelper.isEnabled(context)) }
    var selected by remember { mutableStateOf(ImeHelper.isSelected(context)) }

    fun refresh() {
        enabled = ImeHelper.isEnabled(context)
        selected = ImeHelper.isSelected(context)
    }

    // Re-read state on every resume so the toggle reflects changes made in the
    // system keyboard settings screen.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val activeColor = if (selected) AccentGreen else Color(0xFFFFB300)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF12202B))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "SOLVE SCAN KEYBOARD",
                color = activeColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = when {
                    !enabled -> "Off. Toggle on to enable it in keyboard settings."
                    selected -> "On. Use the SWITCH button on the keyboard to return to your normal one."
                    else -> "Enabled. Toggle on, then pick Solve Scan from the keyboard list."
                },
                color = MutedText,
                fontSize = 11.sp
            )
        }

        Switch(
            checked = selected,
            onCheckedChange = { wantOn ->
                if (wantOn) {
                    // Not enabled yet -> system settings; otherwise the picker.
                    if (ImeHelper.isEnabled(context)) {
                        ImeHelper.showPicker(context)
                    } else {
                        ImeHelper.openInputMethodSettings(context)
                    }
                } else {
                    ImeHelper.showPicker(context)
                }
                refresh()
            },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black,
                checkedTrackColor = AccentGreen,
                uncheckedThumbColor = MutedText,
                uncheckedTrackColor = Color(0xFF1C262F)
            )
        )
    }
}

@Composable
private fun RadarPanel(
    scanningEnabled: Boolean,
    lastSeen: ScanRecord?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(PanelBg)
            .padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        Text(
            text = if (scanningEnabled) ">> LISTENING FOR BARCODES / QR CODES" else ">> CAPTURE PAUSED",
            color = if (scanningEnabled) AccentGreen else Color(0xFFFFB300),
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Press VOL- to (re)arm the reader",
            color = MutedText,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )

        if (lastSeen != null) {
            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = DividerColor)
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "LAST CAPTURE",
                color = MutedText,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = lastSeen.value,
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${lastSeen.format}  ·  ${lastSeen.timeLabel}",
                color = AccentGreen,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun ControlsBar(
    scanCount: Int,
    scanningEnabled: Boolean,
    onToggle: () -> Unit,
    onClear: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().background(TerminalBg)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onToggle,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (scanningEnabled) Color(0xFF1C262F) else AccentGreen
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = if (scanningEnabled) "PAUSE" else "ARM (VOL-)",
                    color = if (scanningEnabled) Color.White else Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    letterSpacing = 1.sp
                )
            }

            OutlinedButton(
                onClick = onClear,
                enabled = scanCount > 0,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = "CLEAR ($scanCount)",
                    color = if (scanCount > 0) AccentGreen else MutedText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    letterSpacing = 1.sp
                )
            }
        }

        HorizontalDivider(color = DividerColor)

        Text(
            text = "CAPTURE LOG",
            color = MutedText,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp)
        )
    }
}

@Composable
private fun HistoryList(
    history: List<ScanRecord>,
    clipboard: ClipboardManager,
    modifier: Modifier = Modifier
) {
    if (history.isEmpty()) {
        Box(
            modifier = modifier.fillMaxWidth().padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = ">> BUFFER EMPTY\nAwaiting signal...",
                color = AccentDim,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(items = history, key = { it.id }) { record ->
            HistoryRow(
                record = record,
                onClick = { clipboard.setText(AnnotatedString(record.value)) }
            )
        }
    }
}

@Composable
private fun HistoryRow(record: ScanRecord, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(RowBg, RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = record.value,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${record.format}  ·  ${record.timeLabel}",
                color = MutedText,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        OutlinedButton(
            onClick = onClick,
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
            modifier = Modifier.size(width = 74.dp, height = 36.dp)
        ) {
            Text(
                text = "COPY",
                color = AccentGreen,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun PermissionRationale(modifier: Modifier = Modifier, onRequest: () -> Unit) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = ">> CAMERA ACCESS REQUIRED",
            color = Color(0xFFB00020),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "The RF reader needs camera access to capture tags silently.",
            color = MutedText,
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onRequest, shape = RoundedCornerShape(10.dp)) {
            Text(text = "GRANT ACCESS", fontWeight = FontWeight.Bold)
        }
    }
}
