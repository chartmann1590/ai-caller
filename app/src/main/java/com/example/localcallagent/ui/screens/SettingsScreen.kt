package com.example.localcallagent.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.localcallagent.ui.MainViewModel
import com.example.localcallagent.ui.theme.*

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val sipConfig by viewModel.sipConfig.collectAsState()
    var purged by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = DarkTextPrimary)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Settings & Diagnostics",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = DarkTextPrimary
            )
        }

        // 1. SIP Account Info Card
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("SIP / VoIP Credentials", fontWeight = FontWeight.SemiBold, color = DarkTextPrimary)
                Spacer(modifier = Modifier.height(8.dp))
                Text("• Account: ${sipConfig.username}@${sipConfig.domain}", fontSize = 12.sp, color = DarkTextSecondary)
                Text("• Port: ${sipConfig.port} (UDP/TCP/TLS)", fontSize = 12.sp, color = DarkTextSecondary)
                Text("• Caller Name: ${sipConfig.displayName}", fontSize = 12.sp, color = DarkTextSecondary)
                Text("• Codecs: G.711 PCMU, G.711 PCMA", fontSize = 12.sp, color = DarkTextSecondary)
            }
        }

        // 2. Hardware Diagnostics Card
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Hardware & Models", fontWeight = FontWeight.SemiBold, color = DarkTextPrimary)
                Spacer(modifier = Modifier.height(8.dp))
                Text("• Gemma 4 E2B: LiteRT-LM GPU/TPU acceleration active", fontSize = 12.sp, color = DarkTextSecondary)
                Text("• ASR Model: Conformer Streaming Transducer (8kHz)", fontSize = 12.sp, color = DarkTextSecondary)
                Text("• TTS Engine: Local Neural Streaming (<150ms cancellation)", fontSize = 12.sp, color = DarkTextSecondary)
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = { viewModel.runBenchmark() },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary)
                ) {
                    Icon(Icons.Default.Speed, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Rerun Device Benchmark", fontSize = 12.sp)
                }
            }
        }

        // 3. Privacy & Data Purge Card
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = EmergencyRed)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Privacy & Data Purge", fontWeight = FontWeight.SemiBold, color = DarkTextPrimary)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Instantly erase all local in-memory transcripts and encrypted SQLite logs.",
                    fontSize = 12.sp,
                    color = DarkTextSecondary
                )
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = {
                        viewModel.purgePrivacyData()
                        purged = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed)
                ) {
                    Text(if (purged) "Data Purged!" else "Erase All Call Records", fontSize = 12.sp)
                }
            }
        }

        // 4. Regulatory & Legal
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Regulatory & Legal Notices", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = DarkTextPrimary)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "• FCC Compliance: Mandatory AI bot disclosure spoken at call start.\n" +
                    "• Two-Party Consent: AI requests consent to continue before inquiry.\n" +
                    "• 911 Disclaimer: Emergency dialing is not supported.",
                    fontSize = 11.sp,
                    color = DarkTextSecondary,
                    lineHeight = 16.sp
                )
            }
        }
    }
}
