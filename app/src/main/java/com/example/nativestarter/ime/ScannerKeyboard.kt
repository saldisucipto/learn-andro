package com.example.nativestarter.ime

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val BarBg = Color(0xFF0D141A)
private val KeyBg = Color(0xFF1C262F)
private val AccentGreen = Color(0xFF00E5A0)
private val MutedText = Color(0xFF6F8494)
private val WarnAmber = Color(0xFFFFB300)

@Composable
fun ScannerKeyboard(
    scanning: Boolean,
    status: String,
    lastValue: String?,
    autoEnter: Boolean,
    onScan: () -> Unit,
    onToggleAutoEnter: () -> Unit,
    onSpace: () -> Unit,
    onEnter: () -> Unit,
    onBackspace: () -> Unit,
    onSwitchKeyboard: () -> Unit,
    onHide: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(BarBg)
            .padding(10.dp)
    ) {
        StatusRow(scanning = scanning, status = status, lastValue = lastValue)

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onScan,
                modifier = Modifier.weight(2.2f).height(52.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (scanning) WarnAmber else AccentGreen
                )
            ) {
                Text(
                    text = if (scanning) "■ STOP" else "▣ SCAN",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    letterSpacing = 1.sp
                )
            }

            Button(
                onClick = onToggleAutoEnter,
                modifier = Modifier.weight(1.5f).height(52.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (autoEnter) AccentGreen else KeyBg
                )
            ) {
                Text(
                    text = if (autoEnter) "⏎ AUTO" else "⏎ OFF",
                    color = if (autoEnter) Color.Black else MutedText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    letterSpacing = 1.sp
                )
            }

            Button(
                onClick = onBackspace,
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = KeyBg)
            ) {
                Text(text = "⌫", color = Color.White, fontSize = 18.sp)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onSpace,
                modifier = Modifier.weight(1f).height(44.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = KeyBg)
            ) {
                Text(text = "SPACE", color = Color.White, fontSize = 12.sp)
            }

            Button(
                onClick = onEnter,
                modifier = Modifier.weight(1f).height(44.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = KeyBg)
            ) {
                Text(text = "ENTER", color = AccentGreen, fontSize = 12.sp)
            }

            Button(
                onClick = onHide,
                modifier = Modifier.weight(1f).height(44.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF12181F))
            ) {
                Text(
                    text = "HIDE",
                    color = MutedText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    letterSpacing = 1.sp
                )
            }

            Button(
                onClick = onSwitchKeyboard,
                modifier = Modifier.weight(1.4f).height(44.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B2A1F))
            ) {
                Text(
                    text = "⌨ SWITCH",
                    color = AccentGreen,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

@Composable
private fun StatusRow(scanning: Boolean, status: String, lastValue: String?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF12181F), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(50))
                .background(if (scanning) WarnAmber else AccentGreen)
        )
        Spacer(modifier = Modifier.size(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = status,
                color = if (scanning) WarnAmber else AccentGreen,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                fontFamily = FontFamily.Monospace
            )
            if (lastValue != null) {
                Text(
                    text = lastValue,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Text(
                    text = "Tap SCAN or press VOL- to capture",
                    color = MutedText,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
