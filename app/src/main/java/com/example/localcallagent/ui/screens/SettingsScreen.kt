package com.example.localcallagent.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.localcallagent.core.model.SipAccountConfig
import com.example.localcallagent.telephony.api.RegistrationState
import com.example.localcallagent.ui.MainViewModel
import com.example.localcallagent.ui.theme.*

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val sipConfig by viewModel.sipConfig.collectAsState()
    val registrationState by viewModel.registrationState.collectAsState()
    val sipStatusMessage by viewModel.sipStatusMessage.collectAsState()
    var purged by remember { mutableStateOf(false) }

    var username by remember(sipConfig) { mutableStateOf(sipConfig.username) }
    var password by remember(sipConfig) { mutableStateOf(sipConfig.password) }
    var domain by remember(sipConfig) { mutableStateOf(sipConfig.domain) }
    var port by remember(sipConfig) { mutableStateOf(sipConfig.port.toString()) }
    var displayName by remember(sipConfig) { mutableStateOf(sipConfig.displayName ?: "") }
    var outboundProxy by remember(sipConfig) { mutableStateOf(sipConfig.outboundProxy ?: "") }

    val statusColor = when (registrationState) {
        RegistrationState.REGISTERED -> SafetyShieldGreen
        RegistrationState.REGISTERING -> WarningAmber
        RegistrationState.FAILED -> EmergencyRed
        RegistrationState.UNREGISTERED -> DarkTextSecondary
    }

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

        // 1. Editable SIP Account Card
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("SIP / VoIP Credentials", fontWeight = FontWeight.SemiBold, color = DarkTextPrimary)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Free SIP: sip2sip.info · iptel.org · sip.linphone.org — see docs/FREE_SIP_SETUP.md",
                    fontSize = 11.sp,
                    color = DarkTextSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(sipStatusMessage, fontSize = 12.sp, color = statusColor, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation()
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = domain,
                    onValueChange = { domain = it },
                    label = { Text("Domain") },
                    placeholder = { Text("sip2sip.info") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = outboundProxy,
                    onValueChange = { outboundProxy = it },
                    label = { Text("Outbound Proxy (optional)") },
                    placeholder = { Text("proxy.sipthor.net") },
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
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedTextField(
                        value = displayName,
                        onValueChange = { displayName = it },
                        label = { Text("Display Name") },
                        modifier = Modifier.weight(2f),
                        singleLine = true
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            viewModel.updateSipConfig(
                                SipAccountConfig(
                                    username = username.trim(),
                                    password = password,
                                    domain = domain.trim(),
                                    port = port.toIntOrNull() ?: 5060,
                                    displayName = displayName.ifBlank { null },
                                    outboundProxy = outboundProxy.ifBlank { null }
                                )
                            )
                            viewModel.registerSip()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary)
                    ) {
                        Text(
                            if (registrationState == RegistrationState.REGISTERING) "Registering…" else "Save & Register",
                            fontSize = 12.sp
                        )
                    }
                    OutlinedButton(onClick = { viewModel.unregisterSip() }) {
                        Text("Unregister", fontSize = 12.sp)
                    }
                }
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
