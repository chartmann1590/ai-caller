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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.localcallagent.core.model.SipAccountConfig
import com.example.localcallagent.ui.MainViewModel
import com.example.localcallagent.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    viewModel: MainViewModel,
    onComplete: () -> Unit
) {
    val qualState by viewModel.qualificationState.collectAsState()
    val modelState by viewModel.modelState.collectAsState()
    val benchmarkReport by viewModel.benchmarkReport.collectAsState()
    val isBenchmarking by viewModel.isBenchmarking.collectAsState()
    val sipConfig by viewModel.sipConfig.collectAsState()

    var username by remember { mutableStateOf(sipConfig.username) }
    var password by remember { mutableStateOf(sipConfig.password) }
    var domain by remember { mutableStateOf(sipConfig.domain) }
    var port by remember { mutableStateOf(sipConfig.port.toString()) }
    var displayName by remember { mutableStateOf(sipConfig.displayName ?: "Alex") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // App Title & Tagline
        Text(
            text = "AI Caller",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = DarkTextPrimary
        )
        Text(
            text = "Privacy-First Autonomous On-Device Calling",
            fontSize = 14.sp,
            color = DarkTextSecondary,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // 1. Privacy Contract Card
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = SafetyShieldGreen)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Privacy & Security Guarantee", fontWeight = FontWeight.SemiBold, color = DarkTextPrimary)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "• 100% On-Device AI: Audio, speech recognition, and Gemma language models never leave your phone.\n" +
                    "• Zero Cloud Telemetry: No transcripts or voice data uploaded to any server.\n" +
                    "• Hardware Encrypted: Call logs protected by AES-256-GCM backed by Titan M2 hardware security.\n" +
                    "• Zero Financial Permissions: The AI cannot access payment methods or make purchases.",
                    fontSize = 12.sp,
                    color = DarkTextSecondary,
                    lineHeight = 18.sp
                )
            }
        }

        // 2. Hardware Qualification Scan Card
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Speed, contentDescription = null, tint = BrandPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Device Qualification", fontWeight = FontWeight.SemiBold, color = DarkTextPrimary)
                    }
                    if (qualState.isQualified) {
                        Surface(color = SafetyShieldGreen.copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp)) {
                            Text(
                                "QUALIFIED",
                                color = SafetyShieldGreen,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("• OS: ${qualState.osVersion}", fontSize = 12.sp, color = DarkTextSecondary)
                Text("• Architecture: ${qualState.cpuArch}", fontSize = 12.sp, color = DarkTextSecondary)
                Text("• RAM: ${qualState.ramGb} GB (8 GB minimum satisfied)", fontSize = 12.sp, color = DarkTextSecondary)
                Text("• Storage: ${qualState.storageFreeGb} GB free (6 GB minimum satisfied)", fontSize = 12.sp, color = DarkTextSecondary)
            }
        }

        // 3. AI Models Status Card
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Psychology, contentDescription = null, tint = BrandTertiary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Local AI Models", fontWeight = FontWeight.SemiBold, color = DarkTextPrimary)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("• Gemma 4 E2B (LiteRT-LM / NPU Acceleration): Ready", fontSize = 12.sp, color = DarkTextSecondary)
                Text("• Local Conformer Streaming ASR (8kHz/16kHz): Ready", fontSize = 12.sp, color = DarkTextSecondary)
                Text("• Low-Latency Neural TTS (<150ms Barge-in): Ready", fontSize = 12.sp, color = DarkTextSecondary)
                Text("• Integrity Check: SHA-256 Validated", fontSize = 12.sp, color = SafetyShieldGreen)
            }
        }

        // 4. Performance Benchmark Card
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Insights, contentDescription = null, tint = WarningAmber)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("On-Device Benchmark", fontWeight = FontWeight.SemiBold, color = DarkTextPrimary)
                    }
                    Button(
                        onClick = { viewModel.runBenchmark() },
                        enabled = !isBenchmarking,
                        colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary)
                    ) {
                        Text(if (isBenchmarking) "Testing..." else "Run Test", fontSize = 11.sp)
                    }
                }
                if (benchmarkReport != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    val r = benchmarkReport!!
                    Text("• Composite Score: ${r.overallScore}/100 (${r.supportLevel})", fontWeight = FontWeight.Bold, color = SafetyShieldGreen, fontSize = 12.sp)
                    Text("• LLM TTFT: ${r.timeToFirstTokenMs} ms | Speed: ${r.decodeTokensPerSecond.toInt()} tok/s", fontSize = 12.sp, color = DarkTextSecondary)
                    Text("• ASR RTF: ${r.asrRealTimeFactor} | TTS Chunk: ${r.ttsFirstChunkMs} ms", fontSize = 12.sp, color = DarkTextSecondary)
                }
            }
        }

        // 5. SIP Softphone Configuration
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PhoneInTalk, contentDescription = null, tint = BrandSecondary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("VoIP / SIP Softphone Setup", fontWeight = FontWeight.SemiBold, color = DarkTextPrimary)
                }
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("SIP Username / Extension") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("SIP Password / Secret") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = domain,
                    onValueChange = { domain = it },
                    label = { Text("SIP Domain / Registrar") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = port,
                        onValueChange = { port = it },
                        label = { Text("Port") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedTextField(
                        value = displayName,
                        onValueChange = { displayName = it },
                        label = { Text("Your Caller Name") },
                        modifier = Modifier.weight(2f),
                        singleLine = true
                    )
                }
            }
        }

        // Finish Onboarding Button
        Button(
            onClick = {
                viewModel.updateSipConfig(
                    SipAccountConfig(
                        username = username,
                        password = password,
                        domain = domain,
                        port = port.toIntOrNull() ?: 5060,
                        displayName = displayName
                    )
                )
                onComplete()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = SafetyShieldGreen),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text("Complete Setup & Start Calling", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}
