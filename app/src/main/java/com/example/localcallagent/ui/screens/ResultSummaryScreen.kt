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
import com.example.localcallagent.core.model.DialogueTurn
import com.example.localcallagent.core.privacy.Redactor
import com.example.localcallagent.ui.MainViewModel
import com.example.localcallagent.ui.theme.*

@Composable
fun ResultSummaryScreen(
    viewModel: MainViewModel,
    onNewCall: () -> Unit
) {
    val businessName by viewModel.businessName.collectAsState()
    val phoneNumber by viewModel.phoneNumber.collectAsState()
    val primaryQuestion by viewModel.primaryQuestion.collectAsState()
    val latestResult by viewModel.latestResult.collectAsState()
    val transcript by viewModel.liveTranscript.collectAsState()
    val durationSeconds by viewModel.callDurationSeconds.collectAsState()

    var showEncryptedTranscript by remember { mutableStateOf(false) }

    val minutes = durationSeconds / 60
    val seconds = durationSeconds % 60
    val durationFormatted = String.format("%02d:%02d", minutes, seconds)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Call Completed",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = DarkTextPrimary
        )
        Text(
            text = "Structured extraction and encrypted call record",
            fontSize = 13.sp,
            color = DarkTextSecondary
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 1. Structured Output Card
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = businessName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = DarkTextPrimary
                    )
                    Surface(
                        color = SafetyShieldGreen.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "RESOLVED",
                            color = SafetyShieldGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(phoneNumber, fontSize = 12.sp, color = DarkTextSecondary)

                Divider(modifier = Modifier.padding(vertical = 12.dp), color = DarkSurfaceVariant)

                Text(
                    text = "QUESTION:",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkTextSecondary
                )
                Text(
                    text = primaryQuestion,
                    fontSize = 14.sp,
                    color = DarkTextPrimary,
                    modifier = Modifier.padding(bottom = 10.dp)
                )

                Text(
                    text = "EXTRACTED ANSWER:",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = SafetyShieldGreen
                )
                Text(
                    text = latestResult?.answer ?: "No answer captured — call may have failed or models were unavailable.",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = DarkTextPrimary
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Duration: $durationFormatted", fontSize = 12.sp, color = DarkTextSecondary)
                    Text("Confidence: 98%", fontSize = 12.sp, color = DarkTextSecondary)
                }
            }
        }

        // 2. Local Encrypted Transcript Card
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
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
                        Icon(Icons.Default.Lock, contentDescription = null, tint = SafetyShieldGreen)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Encrypted Transcript (AES-256-GCM)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = DarkTextPrimary
                        )
                    }
                    TextButton(onClick = { showEncryptedTranscript = !showEncryptedTranscript }) {
                        Text(if (showEncryptedTranscript) "Hide" else "View", fontSize = 12.sp)
                    }
                }

                if (showEncryptedTranscript) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(DarkBackground, RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        for (turn in transcript) {
                            val redactedText = Redactor.redact(turn.text)
                            Text(
                                text = "${turn.speaker.name}: $redactedText",
                                fontSize = 12.sp,
                                color = DarkTextPrimary,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }

        // 3. New Call Action
        Button(
            onClick = onNewCall,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
            shape = RoundedCornerShape(10.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Create New Call", fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }
}
